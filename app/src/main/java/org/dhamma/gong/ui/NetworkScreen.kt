package org.dhamma.gong.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
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
 * Network — SSID, address, and whether this is the centre WiFi or the tablet's
 * own hotspot (design doc §08: "Informational in v1 — nothing depends on it").
 *
 * That parenthesis is the whole brief, and the screen has to keep saying it.
 * The appliance is fully operational in airplane mode forever (§10); the only
 * thing a connection buys is the on-demand doha download. So every red state
 * here is written to reassure rather than alarm — a server who walks past a
 * tablet showing OFFLINE must not conclude the 04:00 gong is at risk, because
 * it is not.
 *
 * What the screen refuses to do is guess. Android will not name the WiFi
 * network to an app without a location permission this appliance deliberately
 * never requests, and it has offered no way to ask "am I tethering" since
 * API 28. Both blanks are labelled as blanks.
 */
@Composable
fun NetworkScreen(vm: AppViewModel) {
    val facts by vm.network.collectAsStateWithLifecycle()
    val probed by vm.networkProbed.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // A cable pulled or a hotspot started elsewhere on the device shows up on
    // the next poll. STARTED-bound: nothing polls behind a dark screen.
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                vm.refreshNetwork()
                delay(5_000)
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
                "Network",
                "Informational only. The gong and doha schedule runs with no " +
                    "network at all — this screen is here so someone on the phone " +
                    "can find the tablet.",
            )

            HeadlineRow(facts, probed)

            ConnectionCard(facts, probed)

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton("Open Wi-Fi settings") { NetworkSettings.openWifi(context) }
                PrimaryButton("Open hotspot settings") { NetworkSettings.openHotspot(context) }
            }
        }
    }
}

// ---------------------------------------------------------------- headline

@Composable
private fun HeadlineRow(facts: NetworkFacts.Facts, probed: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        when {
            !probed -> Tag("CHECKING", Nocturne.Neutral500)
            !facts.online -> Tag("OFFLINE", Nocturne.Neutral500)
            !facts.validated -> Tag("NO INTERNET", Nocturne.Warning)
            else -> Tag(modeLabel(facts.mode).uppercase(), Nocturne.Ok)
        }
        if (probed && facts.metered) Tag("METERED", Nocturne.Warning)
        Text(
            when {
                !probed -> "Reading the connection…"
                !facts.online ->
                    "No connection. The schedule is unaffected — only doha " +
                        "downloads need one."
                !facts.validated ->
                    "Connected, but Android could not reach the internet through " +
                        "it. A sign-in page on the centre WiFi does this."
                else -> "Connected. Doha downloads will work."
            },
            fontSize = 12.5.sp,
            color = if (probed && facts.online && !facts.validated) {
                Nocturne.Warning
            } else {
                Nocturne.Neutral500
            },
        )
    }
}

private fun modeLabel(mode: NetworkFacts.Mode): String = when (mode) {
    NetworkFacts.Mode.OFFLINE -> "Offline"
    NetworkFacts.Mode.WIFI -> "Wi-Fi"
    NetworkFacts.Mode.ETHERNET -> "Ethernet"
    NetworkFacts.Mode.CELLULAR -> "Mobile data"
    NetworkFacts.Mode.OTHER -> "Other"
}

// ---------------------------------------------------------------- connection

@Composable
private fun ConnectionCard(facts: NetworkFacts.Facts, probed: Boolean) {
    SurfaceCard(Modifier.fillMaxWidth()) {
        Eyebrow("This connection")
        Spacer(Modifier.height(12.dp))

        Row(
            Modifier.fillMaxWidth().heightIn(min = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.clearAndSetSemantics {}) {
                Dot(
                    when {
                        !probed -> Nocturne.Neutral600
                        !facts.online -> Nocturne.Neutral600
                        facts.validated -> Nocturne.Ok
                        else -> Nocturne.Warning
                    },
                )
            }
            Text(
                if (probed) modeLabel(facts.mode) else "checking",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = Nocturne.Mono,
                color = Nocturne.Text,
            )
        }

        Spacer(Modifier.height(12.dp))
        Hairline()
        Spacer(Modifier.height(12.dp))

        InfoRow(
            "Network",
            when {
                facts.ssid != null -> facts.ssid
                facts.ssidWithheld -> "name withheld"
                facts.mode == NetworkFacts.Mode.ETHERNET -> "wired"
                else -> "—"
            },
            if (facts.ssidWithheld) Nocturne.Neutral400 else Nocturne.Text,
        )
        Spacer(Modifier.height(8.dp))
        InfoRow("Address", facts.ip ?: "—")
        Spacer(Modifier.height(8.dp))
        InfoRow(
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
        Spacer(Modifier.height(8.dp))
        InfoRow(
            "Data",
            if (facts.metered) "metered" else "unmetered",
            if (facts.metered) Nocturne.Warning else Nocturne.Text,
        )

        if (facts.ssidWithheld) {
            Spacer(Modifier.height(12.dp))
            Text(
                "Android only tells an app the network's name if the app holds a " +
                    "location permission. This one does not ask for one — a " +
                    "location grant to caption an informational screen is a bad " +
                    "trade. Open Wi-Fi settings below to see the name.",
                fontSize = 12.5.sp,
                color = Nocturne.Neutral500,
            )
        }
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
