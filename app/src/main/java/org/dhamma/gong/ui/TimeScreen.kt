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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
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
import org.dhamma.gong.domain.ApplianceZone
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Time — the appliance's own clock (M5 minimal, design doc §08).
 *
 * The whole reason this screen exists is that the appliance zone is a
 * *setting* (`timezone`, default `Asia/Kolkata`) and not the device zone: a
 * travel phone left at a centre will happily report the wrong wall time
 * (AGENTS.md hard rule 5 / FABLE-REVIEW B1). So the two zones are shown side
 * by side, and only the appliance one drives the schedule.
 */

/** Zones a centre tablet plausibly runs in. `Asia/Kolkata` is the default. */
private val CommonZones = listOf(
    "Asia/Kolkata",
    "Asia/Kathmandu",
    "Asia/Colombo",
    "Asia/Bangkok",
    "Asia/Dubai",
    "Europe/London",
    "America/New_York",
    "Australia/Sydney",
    "UTC",
)

/**
 * Validate a typed IANA id through the domain resolver.
 *
 * [ApplianceZone.resolve] deliberately never throws — a corrupt settings row
 * must degrade to IST rather than kill the scheduler loop. That makes it
 * useless as a *validator* on its own, so the round trip is checked here: an
 * id that did not survive resolution is one the resolver silently replaced,
 * i.e. one we must refuse to persist.
 *
 * @return the canonical id when valid, or null.
 */
private fun validatedZoneId(raw: String): String? {
    val id = raw.trim()
    if (id.isEmpty()) return null
    return ApplianceZone.resolve(id).id.takeIf { it == id }
}

@Composable
fun TimeScreen(vm: AppViewModel) {
    val zone by vm.applianceZone.collectAsStateWithLifecycle()
    val state by vm.schedulerState.collectAsStateWithLifecycle()
    val deviceZone = remember { ZoneId.systemDefault() }

    // One instant, rendered in two zones. Bound to STARTED so a wall tablet
    // with the screen off does not recompose once a second for nobody.
    var nowInstant by remember { mutableStateOf(Instant.now()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                nowInstant = Instant.now()
                delay(1_000)
            }
        }
    }

    val timeFmt = remember { DateTimeFormatter.ofPattern("HH:mm:ss") }
    val dateFmt = remember { DateTimeFormatter.ofPattern("EEE d MMM yyyy") }
    // Lowercase 'x' so a zero offset reads "+00:00" rather than a bare "Z".
    val offsetFmt = remember { DateTimeFormatter.ofPattern("xxx") }

    val applianceNow = nowInstant.atZone(zone)
    val deviceNow = nowInstant.atZone(deviceZone)
    val zonesAgree = zone.rules.getOffset(nowInstant) == deviceZone.rules.getOffset(nowInstant)

    var typed by remember { mutableStateOf("") }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // 1280x800 holds the two zone cards side by side; narrower landscape
        // stacks them so neither clips.
        val wide = maxWidth >= 900.dp
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 32.dp, end = 32.dp, top = 26.dp, bottom = 26.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            ScreenTitle(
                "Time",
                "The schedule fires in the appliance zone — not in whatever zone " +
                    "the device thinks it is in.",
            )

            // ---------------------------------------------------- zone contrast
            if (wide) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(22.dp),
                ) {
                    ZoneCard(
                        eyebrow = "Appliance zone",
                        zoneId = zone.id,
                        time = applianceNow.format(timeFmt),
                        date = applianceNow.format(dateFmt),
                        offset = applianceNow.format(offsetFmt),
                        accent = true,
                        note = "Used for course day 0, fire times and grace.",
                        modifier = Modifier.weight(1f),
                    )
                    ZoneCard(
                        eyebrow = "Device zone",
                        zoneId = deviceZone.id,
                        time = deviceNow.format(timeFmt),
                        date = deviceNow.format(dateFmt),
                        offset = deviceNow.format(offsetFmt),
                        accent = false,
                        note = "What Android reports. The scheduler ignores it.",
                        modifier = Modifier.weight(1f),
                    )
                }
            } else {
                ZoneCard(
                    eyebrow = "Appliance zone",
                    zoneId = zone.id,
                    time = applianceNow.format(timeFmt),
                    date = applianceNow.format(dateFmt),
                    offset = applianceNow.format(offsetFmt),
                    accent = true,
                    note = "Used for course day 0, fire times and grace.",
                    modifier = Modifier.fillMaxWidth(),
                )
                ZoneCard(
                    eyebrow = "Device zone",
                    zoneId = deviceZone.id,
                    time = deviceNow.format(timeFmt),
                    date = deviceNow.format(dateFmt),
                    offset = deviceNow.format(offsetFmt),
                    accent = false,
                    note = "What Android reports. The scheduler ignores it.",
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (zonesAgree) {
                    Tag("ZONES AGREE", Nocturne.Ok)
                } else {
                    Tag("ZONES DIFFER", Nocturne.Warning)
                }
                Text(
                    if (zonesAgree) {
                        "Device and appliance currently show the same wall time."
                    } else {
                        "The device has travelled. Gongs still follow ${zone.id}."
                    },
                    fontSize = 12.5.sp,
                    color = Nocturne.Neutral500,
                )
            }

            // ---------------------------------------------------- set the zone
            SurfaceCard(Modifier.fillMaxWidth()) {
                Eyebrow("Set appliance zone")
                Spacer(Modifier.height(4.dp))
                Text(
                    "Saved immediately — there is no save button.",
                    fontSize = 12.5.sp,
                    color = Nocturne.Neutral500,
                )
                Spacer(Modifier.height(12.dp))

                for (id in CommonZones) {
                    ZoneChoiceRow(
                        id = id,
                        selected = id == zone.id,
                        time = nowInstant.atZone(ApplianceZone.resolve(id)).format(timeFmt),
                        onClick = { if (id != zone.id) vm.setSetting("timezone", id) },
                    )
                    Spacer(Modifier.height(4.dp))
                }

                Spacer(Modifier.height(8.dp))
                Hairline()
                Spacer(Modifier.height(12.dp))

                Eyebrow("Other IANA id")
                Spacer(Modifier.height(8.dp))
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
                    PrimaryButton("Use zone") {
                        val id = validatedZoneId(typed)
                        if (id == null) {
                            // Never persist a string the resolver would have
                            // silently swapped for IST.
                            vm.toast("Not a known timezone id — check spelling and case")
                        } else {
                            typed = ""
                            vm.setSetting("timezone", id, "Timezone set to $id")
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Ids are case sensitive. Unknown ids are rejected rather than " +
                        "quietly falling back to ${ApplianceZone.DEFAULT_ID}.",
                    fontSize = 12.5.sp,
                    color = Nocturne.Neutral500,
                )
            }

            // ---------------------------------------------------- clock trust
            SurfaceCard(Modifier.fillMaxWidth()) {
                Eyebrow("Clock trust")
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Dot(if (state.clockTrusted) Nocturne.Ok else Nocturne.Warning)
                    Text(
                        if (state.clockTrusted) "Trusted" else "UNTRUSTED",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (state.clockTrusted) Nocturne.Text else Nocturne.Warning,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "If the wall clock jumps a long way backwards the appliance stops " +
                        "playing automatically, because it can no longer tell which " +
                        "gongs it has already sounded. Nothing fires on its own until " +
                        "someone confirms the clock is right.",
                    fontSize = 12.5.sp,
                    color = Nocturne.Neutral400,
                )
                Spacer(Modifier.height(14.dp))
                PrimaryButton("Confirm clock") { vm.confirmClock() }
            }
        }
    }
}

