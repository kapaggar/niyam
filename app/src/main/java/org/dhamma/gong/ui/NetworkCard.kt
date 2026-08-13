package org.dhamma.gong.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import org.dhamma.gong.domain.NetworkFacts
import org.dhamma.gong.net.NetworkSettings

/**
 * The network facts, as a Setup card rather than a destination (spec §6).
 *
 * Nothing on this appliance depends on a connection — it fires gongs and dohas
 * in airplane mode forever, and the only thing a network buys is the on-demand
 * doha download. That is exactly why it stopped being a rail entry: a screen
 * whose whole job is to report "still offline, still fine" does not deserve a
 * tap of its own. Someone on the phone still needs the tablet's address, so the
 * facts survive; the paragraph explaining Android's location rule moved behind
 * the ⓘ, where curiosity can find it and nobody else has to read it.
 *
 * The screen refuses to guess. Android will not name the Wi-Fi network to an
 * app without a location permission this appliance deliberately never requests,
 * and has offered no way to ask "am I tethering" since API 28. Both blanks are
 * labelled as blanks.
 */
@Composable
fun NetworkCard(vm: AppViewModel, advanced: Boolean) {
    val facts by vm.network.collectAsStateWithLifecycle()
    val probed by vm.networkProbed.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // A cable pulled or a hotspot started elsewhere shows up on the next poll.
    // STARTED-bound: nothing polls behind a dark screen.
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                vm.refreshNetwork()
                delay(5_000)
            }
        }
    }

    SurfaceCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Eyebrow("Network", Modifier.weight(1f))
            when {
                !probed -> Tag("CHECKING", Nocturne.Neutral500)
                !facts.online -> Tag("OFFLINE", Nocturne.Neutral500)
                !facts.validated -> Tag("NO INTERNET", Nocturne.Warning)
                else -> Tag(modeLabel(facts.mode).uppercase(), Nocturne.Ok)
            }
            if (probed && facts.metered) Tag("METERED", Nocturne.Warning)
            InfoDot(
                "Network",
                "The gong and doha schedule runs with no network at all — only " +
                    "doha downloads need one. Android names the Wi-Fi network " +
                    "to an app only if it holds a location permission, and this " +
                    "one does not ask for one; open Wi-Fi settings to see the name.",
            )
        }
        Spacer(Modifier.height(12.dp))

        NetRow(
            "Kind",
            if (probed) modeLabel(facts.mode) else "checking",
            when {
                !probed || !facts.online -> Nocturne.Neutral400
                facts.validated -> Nocturne.Ok
                else -> Nocturne.Warning
            },
        )
        Spacer(Modifier.height(8.dp))
        NetRow(
            "Network",
            when {
                facts.ssid != null -> facts.ssid!!
                facts.ssidWithheld -> "name withheld"
                facts.mode == NetworkFacts.Mode.ETHERNET -> "wired"
                else -> "—"
            },
            if (facts.ssidWithheld) Nocturne.Neutral400 else Nocturne.Text,
        )
        Spacer(Modifier.height(8.dp))
        NetRow("Address", facts.ip ?: "—")
        Spacer(Modifier.height(8.dp))
        NetRow(
            "Internet",
            when {
                !facts.online -> "no connection"
                facts.validated -> "reachable"
                else -> "not reachable"
            },
            when {
                !facts.online -> Nocturne.Neutral400
                facts.validated -> Nocturne.Ok
                else -> Nocturne.Warning
            },
        )

        // Advanced keeps the metered line; Simple gets the four facts and the
        // two buttons that change them. The withheld-SSID explanation lives
        // once, behind the ⓘ above — not repeated here for whoever reads it.
        if (advanced) {
            Spacer(Modifier.height(8.dp))
            NetRow(
                "Data",
                if (facts.metered) "metered" else "unmetered",
                if (facts.metered) Nocturne.Warning else Nocturne.Text,
            )
        }

        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlineButton("Wi-Fi settings", Nocturne.Neutral300) { NetworkSettings.openWifi(context) }
            OutlineButton("Hotspot settings", Nocturne.Neutral300) { NetworkSettings.openHotspot(context) }
        }
    }
}

private fun modeLabel(mode: NetworkFacts.Mode): String = when (mode) {
    NetworkFacts.Mode.OFFLINE -> "Offline"
    NetworkFacts.Mode.WIFI -> "Wi-Fi"
    NetworkFacts.Mode.ETHERNET -> "Ethernet"
    NetworkFacts.Mode.CELLULAR -> "Mobile data"
    NetworkFacts.Mode.OTHER -> "Other"
}

@Composable
private fun NetRow(label: String, value: String, color: Color = Nocturne.Text) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.clearAndSetSemantics {}) { Spacer(Modifier.width(0.dp)) }
        Text(label, fontSize = 12.5.sp, color = Nocturne.Neutral500, modifier = Modifier.width(84.dp))
        Text(value, fontSize = 12.5.sp, fontFamily = Nocturne.Mono, color = color)
    }
}
