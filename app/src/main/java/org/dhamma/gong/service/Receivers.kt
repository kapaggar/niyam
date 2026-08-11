package org.dhamma.gong.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import org.dhamma.gong.schedule.AlarmScheduler

/**
 * Power-loss recovery. A centre tablet lives on a charger behind a curtain;
 * the only thing that reliably brings the service back after an outage is
 * BOOT_COMPLETED (design doc §10).
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.i(TAG, "boot signal ${intent.action} — starting service")
        // Two paths on purpose. The direct start is what normally works; on
        // API 34+ the platform may refuse a mediaPlayback foreground service
        // launched straight from BOOT_COMPLETED, and a refusal here would
        // leave the appliance dead until someone opened the app. The kickstart
        // alarm lands a few seconds later from a context that is allowed to
        // start it, so a refused boot start costs seconds, not a morning.
        runCatching { GongService.start(context) }
            .onFailure { Log.e(TAG, "direct boot start refused — relying on kickstart", it) }
        runCatching { AlarmScheduler(context).armKickstart() }
            .onFailure { Log.e(TAG, "could not arm boot kickstart", it) }
        runCatching { LivenessWorker.ensureScheduled(context) }
            .onFailure { Log.e(TAG, "could not schedule liveness check", it) }
    }

    private companion object {
        const val TAG = "BootReceiver"
    }
}

/**
 * The far end of [AlarmScheduler.armKickstart].
 *
 * It exists only because of a platform rule: background code may not start a
 * `mediaPlayback` foreground service, but the delivery of an exact alarm opens
 * a brief allowlist window, and a broadcast receiver runs inside it. So the
 * liveness worker arms an alarm and this spends the window on one call.
 *
 * Deliberately does nothing else. Anything slow here risks an ANR in the very
 * window we need for the start.
 */
class KickstartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.i(TAG, "kickstart — starting service")
        runCatching { GongService.start(context) }
            .onFailure { Log.e(TAG, "kickstart could not start the service", it) }
    }

    companion object {
        const val ACTION_KICKSTART = "org.dhamma.gong.KICKSTART"
        private const val TAG = "KickstartReceiver"
    }
}

/**
 * A clock or timezone change invalidates every materialized occurrence, so the
 * service must re-resolve rather than trust an alarm armed against the old
 * wall clock (design doc §05, "Re-materialize on").
 */
class TimeChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.i(TAG, "time signal ${intent.action} — poking scheduler")
        runCatching {
            GongService.send(context, GongService.ACTION_TIME_CHANGED) {
                putExtra(GongService.EXTRA_REASON, intent.action.orEmpty())
            }
        }.onFailure { Log.e(TAG, "could not poke scheduler", it) }
    }

    private companion object {
        const val TAG = "TimeChangeReceiver"
    }
}