// ---------------------------------------------------------------- pieces

@Composable
private fun ZoneCard(
    eyebrow: String,
    zoneId: String,
    time: String,
    date: String,
    offset: String,
    accent: Boolean,
    note: String,
    modifier: Modifier = Modifier,
) {
    SurfaceCard(modifier) {
        Eyebrow(eyebrow)
        Spacer(Modifier.height(10.dp))
        Text(
            time,
            fontSize = 44.sp,
            fontFamily = Nocturne.Mono,
            fontWeight = FontWeight.Light,
            maxLines = 1,
            color = if (accent) Nocturne.Accent100 else Nocturne.Neutral400,
        )
        Spacer(Modifier.height(6.dp))
        Text(date, fontSize = 13.5.sp, fontFamily = Nocturne.Mono, color = Nocturne.Neutral300)
        Spacer(Modifier.height(6.dp))
        Text(
            "$zoneId  ·  UTC$offset",
            fontSize = 12.5.sp,
            fontFamily = Nocturne.Mono,
            color = Nocturne.Neutral500,
        )
        Spacer(Modifier.height(8.dp))
        Text(note, fontSize = 12.5.sp, color = Nocturne.Neutral500)
    }
}

@Composable
private fun ZoneChoiceRow(
    id: String,
    selected: Boolean,
    time: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Nocturne.MIN_TOUCH_DP.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) Nocturne.Accent.copy(alpha = 0.14f) else Nocturne.SurfaceHigh)
            .border(
                1.dp,
                if (selected) Nocturne.Accent.copy(alpha = 0.55f) else Nocturne.Neutral800,
                RoundedCornerShape(8.dp),
            )
            .semantics {
                contentDescription =
                    if (selected) "$id, current appliance zone" else "Use zone $id"
            }
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.width(14.dp)) {
            if (selected) {
                Text("✓", fontSize = 13.sp, color = Nocturne.Accent200)
            }
        }
        Text(
            id,
            fontSize = 13.5.sp,
            fontFamily = Nocturne.Mono,
            color = if (selected) Nocturne.Accent100 else Nocturne.Text,
            modifier = Modifier.weight(1f),
        )
        Text(time, fontSize = 13.5.sp, fontFamily = Nocturne.Mono, color = Nocturne.Neutral500)
        if (id == ApplianceZone.DEFAULT_ID) {
            Spacer(Modifier.width(4.dp))
            Tag("DEFAULT", Nocturne.Neutral500)
        }
    }
}
