package org.dhamma.gong.service

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * Appliance first-run surface for the three OS grants that decide whether a
 * centre tablet keeps firing overnight (FABLE-REVIEW B6 / design doc §09–§10):
 *
 * 1. POST_NOTIFICATIONS — health indicator in the shade
 * 2. Exact alarms — `setAlarmClock` (heartbeat still covers the rest)
 * 3. Unrestricted battery — OEM killers that freeze the FGS + alarms
 *
 * None of these block the scheduler from starting; they only improve
 * reliability. The dashboard health card surfaces the denials as tappable rows.
 */
object AppliancePermissions {

    data class Status(
        val notificationsGranted: Boolean,
        val exactAlarmsAllowed: Boolean,
        val batteryUnrestricted: Boolean,
    ) {
        val allOk: Boolean
            get() = notificationsGranted && exactAlarmsAllowed && batteryUnrestricted
    }

    fun status(context: Context): Status {
        val notifications = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        return Status(
            notificationsGranted = notifications,
            exactAlarmsAllowed = canScheduleExactAlarms(context),
            batteryUnrestricted = isBatteryUnrestricted(context),
        )
    }

    fun canScheduleExactAlarms(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val am = context.getSystemService(AlarmManager::class.java) ?: return false
        return am.canScheduleExactAlarms()
    }

    fun isBatteryUnrestricted(context: Context): Boolean {
        val pm = context.getSystemService(PowerManager::class.java) ?: return true
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * Opens the system sheet that grants SCHEDULE_EXACT_ALARM for this package.
     * No-op below API 31 (exact alarms are unrestricted there).
     */
    fun openExactAlarmSettings(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    /**
     * Opens the battery-optimisation exemption dialog when still restricted.
     * Falls back to the app's system details page if the OEM rejects the
     * direct request intent.
     */
    fun openBatterySettings(context: Context) {
        if (isBatteryUnrestricted(context)) return
        val direct = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val ok = runCatching { context.startActivity(direct) }.isSuccess
        if (!ok) {
            val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching { context.startActivity(fallback) }
        }
    }

    fun openAppNotificationSettings(context: Context) {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }
}
