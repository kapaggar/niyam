package org.dhamma.gong.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Power-loss recovery. A centre tablet lives on a charger behind a curtain;
 * the only thing that reliably brings the service back after an outage is
 * BOOT_COMPLETED (design doc §10).
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.i(TAG, "boot signal ${intent.action} — starting service")
        runCatching { GongService.start(context) }
            .onFailure { Log.e(TAG, "could not start service on boot", it) }
    }

    private companion object {
        const val TAG = "BootReceiver"
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
