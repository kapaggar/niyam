package org.dhamma.gong.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Amp power on install day: where the Shelly is, whether it answers, whether
 * the schedule drives it, and a way to switch the amp by hand (spec §7).
 *
 * Four controls, because four is what a Shelly on the centre LAN needs. Switch
 * id, lead and lag, and the device password stay on Advanced → Amp power; a
 * centre that set them there keeps them, because this card writes the same
 * settings rows [org.dhamma.gong.relay.RelayController] already reads and
 * touches nothing else.
 *
 * Three honesty rules carry over from the full screen and are not negotiable
 * here either:
 *
 * 1. **Reachability has three states.** `RelayController.State.reachable` is a
 *    `Boolean?` and `null` means *never probed* — painted neutral, never green,
 *    and never the red that would accuse a working Shelly of being down.
 * 2. **`lastError` is shown verbatim.** A silent failure on a mains relay is
 *    unacceptable; a stack trace on a wall tablet is useless. One line, as the
 *    controller reported it.
 * 3. **Reachable is not the same claim as ok.** A Shelly that answers with
 *    "authentication required" is `reachable = true` and `lastActionOk =
 *    false` — it heard the request and rejected it. Neither the status tag nor
 *    the "Amp believed on/off" sentence may paint green or assert a state that
 *    call did not produce; both fold `lastActionOk` in alongside `reachable`.
 *
 * The relay password is never read into this file at all.
 */
@Composable
fun AmpPowerSimpleCard(vm: AppViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val relay by vm.relayState.collectAsStateWithLifecycle()

    val host = settings["relay_host"].orEmpty()
    val configured = host.isNotBlank()
    val auto = settings["relay_enabled"] == "1"

    SurfaceCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Eyebrow("Amp power", Modifier.weight(1f))
            when {
                !configured -> Tag("NO ADDRESS", Nocturne.Neutral500)
                relay.reachable == null -> Tag("NOT PROBED", Nocturne.Neutral500)
                // Reachable but the call itself failed (e.g. a password the
                // Shelly rejected) is not "OK" — that word claims the switch
                // happened, and it did not.
                relay.reachable == true && !relay.lastActionOk -> Tag("ACTION FAILED", Nocturne.Error)
                relay.reachable == true -> Tag("OK", Nocturne.Ok)
                else -> Tag("UNREACHABLE", Nocturne.Error)
            }
            InfoDot(
                "Amp power",
                "A Shelly relay on the centre network switches the amplifier on " +
                    "just before a gong and off just after. The gong rings on " +
                    "time whether or not the relay answers. Every manual ON also " +
                    "arms the Shelly's own auto-off timer, so a dead tablet " +
                    "cannot leave the amp energised overnight.",
            )
        }
        Spacer(Modifier.height(12.dp))

        // Auto with schedule — `relay_enabled`. Dead until there is an address,
        // because a relay with no host cannot switch anything.
        Row(
            Modifier.fillMaxWidth().heightIn(min = Nocturne.MIN_TOUCH_DP.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Auto with schedule",
                fontSize = 13.5.sp,
                color = Nocturne.Text,
                modifier = Modifier.weight(1f),
            )
            if (configured) {
                Toggle("", auto, contentDescription = "Auto with schedule") {
                    vm.toggle("relay_enabled")
                }
            } else {
                // 42 % is the handoff's inert alpha (matches NavItem, StepButton
                // and InertButton) — not a locally invented 50 %.
                Box(Modifier.alpha(0.42f)) {
                    Toggle("", false, enabled = false, contentDescription = "Auto with schedule") {}
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Hairline()
        Spacer(Modifier.height(12.dp))

        Eyebrow("Host or IP")
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.weight(1f)) {
                CommittingField(
                    stored = host,
                    placeholder = "192.168.1.50",
                    description = "Relay host or IP address",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                ) { vm.setRelaySetting("relay_host", it.trim(), "Relay host saved") }
            }
            // Blank host is not an error state to hide the button for — staff
            // tap Test to find out what is wrong, so say what is wrong.
            OutlineButton("Test", Nocturne.Neutral300) {
                if (configured) vm.relayTest() else vm.toast("Enter the Shelly's IP first.")
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Saved as you leave the field. Give the Shelly a fixed address on the " +
                "centre router so this stays true.",
            fontSize = 12.5.sp,
            color = Nocturne.Neutral500,
        )

        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlineButton("Amp on", Nocturne.Neutral300) {
                if (configured) vm.relayManualOn() else vm.toast("Enter the Shelly's IP first.")
            }
            OutlineButton("Amp off", Nocturne.Neutral300) {
                if (configured) vm.relayManualOff() else vm.toast("Enter the Shelly's IP first.")
            }
        }

        Spacer(Modifier.height(12.dp))
        // Reachable-but-failed gets its own branch rather than falling into
        // "Amp believed on/off": `armed` is set optimistically the moment a
        // manual switch is requested, before the network call resolves, and a
        // rejected call must not be read back as if it had gone through.
        val actionFailed = relay.reachable == true && !relay.lastActionOk
        Text(
            when {
                !configured -> "No address yet. Until one is set the relay does nothing."
                relay.reachable == null -> "Nothing has been sent to the Shelly yet. Tap Test."
                // `armed` is not checked before this: it flips the moment a
                // manual switch is requested, before the network call
                // resolves, so it is set on a call this branch is about to
                // report as rejected too — checking reachability first, then
                // the call outcome, then armed keeps each branch honest about
                // what actually happened rather than what was merely intended.
                relay.reachable == false -> "The last call did not reach the Shelly. The schedule is unaffected."
                actionFailed -> "Reachable, but the last call failed. The amp's actual state is unknown."
                relay.armed -> "Reachable. Amp believed on."
                else -> "Reachable. Amp believed off."
            },
            fontSize = 12.5.sp,
            color = if (relay.reachable == false || actionFailed) Nocturne.Warning else Nocturne.Neutral500,
        )

        if (relay.lastError.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                relay.lastError,
                fontSize = 12.5.sp,
                fontFamily = Nocturne.Mono,
                color = Nocturne.Error,
            )
        }
    }
}
