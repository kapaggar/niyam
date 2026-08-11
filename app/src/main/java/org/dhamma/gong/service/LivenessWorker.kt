package org.dhamma.gong.service

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import android.util.Log
import org.dhamma.gong.schedule.AlarmScheduler
import java.util.concurrent.TimeUnit

/**
 * The third keep-alive belt.
 *
 * The first two — the exact next-fire alarm and the 30 s in-service heartbeat —
 * both assume the service is still alive. An OEM battery killer breaks that
 * assumption silently: the process goes away, and nobody finds out until a hall
 * of a hundred people is not woken at 04:00. WorkManager survives that kill,
 * because the OS owns the schedule rather than the app.
 *
 * What this worker cannot do is start the service itself. A `mediaPlayback`
 * foreground service may not be started from the background on modern Android,
 * and a Worker is background by definition. So it does the one thing it is
 * allowed to do: arms a near-immediate exact alarm.
 * [KickstartReceiver] then runs inside the short allowlist window that alarm
 * delivery grants, and *that* context may start the service.
 *
 * Cheap by design — it wakes, reads one flag, and usually does nothing.
 */
class LivenessWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {

    override fun doWork(): Result {
        val alive = GongService.running.value != null
        if (!alive) {
            Log.w(TAG, "service is not running — arming kickstart")
            // Not `runCatching`-free: a refused alarm must not fail the worker,
            // or WorkManager backs the whole chain off exactly when the
            // appliance most needs re-arming.
            runCatching { AlarmScheduler(applicationContext).armKickstart(1_000) }
                .onFailure { Log.e(TAG, "kickstart could not be armed", it) }
        }
        return Result.success()
    }

    companion object {
        private const val TAG = "LivenessWorker"
        private const val UNIQUE = "gong_liveness"

        /**
         * Idempotent. [ExistingPeriodicWorkPolicy.KEEP] matters: this is called
         * from service start *and* from boot, and REPLACE would reset the
         * 15-minute clock every time, so a tablet that reboots often would
         * never actually reach a run.
         */
        fun ensureScheduled(context: Context) {
            runCatching {
                val request = PeriodicWorkRequestBuilder<LivenessWorker>(
                    PERIOD_MINUTES,
                    TimeUnit.MINUTES,
                ).build()
                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    UNIQUE,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request,
                )
                Log.i(TAG, "liveness check scheduled every $PERIOD_MINUTES min")
            }.onFailure {
                // WorkManager is a convenience here, not the critical path.
                // Losing it costs the third belt, not the gong.
                Log.e(TAG, "could not schedule liveness check", it)
            }
        }

        /** 15 min is WorkManager's floor for periodic work. */
        const val PERIOD_MINUTES = 15L
    }
}
