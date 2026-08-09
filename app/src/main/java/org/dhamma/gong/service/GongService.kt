package org.dhamma.gong.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.dhamma.gong.R
import org.dhamma.gong.data.GongDatabase
import org.dhamma.gong.data.GongRepository
import org.dhamma.gong.data.SeedLoader
import org.dhamma.gong.domain.ApplianceZone
import org.dhamma.gong.domain.Occurrence
import org.dhamma.gong.domain.PlayCommand
import org.dhamma.gong.domain.PlayKind
import org.dhamma.gong.domain.SystemGongClock
import org.dhamma.gong.player.AudioRouter
import org.dhamma.gong.player.ExoAudioSink
import org.dhamma.gong.player.MediaResolver
import org.dhamma.gong.player.PlayerEngine
import org.dhamma.gong.relay.RelayController
import org.dhamma.gong.schedule.AlarmScheduler
import org.dhamma.gong.schedule.SchedulerEngine
import org.dhamma.gong.ui.MainActivity

/**
 * The appliance process. Maps 1:1 to the Pi's `gongd`: it owns the scheduler
 * and the player, and the UI is a client of it, never a peer — closing the
 * activity must change nothing (design doc §03).
 *
 * START_STICKY plus a boot receiver is the recovery path; the scheduler's own
 * heartbeat (M3) is the belt to the alarm's braces.
 */
