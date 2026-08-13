package org.dhamma.gong.ui

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.dhamma.gong.relay.RelayController
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Amp power — the Shelly relay that switches the centre amplifier
 * (`docs/superpowers/specs/2026-08-09-shelly-relay-design.md`, "Screen").
 *
 * Two honesty rules shape this screen:
 *
 * 1. **Reachability has three states, not two.** `RelayController.State.reachable`
 *    is a `Boolean?`, and `null` means *never probed*. It is painted neutral —
 *    never green, and never the red that would accuse a working Shelly of being
 *    down. The screen never claims reachable without a probe behind it.
 * 2. **A silent failure on a mains relay is unacceptable.** Whenever there is a
 *    `lastError` it is shown verbatim, next to the action and timestamp that
 *    produced it.
 *
 * The relay password is a LAN device credential. Like the PIN it is never
 * rendered: the stored value is never read into this screen at all, the entry
 * field is masked, and only "set"/"not set" is reported.
 */
@Composable
fun RelayScreen(vm: AppViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val relay by vm.relayState.collectAsStateWithLifecycle()
    val zone by vm.applianceZone.collectAsStateWithLifecycle()

    // Room is the truth for configuration; RelayController.State is the truth
    // for what the network actually did. Neither is asked to speak for the other.
    val host = settings["relay_host"].orEmpty()
    val configured = host.isNotBlank()
    val enabled = settings["relay_enabled"] == "1"
    val passwordSet = settings["relay_auth_pass"].orEmpty().isNotEmpty()

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
                "Amp power",
                "The relay switches the amplifier on just before a gong and off " +
                    "just after. The gong rings on time whether or not the relay " +
                    "answers.",
            )

            HeadlineRow(configured, enabled, relay)

            val connection: @Composable () -> Unit = {
                ConnectionCard(vm, settings, configured, passwordSet)
            }
            val status: @Composable () -> Unit = {
                StatusCard(relay, configured, zone)
            }

            if (wide) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(22.dp),
                ) {
                    Box(Modifier.weight(1f)) { connection() }
                    Box(Modifier.width(394.dp)) { status() }
                }
            } else {
                connection()
                status()
            }

            val override: @Composable () -> Unit = { OverrideCard(vm, configured, relay) }
            val timing: @Composable () -> Unit = { TimingCard(vm, settings) }

            if (wide) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(22.dp),
                ) {
                    Box(Modifier.weight(1f)) { override() }
                    Box(Modifier.width(394.dp)) { timing() }
                }
            } else {
                override()
                timing()
            }
        }
    }
}

// ---------------------------------------------------------------- headline

@Composable
private fun HeadlineRow(
    configured: Boolean,
    enabled: Boolean,
    relay: RelayController.State,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        when {
            !configured -> Tag("NOT CONFIGURED", Nocturne.Neutral500)
            !enabled -> Tag("RELAY OFF", Nocturne.Neutral500)
            else -> Tag("RELAY ON", Nocturne.Ok)
        }
        if (configured && relay.armed) Tag("AMP BELIEVED ON", Nocturne.Warning)
        Text(
            when {
                !configured -> "Set the Shelly's address below. Until then the relay does nothing."
                !enabled -> "Configured, but automatic switching is off. Turn Relay on from the Dashboard."
                else -> "Automatic switching follows the schedule. Manual override is always available."
            },
            fontSize = 12.5.sp,
            color = Nocturne.Neutral500,
        )
    }
}

// ---------------------------------------------------------------- connection

