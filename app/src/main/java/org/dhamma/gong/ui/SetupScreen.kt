package org.dhamma.gong.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import org.dhamma.gong.service.AppliancePermissions
import java.time.format.DateTimeFormatter

/**
 * Setup — the first-run grant checklist (M5 minimal, design doc §08–§10).
 *
 * None of these three grants stop the scheduler from starting; they decide
 * whether a centre tablet is still firing at 04:00 three weeks later. The
 * checklist exists so staff can see, before they walk away from the device,
 * which ones the OS is still withholding.
 *
 * Health must not lie: a row goes green only when the underlying status is
 * actually true (FABLE-REVIEW B6/B14).
 */
@Composable
fun SetupScreen(vm: AppViewModel) {
    val permissions by vm.permissionStatus.collectAsStateWithLifecycle()
    val state by vm.schedulerState.collectAsStateWithLifecycle()
    val zone by vm.applianceZone.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Staff leave for a system settings page and come back. Re-read on every
    // return to RESUMED so the checklist reflects what they just did.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            vm.refreshPermissionStatus()
        }
    }

    // Prefer the scheduler's own live view of exact alarms where it has one:
    // it is the component that actually failed to arm.
    val exactOk = permissions.exactAlarmsAllowed && state.exactAlarmsAllowed
    val timeFmt = remember { DateTimeFormatter.ofPattern("HH:mm:ss") }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 900.dp
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 32.dp, end = 32.dp, top = 26.dp, bottom = 26.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            ScreenTitle(
                "Setup",
                "Three OS grants decide whether this tablet is still gonging in " +
                    "three weeks. Amber rows are not fatal — they are unreliable.",
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (permissions.allOk) {
                    Tag("ALL GRANTED", Nocturne.Ok)
                } else {
                    Tag("ACTION NEEDED", Nocturne.Warning)
                }
                Text(
                    if (permissions.allOk) {
                        "Nothing left to grant on this device."
                    } else {
                        "Tap an amber row to open the matching system page."
                    },
                    fontSize = 12.5.sp,
                    color = Nocturne.Neutral500,
                )
            }

            val checklist: @Composable () -> Unit = {
                SurfaceCard(Modifier.fillMaxWidth()) {
                    Eyebrow("Permission checklist")
                    Spacer(Modifier.height(12.dp))

                    CheckRow(
                        label = "Notifications",
                        granted = permissions.notificationsGranted,
                        grantedText = "allowed",
                        deniedText = "blocked",
                        why = "The foreground-service notification is how staff and the " +
                            "OS both see the appliance is alive.",
                        action = "Open notification settings",
                        onAction = { AppliancePermissions.openAppNotificationSettings(context) },
                    )
                    Spacer(Modifier.height(10.dp))
                    Hairline()
                    Spacer(Modifier.height(10.dp))

                    CheckRow(
                        label = "Exact alarms",
                        granted = exactOk,
                        grantedText = "allowed",
                        deniedText = "denied",
                        why = "Without them the gong lands on the heartbeat instead of " +
                            "the second, and may miss its grace window.",
                        action = "Open alarm settings",
                        onAction = { AppliancePermissions.openExactAlarmSettings(context) },
                    )
                    Spacer(Modifier.height(10.dp))
                    Hairline()
                    Spacer(Modifier.height(10.dp))

                    CheckRow(
                        label = "Battery",
                        granted = permissions.batteryUnrestricted,
                        grantedText = "unrestricted",
                        deniedText = "restricted",
                        why = "OEM battery savers freeze the service overnight — this is " +
                            "the grant that most often costs a 04:00 gong.",
                        action = "Open battery settings",
                        onAction = { AppliancePermissions.openBatterySettings(context) },
                    )
                }
            }

            val applianceState: @Composable () -> Unit = {
                SurfaceCard(Modifier.fillMaxWidth()) {
                    Eyebrow("Appliance state")
                    Spacer(Modifier.height(12.dp))
                    StateRow(
                        "Scheduler",
                        if (state.running) "running" else "stopped",
                        if (state.running) Nocturne.Ok else Nocturne.Error,
                    )
                    Spacer(Modifier.height(8.dp))
                    StateRow(
                        "Last tick",
                        state.lastTick
                            ?.withZoneSameInstant(zone)
                            ?.format(timeFmt)
                            ?: "—",
                        if (state.lastTick != null) Nocturne.Ok else Nocturne.Neutral600,
                    )
                    Spacer(Modifier.height(8.dp))
                    StateRow(
                        "Clock",
                        if (state.clockTrusted) "trusted" else "untrusted — see Time",
                        if (state.clockTrusted) Nocturne.Ok else Nocturne.Warning,
                    )
                    Spacer(Modifier.height(8.dp))
                    StateRow(
                        "Course today",
                        state.course?.let { "day ${it.day}" } ?: "none",
                        if (state.course != null) Nocturne.Ok else Nocturne.Neutral600,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "The scheduler lives in the service, not in this screen. " +
                            "Closing the app leaves it running.",
                        fontSize = 12.5.sp,
                        color = Nocturne.Neutral500,
                    )
                }
            }

            if (wide) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(22.dp),
                ) {
                    Box(Modifier.weight(1f)) { checklist() }
                    Box(Modifier.width(394.dp)) { applianceState() }
                }
            } else {
                checklist()
                applianceState()
            }
        }
    }
}

// ---------------------------------------------------------------- pieces

/**
 * One checklist item. The whole row is the tap target when the grant is
 * missing (>= 44 dp), with the button as the visible affordance — both run
 * the same handler, so a tap anywhere does the right thing.
 */
@Composable
private fun CheckRow(
    label: String,
    granted: Boolean,
    grantedText: String,
    deniedText: String,
    why: String,
    action: String,
    onAction: () -> Unit,
) {
    val dot = if (granted) Nocturne.Ok else Nocturne.Warning
    val rowMod = if (granted) {
        Modifier.fillMaxWidth()
    } else {
        Modifier
            .fillMaxWidth()
            .heightIn(min = Nocturne.MIN_TOUCH_DP.dp)
            .clip(RoundedCornerShape(8.dp))
            .semantics { contentDescription = "$label: $deniedText. $action" }
            .clickable(role = Role.Button, onClick = onAction)
    }
    Column(rowMod) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.clearAndSetSemantics {}) { Dot(dot) }
            Text(
                label,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = Nocturne.Text,
                modifier = Modifier.weight(1f),
            )
            Text(
                if (granted) grantedText else deniedText,
                fontSize = 12.5.sp,
                fontFamily = Nocturne.Mono,
                color = dot,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(why, fontSize = 12.5.sp, color = Nocturne.Neutral500)
        if (!granted) {
            Spacer(Modifier.height(10.dp))
            PrimaryButton(action, onClick = onAction)
        }
    }
}

@Composable
private fun StateRow(label: String, value: String, dot: Color) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.clearAndSetSemantics {}) { Dot(dot) }
        Text(label, fontSize = 12.5.sp, color = Nocturne.Neutral500, modifier = Modifier.width(100.dp))
        Text(value, fontSize = 12.5.sp, fontFamily = Nocturne.Mono, color = Nocturne.Text)
    }
}
