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
import org.dhamma.gong.domain.PlayCommand
import org.dhamma.gong.domain.PlayKind
import org.dhamma.gong.player.AudioRouter
import org.dhamma.gong.player.ExoAudioSink
import org.dhamma.gong.player.MediaResolver
import org.dhamma.gong.player.PlayerEngine
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

    override fun onCreate() {
        super.onCreate()
        val db = GongDatabase.get(this)
        repo = GongRepository(db)
        playerEngine = PlayerEngine(
            repo = repo,
            resolver = MediaResolver(this, db),
            router = AudioRouter(this),
            sink = ExoAudioSink(this),
            scope = scope,
        )
        instance.value = this

        createChannel()
        startForegroundCompat(buildNotification("Starting…", ""))

        scope.launch {
            runCatching { SeedLoader.applyFromAssets(this@GongService, db) }
                .onFailure { Log.e(TAG, "seed failed", it) }
            observePlayer()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TEST_GONG -> scope.launch { testGong() }
            ACTION_TEST_DOHA -> scope.launch { testDoha(intent.getIntExtra(EXTRA_SLOT, 1)) }
            ACTION_STOP -> scope.launch { playerEngine.stop() }
            // M3 wires these to a scheduler re-materialize; for now they just
            // keep the service alive and record why it was woken.
            ACTION_TIME_CHANGED, ACTION_POKE -> {
                val reason = intent.getStringExtra(EXTRA_REASON).orEmpty()
                Log.i(TAG, "poke (${intent.action}) reason=$reason")
            }
        }
        // Restart with a null intent after an OEM kill; onCreate re-arms us.
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        instance.value = null
        runBlocking { runCatching { playerEngine.release() } }
        scope.cancel()
        super.onDestroy()
    }

    // ------------------------------------------------------------ commands

    val player: PlayerEngine get() = playerEngine
    val repository: GongRepository get() = repo

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

    /** The scheduler (M3) writes the "next event" line here. */
    private val notificationBody = MutableStateFlow("")

    fun setNotificationBody(text: String) {
        notificationBody.value = text
        notify(buildNotification("Gong scheduler running", text))
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
            .setSmallIcon(R.drawable.ic_launcher_foreground)
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
