package org.dhamma.gong.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.text.font.FontWeight
import org.dhamma.gong.domain.ApplianceZone
import java.time.format.DateTimeFormatter

/**
 * Time — one clock, and the trust state that can silence it.
 *
 * The screen used to make staff choose a timezone from a list, defaulting to
 * IST. That was one more thing to get wrong on install day, and getting it
 * wrong moves every gong by hours. A tablet bought and installed at the centre
 * already knows where it is, so the app follows the device and says so.
 *
 * The pin is still reachable for the one case that needs it — a donated phone
 * that insists it is in another country — but it is not the first thing anyone
 * sees, and there is nothing to configure on a normal install.
 */
@Composable
fun TimeScreen(vm: AppViewModel) {
    val zone by vm.applianceZone.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val state by vm.schedulerState.collectAsStateWithLifecycle()
    val now = rememberNow()

    val pinned = ApplianceZone.isPinned(settings["timezone"])
    val timeFmt = remember { DateTimeFormatter.ofPattern("HH:mm:ss") }
    val dateFmt = remember { DateTimeFormatter.ofPattern("EEE d MMM yyyy") }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 32.dp, end = 32.dp, top = 26.dp, bottom = 26.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        ScreenTitle("Time", "The clock the schedule fires on.")

        SurfaceCard(Modifier.fillMaxWidth()) {
            Text(
                now.withZoneSameInstant(zone).format(timeFmt),
                fontSize = 56.sp,
                fontFamily = Nocturne.Mono,
                fontWeight = FontWeight.Light,
                maxLines = 1,
                color = Nocturne.Accent100,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                now.withZoneSameInstant(zone).format(dateFmt),
                fontSize = 15.sp,
                fontFamily = Nocturne.Mono,
                color = Nocturne.Neutral300,
            )
            Spacer(Modifier.height(10.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Tag(zone.id.uppercase(), Nocturne.Ok)
                Text(
                    if (pinned) "Pinned in settings." else "Taken from the tablet.",
                    fontSize = 12.5.sp,
                    color = Nocturne.Neutral500,
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "Set the tablet's date, time and timezone in Android settings and " +
                    "the schedule follows. Nothing to configure here.",
                fontSize = 12.5.sp,
                color = Nocturne.Neutral500,
            )
        }

        // -------------------------------------------------------- clock trust
        SurfaceCard(Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Dot(if (state.clockTrusted) Nocturne.Ok else Nocturne.Warning)
                Text(
                    if (state.clockTrusted) "Clock trusted" else "CLOCK UNTRUSTED",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (state.clockTrusted) Nocturne.Text else Nocturne.Warning,
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "If the clock jumps a long way backwards the appliance stops playing " +
                    "automatically, because it can no longer tell which gongs it has " +
                    "already sounded. Check the time above, then confirm.",
                fontSize = 12.5.sp,
                color = Nocturne.Neutral400,
            )
            Spacer(Modifier.height(14.dp))
            PrimaryButton("Confirm clock") { vm.confirmClock() }
        }

        // ------------------------------------------------------------- pin
        PinZoneCard(vm, pinned, currentId = zone.id)
    }
}

/**
 * The escape hatch, deliberately last and deliberately quiet.
 *
 * Only a device that reports the wrong zone needs this. Putting it first would
 * invite people to set it "just in case", which is exactly how a centre ends up
 * gonging on another continent's clock.
 */
@Composable
private fun PinZoneCard(vm: AppViewModel, pinned: Boolean, currentId: String) {
    var typed by remember(pinned) { mutableStateOf("") }

    SurfaceCard(Modifier.fillMaxWidth()) {
        Eyebrow("Override the zone")
        Spacer(Modifier.height(4.dp))
        Text(
            "Only needed if the tablet reports the wrong timezone and cannot be " +
                "corrected in Android settings.",
            fontSize = 12.5.sp,
            color = Nocturne.Neutral500,
        )
        Spacer(Modifier.height(14.dp))

        if (pinned) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Tag("PINNED", Nocturne.Warning)
                Text(
                    currentId,
                    fontSize = 13.5.sp,
                    fontFamily = Nocturne.Mono,
                    color = Nocturne.Text,
                    modifier = Modifier.weight(1f),
                )
                OutlineButton("Follow the tablet") {
                    vm.setSetting("timezone", ApplianceZone.FOLLOW_DEVICE, "Following the tablet's timezone")
                }
            }
        } else {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Field(
                    value = typed,
                    onValueChange = { typed = it },
                    placeholder = "Region/City, e.g. Asia/Kolkata",
                    modifier = Modifier.weight(1f),
                )
                PrimaryButton("Pin zone") {
                    val id = typed.trim()
                    // Never persist a string the resolver would silently ignore
                    // — that would look pinned and behave unpinned.
                    val ok = id.isNotEmpty() &&
                        runCatching { java.time.ZoneId.of(id) }.isSuccess
                    if (!ok) {
                        vm.toast("Not a known timezone id — check spelling and case")
                    } else {
                        typed = ""
                        vm.setSetting("timezone", id, "Timezone pinned to $id")
                    }
                }
            }
        }
    }
}