class GongService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private lateinit var repo: GongRepository
    private lateinit var playerEngine: PlayerEngine
    private lateinit var schedulerEngine: SchedulerEngine
    private lateinit var relayController: RelayController

    /**
     * The appliance's zone — the `timezone` setting, never the device default
     * (a centre tablet must gong in the centre's zone even if the phone thinks
     * it has travelled). Volatile: written from the service scope, read by the
     * scheduler through the clock's zone provider.
     */
    @Volatile
    private var applianceZone = ApplianceZone.DEFAULT

    override fun onCreate() {
        super.onCreate()
        val db = GongDatabase.get(this)
        repo = GongRepository(db)
        // The relay switches the amplifier as a convenience. Both hooks below
        // are fire-and-forget: nothing in the play path ever awaits a network
        // call, so an unreachable Shelly cannot delay or fail a gong.
        relayController = RelayController(repo = repo, scope = scope)
        playerEngine = PlayerEngine(
            repo = repo,
            resolver = MediaResolver(this, db),
            router = AudioRouter(this),
            sink = ExoAudioSink(this),
            scope = scope,
            onPlayEnded = {
                relayController.onPlayEnded(java.time.ZonedDateTime.now(applianceZone))
            },
        )
        schedulerEngine = SchedulerEngine(
            repo = repo,
            clock = SystemGongClock { applianceZone },
            alarms = AlarmScheduler(this),
            scope = scope,
            dispatch = { playerEngine.submit(it) },
            warmUp = { playerEngine.warmUp() },
            relayTick = { now, deadline, trusted ->
                relayController.onTick(
                    now = now,
                    nextDeadline = deadline,
                    playing = playerEngine.status.value.playing,
                    clockTrusted = trusted,
                )
            },
        )
        instance.value = this

        createChannel()
        startForegroundCompat(buildNotification("Starting…", ""))

        scope.launch {
            // Seed must land before the first materialize, or the scheduler
            // would resolve an empty schedule and arm nothing.
            runCatching { SeedLoader.applyFromAssets(this@GongService, db) }
                .onFailure { Log.e(TAG, "seed failed", it) }
            refreshZone()
            schedulerEngine.start()
        }
        scope.launch { observePlayer() }
        scope.launch { observeScheduler() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TEST_GONG -> scope.launch { testGong() }
            ACTION_TEST_DOHA -> scope.launch { testDoha(intent.getIntExtra(EXTRA_SLOT, 1)) }
            ACTION_STOP -> scope.launch { playerEngine.stop() }
            ACTION_ALARM -> schedulerEngine.poke("alarm")
            ACTION_TIME_CHANGED -> scope.launch {
                // The wall clock moved: every materialized instant is stale.
                refreshZone()
                schedulerEngine.poke(intent.getStringExtra(EXTRA_REASON) ?: "time changed")
            }
            ACTION_POKE -> scope.launch {
                // Settings edits arrive as pokes; the timezone setting is the
                // one the clock cannot see through the snapshot, so re-read it.
                refreshZone()
                schedulerEngine.poke(intent.getStringExtra(EXTRA_REASON) ?: "poke")
            }
        }
        // Restart with a null intent after an OEM kill; onCreate re-arms us.
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        instance.value = null
        scope.cancel()
        // release() does no Room writes and the sink frees on Main.immediate,
        // so this cannot deadlock the main thread (FABLE-REVIEW B13).
        runBlocking { runCatching { playerEngine.release() } }
        super.onDestroy()
    }

    // ------------------------------------------------------------ commands

    val player: PlayerEngine get() = playerEngine
    val scheduler: SchedulerEngine get() = schedulerEngine
    val repository: GongRepository get() = repo

    /** The Amp power screen observes [RelayController.state] and drives it. */
    val relay: RelayController get() = relayController

    /** Any edit that changes what fires next must call this. */
    fun pokeScheduler(reason: String) {
        scope.launch {
            refreshZone()
            schedulerEngine.poke(reason)
        }
    }

    /** Re-resolve the appliance zone from the `timezone` setting. */
    private suspend fun refreshZone() {
        val zone = ApplianceZone.resolve(repo.setting("timezone"))
        if (zone != applianceZone) {
            Log.i(TAG, "appliance timezone → $zone")
            applianceZone = zone
        }
    }

    /** Staff confirmed the wall clock; automatic plays resume. */
    suspend fun confirmClock() {
        repo.confirmClock(java.time.ZonedDateTime.now(applianceZone))
        schedulerEngine.poke("clock confirmed")
    }

    /** A staff-triggered gong. Allowed even when the clock is untrusted. */
    suspend fun testGong() {
        playerEngine.submit(
            PlayCommand(
                kind = PlayKind.TEST_GONG,
                trackStem = repo.setting("gong_track"),
                repeats = TEST_STRIKES,
                gapSeconds = repo.settingInt("gong_gap_seconds"),
                volume = repo.settingInt("gong_volume"),
                label = "Test gong",
            ),
        )
    }

    suspend fun testDoha(slot: Int) {
        playerEngine.submit(
            PlayCommand(
                kind = PlayKind.TEST_DOHA,
                dohaSlot = slot,
                repeats = 1,
                volume = repo.settingInt("doha_volume"),
                label = "Test doha slot $slot",
            ),
        )
    }

    suspend fun stopPlayback() = playerEngine.stop()

    // ------------------------------------------------------------ notification

    private suspend fun observePlayer() {
        playerEngine.status.collect { status ->
            val title = if (status.playing) {
                "Ringing — ${status.label}"
            } else {
                "Gong scheduler running"
            }
            val body = if (status.playing && status.ofStrikes > 0) {
                "Strike ${status.strike} of ${status.ofStrikes} · ${status.route}"
            } else {
                notificationBody.value
            }
            notify(buildNotification(title, body))
        }
    }

    /** The scheduler writes the "next event" line here. */
    private val notificationBody = MutableStateFlow("")

    /**
     * The persistent notification is the appliance's health indicator: from
     * across the office it must answer "what day, what's next, is it healthy"
     * (design doc §09).
     */
    private suspend fun observeScheduler() {
        schedulerEngine.state.collect { s ->
            val course = s.course?.let { "${it.typeName} · Day ${it.day}" } ?: "No course"
            val next = s.next?.let {
                val time = "%02d:%02d".format(it.fireAt.hour, it.fireAt.minute)
                when (it.kind) {
                    Occurrence.Kind.GONG -> "next $time ×${it.repeats}"
                    Occurrence.Kind.DOHA -> "next $time doha"
                }
            } ?: "nothing scheduled"
            val warning = when {
                !s.clockTrusted -> " · CLOCK UNTRUSTED"
                !s.exactAlarmsAllowed -> " · exact alarms denied"
                else -> ""
            }
            notificationBody.value = "$course · $next$warning"
            if (playerEngine.status.value.playing.not()) {
                notify(buildNotification("Gong scheduler running", notificationBody.value))
            }
        }
    }

    private fun notify(n: Notification) {
        getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, n)
    }

    private fun buildNotification(title: String, body: String): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, GongService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(R.drawable.ic_stat_gong)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .addAction(Notification.Action.Builder(null, "Stop", stop).build())
            .build()
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Gong appliance",
            // LOW: the notification is a health indicator, not an alert. The
            // gong itself is the alert.
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Shows the next scheduled gong and playback state."
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    private fun startForegroundCompat(n: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, n)
        }
    }

    companion object {
        private const val TAG = "GongService"
        const val CHANNEL_ID = "gong_appliance"
        const val NOTIFICATION_ID = 1

        const val ACTION_TEST_GONG = "org.dhamma.gong.TEST_GONG"
        const val ACTION_TEST_DOHA = "org.dhamma.gong.TEST_DOHA"
        const val ACTION_STOP = "org.dhamma.gong.STOP"
        const val ACTION_TIME_CHANGED = "org.dhamma.gong.TIME_CHANGED"
        const val ACTION_POKE = "org.dhamma.gong.POKE"
        const val ACTION_ALARM = "org.dhamma.gong.ALARM"
        const val EXTRA_SLOT = "slot"
        const val EXTRA_REASON = "reason"

        /** Design handoff §"Interactions": the test button fires four strikes. */
        const val TEST_STRIKES = 4

        /**
         * The running service, for the UI to observe. Null while stopped — the
         * UI must degrade, not crash.
         */
        val instance = MutableStateFlow<GongService?>(null)

        val running: StateFlow<GongService?> = instance.asStateFlow()

        fun start(context: Context) {
            context.startForegroundService(Intent(context, GongService::class.java))
        }

        fun send(context: Context, action: String, extras: Intent.() -> Unit = {}) {
            context.startForegroundService(
                Intent(context, GongService::class.java).setAction(action).apply(extras),
            )
        }
    }
}
