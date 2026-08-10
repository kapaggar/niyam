package org.dhamma.gong.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import org.dhamma.gong.domain.RoutePlan

/**
 * Audio out — the route picker, a test per route, and the last-known-good
 * indicator (design doc §08).
 *
 * The honesty rule here is the same one the player obeys: **a gong from the
 * wrong speaker beats no gong.** A route that is not plugged in when an alarm
 * fires does not become silence — the appliance falls back to the built-in
 * speaker and records that it did. So this screen has to make two different
 * things visible at once: what staff have *chosen*, and what would *actually*
 * happen if the next gong fired this second. When those disagree it says so
 * loudly, because the failure it exists to prevent is a centre discovering in
 * week three that every morning gong has been coming out of the tablet.
 *
 * Testing a route deliberately does not select it. Auditioning a Bluetooth amp
 * by pointing the whole appliance at it — and forgetting to point it back — is
 * exactly how that failure happens.
 */
@Composable
fun AudioOutScreen(vm: AppViewModel) {
    val rows by vm.audioRoutes.collectAsStateWithLifecycle()
    val choice by vm.routeChoice.collectAsStateWithLifecycle()
    val service by vm.service.collectAsStateWithLifecycle()
    val status by vm.playerStatus.collectAsStateWithLifecycle()

    // Devices appear and vanish while someone is standing at the tablet with a
    // cable in their hand, so the list is re-read on a slow poll rather than
    // once on entry. Bound to STARTED: a wall tablet with the screen off has
    // nobody to show it to.
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                vm.refreshAudioRoutes()
                delay(4_000)
            }
        }
    }

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
                "Audio out",
                "Where the gong comes out. If the chosen device is missing when an " +
                    "alarm fires, the appliance uses the built-in speaker rather " +
                    "than staying silent.",
            )

            HeadlineRow(choice)

            val picker: @Composable () -> Unit = {
                PickerCard(vm, rows, serviceRunning = service != null)
            }
            val effect: @Composable () -> Unit = {
                EffectiveCard(choice, rows, service != null, status.route)
            }

            if (wide) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(22.dp),
                ) {
                    Box(Modifier.weight(1f)) { picker() }
                    Box(Modifier.width(394.dp)) { effect() }
                }
            } else {
                picker()
                effect()
            }

            DevicesCard()
        }
    }
}

// ---------------------------------------------------------------- headline

@Composable
private fun HeadlineRow(choice: RoutePlan.Choice) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (choice.fellBack) {
            Tag("FALLING BACK", Nocturne.Warning)
        } else {
            Tag(RoutePlan.genericLabel(choice.key).uppercase(), Nocturne.Ok)
        }
        Text(
            if (choice.fellBack) {
                "${RoutePlan.genericLabel(choice.requested)} is chosen but not " +
                    "attached. Gongs are ringing from the built-in speaker."
            } else {
                "The chosen output is attached. Gongs are going where you asked."
            },
            fontSize = 12.5.sp,
            color = if (choice.fellBack) Nocturne.Warning else Nocturne.Neutral500,
        )
    }
}

// ---------------------------------------------------------------- picker

@Composable
private fun PickerCard(
    vm: AppViewModel,
    rows: List<AppViewModel.RouteRow>,
    serviceRunning: Boolean,
) {
    SurfaceCard(Modifier.fillMaxWidth()) {
        Eyebrow("Output device")
        Spacer(Modifier.height(4.dp))
        Text(
            "Tap to choose — saved immediately, there is no save button. Test rings " +
                "the gong through that device without changing your choice.",
            fontSize = 12.5.sp,
            color = Nocturne.Neutral500,
        )
        Spacer(Modifier.height(14.dp))

        if (rows.isEmpty()) {
            // The first poll has not landed. Claiming "speaker only" here would
            // be a guess, and a wrong one on a tablet with a DAC plugged in.
            Text("Reading attached devices…", fontSize = 13.5.sp, color = Nocturne.Neutral500)
        }

        for (row in rows) {
            RouteRowView(
                row = row,
                serviceRunning = serviceRunning,
                onSelect = { vm.selectAudioRoute(row.key) },
                onTest = { vm.testAudioRoute(row.key) },
            )
            Spacer(Modifier.height(6.dp))
        }

        Spacer(Modifier.height(6.dp))
        Text(
            "A chosen device that is unplugged keeps its place in this list. " +
                "Removing it from view would hide the very setting that is " +
                "quietly falling back every morning.",
            fontSize = 12.5.sp,
            color = Nocturne.Neutral500,
        )
    }
}