@Composable
private fun ConnectionCard(
    vm: AppViewModel,
    settings: Map<String, String>,
    configured: Boolean,
    passwordSet: Boolean,
) {
    SurfaceCard(Modifier.fillMaxWidth()) {
        Eyebrow("Shelly connection")
        Spacer(Modifier.height(4.dp))
        Text(
            "Saved as you leave each field — there is no save button. Give the " +
                "Shelly a fixed address on the centre router so this stays true.",
            fontSize = 12.5.sp,
            color = Nocturne.Neutral500,
        )
        Spacer(Modifier.height(14.dp))

        Eyebrow("Host or IP")
        Spacer(Modifier.height(6.dp))
        CommittingField(
            stored = settings["relay_host"].orEmpty(),
            placeholder = "192.168.1.50",
            description = "Relay host or IP address",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        ) { vm.setRelaySetting("relay_host", it.trim(), "Relay host saved") }

        Spacer(Modifier.height(12.dp))
        Eyebrow("Username")
        Spacer(Modifier.height(6.dp))
        CommittingField(
            stored = settings["relay_auth_user"].orEmpty(),
            placeholder = "admin",
            description = "Relay username",
        ) { vm.setRelaySetting("relay_auth_user", it.trim(), "Relay username saved") }

        Spacer(Modifier.height(12.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Eyebrow("Password")
            if (passwordSet) Tag("SET", Nocturne.Ok) else Tag("NOT SET", Nocturne.Neutral500)
        }
        Spacer(Modifier.height(6.dp))
        MaskedEntryField(vm)
        Spacer(Modifier.height(6.dp))
        Text(
            "Never displayed. Leave blank if the Shelly has no password; type a " +
                "new one to replace what is stored.",
            fontSize = 12.5.sp,
            color = Nocturne.Neutral500,
        )

        Spacer(Modifier.height(14.dp))
        Hairline()
        Spacer(Modifier.height(14.dp))

        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Stepper(
                label = "Switch id",
                value = settings["relay_switch_id"].orEmpty().toIntOrNull() ?: 0,
                min = 0,
                max = 9,
                unit = "",
            ) { vm.setRelaySetting("relay_switch_id", it.toString(), "Switch id set to $it") }
            Spacer(Modifier.width(2.dp))
            if (configured) {
                PrimaryButton("Test connection") { vm.relayTest() }
            } else {
                InertButton("Test connection", "Test connection, needs a relay host first")
            }
        }
    }
}

/**
 * Password entry, masked exactly as `PinScreens.PinField` masks the PIN. The
 * stored password is never loaded in, so there is nothing here to reveal; an
 * empty buffer is never committed, so blanking the field cannot silently
 * unset a working credential — "Clear password" is the explicit way to do that.
 */
@Composable
private fun MaskedEntryField(vm: AppViewModel) {
    var typed by remember { mutableStateOf("") }
    var focused by remember { mutableStateOf(false) }

    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .weight(1f)
                .height(Nocturne.MIN_TOUCH_DP.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Nocturne.SurfaceHigh)
                .border(1.dp, Nocturne.Neutral700, RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (typed.isEmpty()) {
                Text(
                    "unchanged",
                    fontSize = 13.5.sp,
                    color = Nocturne.Neutral600,
                    maxLines = 1,
                )
            }
            BasicTextField(
                value = typed,
                onValueChange = { typed = it },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation('•'),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                textStyle = TextStyle(
                    fontSize = 13.5.sp,
                    color = Nocturne.Text,
                    fontFamily = Nocturne.Mono,
                    letterSpacing = 3.sp,
                ),
                cursorBrush = SolidColor(Nocturne.Accent),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "New relay password" }
                    .onFocusChanged { focus ->
                        val had = focused
                        focused = focus.hasFocus
                        if (had && !focus.hasFocus && typed.isNotEmpty()) {
                            vm.setRelaySetting("relay_auth_pass", typed, "Relay password saved")
                            typed = ""
                        }
                    },
            )
        }
        OutlineButton("Clear password", Nocturne.Neutral300) {
            typed = ""
            vm.setRelaySetting("relay_auth_pass", "", "Relay password cleared")
        }
    }
}

// ---------------------------------------------------------------- status

