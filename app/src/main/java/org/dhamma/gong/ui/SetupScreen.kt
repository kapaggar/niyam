package org.dhamma.gong.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.AlertDialog
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
import kotlinx.coroutines.delay
import org.dhamma.gong.BuildConfig
import org.dhamma.gong.domain.BackupCheck
import org.dhamma.gong.domain.Liveness
import org.dhamma.gong.domain.PinCode
import org.dhamma.gong.domain.Readiness
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
    val pinHash by vm.pinHash.collectAsStateWithLifecycle()
    val now = rememberNow()
    val context = LocalContext.current

    // Staff leave for a system settings page and come back. Re-read on every
    // return to RESUMED so the checklist reflects what they just did.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            // Re-poll rather than read once. A grant can land asynchronously
            // after the OEM's own dialog closes, and a checklist that still
            // says "denied" sends staff round the loop a second time.
            while (true) {
                vm.refreshPermissionStatus()
                delay(1_000)
            }
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

            // One honest verdict. Grants alone are not enough — a tablet with
            // every permission and a frozen scheduler is not ready, and that is
            // exactly the state an OEM battery killer leaves behind.
            val checks = Readiness.Checks(
                notificationsGranted = permissions.notificationsGranted,
                exactAlarmsAllowed = exactOk,
                batteryUnrestricted = permissions.batteryUnrestricted,
                serviceAlive = Liveness.isAlive(state.lastTick, now),
                pinSet = PinCode.isSet(pinHash),
            )
            val ready = Readiness.isReady(checks)
            Banner(
                text = (if (ready) "READY — " else "NOT READY — ") + Readiness.summary(checks)
                    .removePrefix("Not ready — ")
                    .replaceFirstChar { it.uppercase() },
                color = if (ready) Nocturne.Ok else Nocturne.Warning,
            )
            if (!ready) {
                Text(
                    "Tap an amber row below to open the matching system page.",
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
                    // Age, not just a timestamp. A clock reading 04:12:31 tells
                    // nobody whether the loop is alive; "6s ago" does, and a
                    // healthy appliance never exceeds the 30 s heartbeat by much.
                    StateRow(
                        "Last tick",
                        state.lastTick?.let {
                            "${it.withZoneSameInstant(zone).format(timeFmt)} " +
                                "· ${Liveness.ageLabel(state.lastTick, now)}"
                        } ?: "never",
                        when (Liveness.health(state.lastTick, now)) {
                            Liveness.Health.ALIVE -> Nocturne.Ok
                            Liveness.Health.STALE -> Nocturne.Error
                            Liveness.Health.UNKNOWN -> Nocturne.Neutral600
                        },
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
                    Spacer(Modifier.height(8.dp))
                    // The one place a tester can answer "which build is this?"
                    // without a cable. Every substantive change bumps it
                    // (AGENTS.md hard rule 11), so a bug that reappears can be
                    // told apart from an APK that never actually installed.
                    StateRow(
                        "Build",
                        "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                        Nocturne.Neutral600,
                    )
                    Spacer(Modifier.height(8.dp))
                    // Presence only — never the key, never its length. Two APKs
                    // can share a version and differ here, which is exactly the
                    // confusion this row exists to end: "downloads are disabled"
                    // is a property of the build, not a fault on the tablet.
                    StateRow(
                        "Media key",
                        if (BuildConfig.MEDIA_PASSPHRASE.isEmpty()) {
                            "absent — doha downloads off"
                        } else {
                            "present"
                        },
                        if (BuildConfig.MEDIA_PASSPHRASE.isEmpty()) {
                            Nocturne.Neutral600
                        } else {
                            Nocturne.Ok
                        },
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

            // PIN lives here rather than in its own nav entry: it is an
            // install-day decision, made once by the same person working
            // through the grants above, not something staff visit.
            SecurityCard(vm)

            BackupCard(vm, zone)
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

// ---------------------------------------------------------------- backup

/**
 * Backup and restore.
 *
 * The file is configuration only — settings, courses, schedule, slot mapping.
 * It deliberately carries no `fired:` guards, no play log, no PIN and no relay
 * password; [org.dhamma.gong.domain.BackupFile] explains why each of those
 * would break an appliance rather than help it.
 *
 * Restore is two taps with the numbers in between. It replaces the whole
 * schedule, and "replace your 12 courses with 39" is a different decision from
 * "restore" — so the confirm sheet states both sides before anything is written.
 */
@Composable
private fun BackupCard(vm: AppViewModel, zone: java.time.ZoneId) {
    val pending by vm.pendingRestore.collectAsStateWithLifecycle()
    val today = remember(zone) { java.time.LocalDate.now(zone).toString() }

    val save = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> vm.exportBackup(uri) }

    val open = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> vm.inspectBackup(uri) }

    SurfaceCard(Modifier.fillMaxWidth()) {
        Eyebrow("Backup")
        Spacer(Modifier.height(4.dp))
        Text(
            "Settings, courses, the schedule matrix and the doha slot mapping, " +
                "as one readable file. Keep a copy off the tablet — a dead " +
                "device otherwise means re-entering the whole calendar by hand.",
            fontSize = 12.5.sp,
            color = Nocturne.Neutral500,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "Not included, on purpose: the PIN, the relay password, the doha " +
                "folder grant, and the record of which gongs have already " +
                "fired. Restoring that last one would tell the appliance " +
                "today's gongs were already rung.",
            fontSize = 12.5.sp,
            color = Nocturne.Neutral500,
        )
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryButton("Save a backup") {
                save.launch(org.dhamma.gong.domain.BackupFile.suggestedName(today))
            }
            OutlineButton("Restore from file") {
                // Some providers mislabel .json; accept anything and let the
                // parser be the judge rather than hiding the file staff picked.
                open.launch(arrayOf("application/json", "text/plain", "*/*"))
            }
        }
    }

    pending?.let { RestoreConfirm(it, onCancel = vm::dismissRestore, onConfirm = vm::confirmRestore) }
}

@Composable
private fun RestoreConfirm(
    pending: AppViewModel.PendingRestore,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    val check = pending.check
    val ok = check as? BackupCheck.Result.Ok

    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = Nocturne.Surface,
        title = {
            Text(
                if (ok == null) "That file cannot be restored" else "Replace this tablet's schedule?",
                fontSize = 17.sp,
                color = Nocturne.Text,
            )
        },
        text = {
            Column {
                if (ok == null) {
                    Text(
                        (check as BackupCheck.Result.Rejected).reason,
                        fontSize = 13.5.sp,
                        color = Nocturne.Warning,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Nothing on the tablet has been changed.",
                        fontSize = 12.5.sp,
                        color = Nocturne.Neutral500,
                    )
                } else {
                    Text(
                        "On this tablet now: ${pending.currentCourses} courses, " +
                            "${pending.currentEvents} schedule rows.",
                        fontSize = 13.5.sp,
                        color = Nocturne.Neutral300,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "In the backup: ${ok.courses} courses, ${ok.events} schedule rows, " +
                            "${ok.slots} doha slots, ${ok.settings} settings.",
                        fontSize = 13.5.sp,
                        color = Nocturne.Accent100,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text("Saved ${ok.exportedAt}.", fontSize = 12.5.sp, color = Nocturne.Neutral500)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "The courses and schedule above are replaced, not merged. " +
                            "Restored doha slots arrive unverified — rescan the " +
                            "folder in Sounds afterwards.",
                        fontSize = 12.5.sp,
                        color = Nocturne.Warning,
                    )
                }
            }
        },
        confirmButton = {
            if (ok != null) {
                PrimaryButton("Replace and restore", onClick = onConfirm)
            } else {
                PrimaryButton("Close", onClick = onCancel)
            }
        },
        dismissButton = {
            if (ok != null) OutlineButton("Cancel", Nocturne.Neutral300, onCancel)
        },
    )
}