@Composable
private fun RouteRowView(
    row: AppViewModel.RouteRow,
    serviceRunning: Boolean,
    onSelect: () -> Unit,
    onTest: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Nocturne.MIN_TOUCH_DP.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (row.selected) Nocturne.Accent.copy(alpha = 0.14f) else Nocturne.SurfaceHigh,
            )
            .border(
                1.dp,
                if (row.selected) Nocturne.Accent.copy(alpha = 0.55f) else Nocturne.Neutral800,
                RoundedCornerShape(8.dp),
            )
            .padding(start = 12.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            Modifier
                .weight(1f)
                .heightIn(min = Nocturne.MIN_TOUCH_DP.dp)
                .semantics {
                    contentDescription = if (row.selected) {
                        "${row.label}, current output"
                    } else {
                        "Use ${row.label} for output"
                    }
                }
                .clickable(role = Role.RadioButton, onClick = onSelect),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.width(14.dp)) {
                if (row.selected) Text("✓", fontSize = 13.sp, color = Nocturne.Accent200)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    row.label,
                    fontSize = 13.5.sp,
                    color = if (row.selected) Nocturne.Accent100 else Nocturne.Text,
                    maxLines = 1,
                )
                // Two amps on one route key: staff need to know only the first
                // is what "Bluetooth" will actually reach.
                if (row.devices.size > 1) {
                    Text(
                        "also attached: ${row.devices.drop(1).joinToString(", ")}",
                        fontSize = 11.5.sp,
                        color = Nocturne.Neutral500,
                        maxLines = 1,
                    )
                }
            }
            if (!row.available) Tag("NOT ATTACHED", Nocturne.Warning)
            if (row.lastOk) Tag("LAST PLAYED", Nocturne.Ok)
        }

        if (serviceRunning) {
            SmallButton("Test", "Test the gong through ${row.label}", onTest)
        } else {
            SmallButtonInert("Test", "Test ${row.label}, needs the appliance service")
        }
    }
}

// ---------------------------------------------------------------- effective

@Composable
private fun EffectiveCard(
    choice: RoutePlan.Choice,
    rows: List<AppViewModel.RouteRow>,
    serviceRunning: Boolean,
    playerRoute: String,
) {
    val lastOk = rows.firstOrNull { it.lastOk }

    SurfaceCard(Modifier.fillMaxWidth()) {
        Eyebrow("At fire time")
        Spacer(Modifier.height(12.dp))

        Row(
            Modifier.fillMaxWidth().heightIn(min = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Dot(if (choice.fellBack) Nocturne.Warning else Nocturne.Ok)
            Text(
                RoutePlan.genericLabel(choice.key),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = Nocturne.Mono,
                color = if (choice.fellBack) Nocturne.Warning else Nocturne.Text,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Resolved fresh for every burst, never cached from setup — a device " +
                "unplugged at midnight is noticed at 04:00.",
            fontSize = 12.5.sp,
            color = Nocturne.Neutral500,
        )

        Spacer(Modifier.height(12.dp))
        Hairline()
        Spacer(Modifier.height(12.dp))

        InfoRow("Chosen", RoutePlan.genericLabel(choice.requested))
        Spacer(Modifier.height(8.dp))
        InfoRow(
            "Fallback",
            if (choice.fellBack) "in use" else "not needed",
            if (choice.fellBack) Nocturne.Warning else Nocturne.Text,
        )
        Spacer(Modifier.height(8.dp))
        InfoRow(
            "Last played",
            lastOk?.let { RoutePlan.genericLabel(it.key) } ?: "nothing yet",
        )
        Spacer(Modifier.height(8.dp))
        InfoRow("Player", playerRoute.ifBlank { "—" })

        if (!serviceRunning) {
            Spacer(Modifier.height(12.dp))
            Text(
                "The appliance service is not running, so nothing can be tested " +
                    "from here. Your choice is still saved.",
                fontSize = 12.5.sp,
                color = Nocturne.Warning,
            )
        }

        Spacer(Modifier.height(12.dp))
        Text(
            "\"Last played\" is the last route that finished a burst — a test " +
                "counts. It is the quickest proof that a device is not merely " +
                "attached but actually carrying sound.",
            fontSize = 12.5.sp,
            color = Nocturne.Neutral500,
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String, color: Color = Nocturne.Text) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            label,
            fontSize = 12.5.sp,
            color = Nocturne.Neutral500,
            modifier = Modifier.width(84.dp),
        )
        Text(value, fontSize = 12.5.sp, fontFamily = Nocturne.Mono, color = color)
    }
}

// ---------------------------------------------------------------- devices

@Composable
private fun DevicesCard() {
    val context = LocalContext.current
    SurfaceCard(Modifier.fillMaxWidth()) {
        Eyebrow("Attaching an amplifier")
        Spacer(Modifier.height(8.dp))
        Text(
            "Pairing happens in Android's own settings, not here — this screen " +
                "only chooses between what is already attached. A USB audio " +
                "interface needs nothing more than the cable.",
            fontSize = 12.5.sp,
            color = Nocturne.Neutral400,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "For a hall, prefer USB over Bluetooth. Bluetooth adds a delay of its " +
                "own between strikes, which is audible in a burst and varies by " +
                "amp; a wired interface does not.",
            fontSize = 12.5.sp,
            color = Nocturne.Neutral400,
        )
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryButton("Open Bluetooth settings") { openBluetoothSettings(context) }
        }
    }
}

private fun openBluetoothSettings(context: Context) {
    val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val ok = runCatching { context.startActivity(intent) }.isSuccess
    if (!ok) {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}

// ---------------------------------------------------------------- pieces

@Composable
private fun SmallButton(label: String, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .heightIn(min = Nocturne.MIN_TOUCH_DP.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, Nocturne.Neutral700, RoundedCornerShape(8.dp))
            .semantics { contentDescription = description }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 13.5.sp, color = Nocturne.Neutral300)
    }
}

@Composable
private fun SmallButtonInert(label: String, description: String) {
    Box(
        Modifier
            .heightIn(min = Nocturne.MIN_TOUCH_DP.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, Nocturne.Neutral800, RoundedCornerShape(8.dp))
            .semantics {
                contentDescription = description
                disabled()
            }
            .alpha(0.42f)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 13.5.sp, color = Nocturne.Neutral400)
    }
}