@Composable
private fun StatusCard(
    relay: RelayController.State,
    configured: Boolean,
    zone: ZoneId,
) {
    val stamp = remember { DateTimeFormatter.ofPattern("EEE d MMM · HH:mm:ss") }

    // The third state is the point. `null` is "never probed" and gets its own
    // neutral paint — it is not a failure, and it is not permission to claim
    // the Shelly is up.
    val (dot, reach) = when {
        !configured -> Nocturne.Neutral600 to "not configured"
        relay.reachable == null -> Nocturne.Neutral600 to "not probed yet"
        relay.reachable == true -> Nocturne.Ok to "reachable"
        else -> Nocturne.Error to "unreachable"
    }

    SurfaceCard(Modifier.fillMaxWidth()) {
        Eyebrow("Relay status")
        Spacer(Modifier.height(12.dp))

        Row(
            Modifier.fillMaxWidth().heightIn(min = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.clearAndSetSemantics {}) { Dot(dot) }
            Text(
                reach,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = Nocturne.Mono,
                color = dot,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            if (relay.reachable == null) {
                "Nothing has been sent to the Shelly yet. Tap Test connection to find out."
            } else {
                "From the last call the appliance made — not a live ping."
            },
            fontSize = 12.5.sp,
            color = Nocturne.Neutral500,
        )

        Spacer(Modifier.height(12.dp))
        Hairline()
        Spacer(Modifier.height(12.dp))

        InfoRow("Device", relay.deviceInfo.ifBlank { "—" })
        Spacer(Modifier.height(8.dp))
        InfoRow(
            "Last action",
            if (relay.lastAction.isBlank()) {
                "none this session"
            } else {
                "${relay.lastAction} · ${if (relay.lastActionOk) "ok" else "failed"}"
            },
            when {
                relay.lastAction.isBlank() -> Nocturne.Text
                relay.lastActionOk -> Nocturne.Ok
                else -> Nocturne.Error
            },
        )
        Spacer(Modifier.height(8.dp))
        InfoRow(
            "At",
            relay.lastActionAt?.withZoneSameInstant(zone)?.format(stamp) ?: "—",
        )
        Spacer(Modifier.height(8.dp))
        InfoRow("Amp", if (relay.armed) "believed on" else "believed off")

        if (relay.lastError.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Nocturne.Error.copy(alpha = 0.12f))
                    .border(1.dp, Nocturne.Error.copy(alpha = 0.36f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Column {
                    Eyebrow("Last error")
                    Spacer(Modifier.height(4.dp))
                    Text(
                        relay.lastError,
                        fontSize = 12.5.sp,
                        fontFamily = Nocturne.Mono,
                        color = Nocturne.Error,
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(
            "Relay activity is deliberately kept off the Logs screen — that is " +
                "the record of what actually played.",
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
        Text(label, fontSize = 12.5.sp, color = Nocturne.Neutral500, modifier = Modifier.width(84.dp))
        Text(value, fontSize = 12.5.sp, fontFamily = Nocturne.Mono, color = color)
    }
}

// ---------------------------------------------------------------- override

@Composable
private fun OverrideCard(
    vm: AppViewModel,
    configured: Boolean,
    relay: RelayController.State,
) {
    SurfaceCard(Modifier.fillMaxWidth()) {
        Eyebrow("Manual override")
        Spacer(Modifier.height(4.dp))
        Text(
            "Switches the amp now, regardless of the schedule — and regardless of " +
                "clock trust, because a person is standing at the tablet. Every " +
                "ON also arms the Shelly's own auto-off timer, so a dead tablet " +
                "cannot leave the amp energised overnight.",
            fontSize = 12.5.sp,
            color = Nocturne.Neutral400,
        )
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (configured) {
                PrimaryButton("Amp on") { vm.relayManualOn() }
                OutlineButton("Amp off", Nocturne.Neutral300) { vm.relayManualOff() }
            } else {
                InertButton("Amp on", "Amp on, needs a relay host first")
                InertButton("Amp off", "Amp off, needs a relay host first")
            }
        }
        if (configured && relay.reachable == false) {
            Spacer(Modifier.height(12.dp))
            Text(
                "The last call did not reach the Shelly. These buttons will still " +
                    "try; the schedule is unaffected either way.",
                fontSize = 12.5.sp,
                color = Nocturne.Warning,
            )
        }
    }
}

// ---------------------------------------------------------------- timing

@Composable
private fun TimingCard(vm: AppViewModel, settings: Map<String, String>) {
    SurfaceCard(Modifier.fillMaxWidth()) {
        Eyebrow("Lead and lag")
        Spacer(Modifier.height(4.dp))
        Text(
            "Lead is how long the amp warms before a gong; lag is how long it " +
                "stays on after. The amp may come on up to 30 s early — pre-arming " +
                "rides the scheduler heartbeat rather than adding an alarm.",
            fontSize = 12.5.sp,
            color = Nocturne.Neutral400,
        )
        Spacer(Modifier.height(14.dp))
        Stepper(
            label = "Lead",
            value = settings["relay_lead_seconds"].orEmpty().toIntOrNull() ?: 5,
            min = 0,
            max = 120,
            unit = "s",
        ) { vm.setRelaySetting("relay_lead_seconds", it.toString(), "Lead set to ${it}s") }
        Spacer(Modifier.height(10.dp))
        Stepper(
            label = "Lag",
            value = settings["relay_lag_seconds"].orEmpty().toIntOrNull() ?: 5,
            min = 0,
            max = 120,
            unit = "s",
        ) { vm.setRelaySetting("relay_lag_seconds", it.toString(), "Lag set to ${it}s") }
    }
}

// ---------------------------------------------------------------- pieces




/** The same pill, drawn dead: no host, so there is nothing honest to do. */
@Composable
private fun InertButton(label: String, description: String) {
    Box(
        Modifier
            .height(Nocturne.MIN_TOUCH_DP.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, Nocturne.Neutral800, RoundedCornerShape(8.dp))
            .semantics {
                contentDescription = description
                disabled()
            }
            .alpha(0.42f)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 13.5.sp, color = Nocturne.Neutral400)
    }
}
