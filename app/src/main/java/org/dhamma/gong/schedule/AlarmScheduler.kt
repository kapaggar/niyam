package org.dhamma.gong.schedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import org.dhamma.gong.service.GongService
import java.time.ZonedDateTime

/**
 * Wake source for the next occurrence.
 *
 * `setAlarmClock` is the choice from design doc §03: exempt from Doze and,
 * unlike `setExactAndAllowWhileIdle`, not throttled to one firing per 9
 * minutes. The cost is a system alarm icon, which is acceptable on an
 * appliance (open question 2 in the handoff).
 *
 * The alarm is an **optimisation**, never the only path — [SchedulerEngine]
 * also runs a 30 s heartbeat, which is how OEM battery killers get survived.
 */
open class AlarmScheduler(private val context: Context) {

    private val manager: AlarmManager? = context.getSystemService(AlarmManager::class.java)

    /** True when the OS will honour an exact alarm right now. */
    open fun canScheduleExact(): Boolean = when {
        manager == null -> false
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> manager.canScheduleExactAlarms()
        else -> true
    }

    /**
     * Arm a wake-up for [at]. Idempotent — the pending intent is a singleton,
     * so re-arming replaces rather than stacks.
     */
    open fun arm(at: ZonedDateTime) {
        val am = manager ?: return
        val triggerAtMs = at.toInstant().toEpochMilli()
        val operation = wakeIntent()
        try {
            if (canScheduleExact()) {
                am.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAtMs, showIntent()), operation)
            } else {
                // Exact alarms denied: still ask for the best the OS will give,
                // and lean on the heartbeat for the rest.
                Log.w(TAG, "exact alarms unavailable — falling back to inexact + heartbeat")
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, operation)
            }
            Log.i(TAG, "armed for $at")
        } catch (e: SecurityException) {
            Log.e(TAG, "alarm refused; heartbeat is now the only wake source", e)
        }
    }

    open fun cancel() {
        manager?.cancel(wakeIntent())
    }

    /**
     * Arm a near-term alarm whose only job is to start the service.
     *
     * This is the **third** keep-alive belt, behind the next-fire alarm and the
     * 30 s in-service heartbeat. Both of those assume the service is alive; an
     * OEM battery killer breaks that assumption, and the tablet then sits there
     * looking fine until nothing rings at 04:00.
     *
     * It has to go through a broadcast receiver rather than
     * `PendingIntent.getService`. A [org.dhamma.gong.service.LivenessWorker]
     * runs in the background, where starting a `mediaPlayback` foreground
     * service is refused outright on modern Android — but the delivery of an
     * exact alarm grants a short allowlist window, and
     * [org.dhamma.gong.service.KickstartReceiver] spends it on exactly one
     * `startForegroundService` call.
     *
     * @param delayMs how far ahead to arm. Small on purpose — this is a
     *   resurrection, not a schedule.
     */
    open fun armKickstart(delayMs: Long = 3_000) {
        val am = manager ?: return
        val at = System.currentTimeMillis() + delayMs
        try {
            if (canScheduleExact()) {
                am.setAlarmClock(AlarmManager.AlarmClockInfo(at, showIntent()), kickstartIntent())
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, kickstartIntent())
            }
            Log.i(TAG, "kickstart armed in ${delayMs}ms")
        } catch (e: SecurityException) {
            Log.e(TAG, "kickstart refused", e)
        }
    }

    private fun kickstartIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_KICKSTART,
        Intent(context, org.dhamma.gong.service.KickstartReceiver::class.java)
            .setAction(org.dhamma.gong.service.KickstartReceiver.ACTION_KICKSTART),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun wakeIntent(): PendingIntent = PendingIntent.getService(
        context,
        REQUEST_WAKE,
        Intent(context, GongService::class.java).setAction(GongService.ACTION_ALARM),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Tapping the system alarm icon opens the dashboard. */
    private fun showIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        REQUEST_SHOW,
        Intent(context, org.dhamma.gong.ui.MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private companion object {
        const val TAG = "AlarmScheduler"
        const val REQUEST_WAKE = 100
        const val REQUEST_SHOW = 101
        const val REQUEST_KICKSTART = 102
    }
}
