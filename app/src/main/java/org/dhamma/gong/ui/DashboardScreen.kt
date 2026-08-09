package org.dhamma.gong.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
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
import kotlinx.coroutines.launch
import org.dhamma.gong.domain.Occurrence
import org.dhamma.gong.domain.PlayResult
import org.dhamma.gong.service.AppliancePermissions
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * The default and permanent view. It answers, from ~2 m away: what day is it,
 * what fires next, is the appliance healthy, and can I test it
 * (design handoff §1).
 */
@Composable
fun DashboardScreen(vm: AppViewModel) {
    val state by vm.schedulerState.collectAsStateWithLifecycle()
    val player by vm.playerStatus.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val slots by vm.mappedDohaSlots.collectAsStateWithLifecycle()
    val overlapping by vm.overlappingCourses.collectAsStateWithLifecycle()
    val permissions by vm.permissionStatus.collectAsStateWithLifecycle()

    // The clock ticks every second and the countdown is recomputed from
    // seconds — never by borrowing minutes by hand (design handoff). It shows
    // the *appliance's* wall time, which may differ from the device TZ.
    //
    // The tick is bound to STARTED: a wall tablet with the screen off must not
    // recompose once a second for nobody.
    val zone by vm.applianceZone.collectAsStateWithLifecycle()
    var now by remember(zone) { mutableStateOf(ZonedDateTime.now(zone)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(zone, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                now = ZonedDateTime.now(zone)
                delay(1_000)
            }
        }
    }

    // The design targets 1280×800 landscape, where the hero and its 394 dp
    // asides sit side by side. Narrower landscape (and any phone) cannot hold
    // that Row: the fixed asides would push the test panel — and with it
    // "Stop" — off the right edge, which the vertical scroll cannot rescue.
    // Below ~900 dp everything stacks full width instead.
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 900.dp
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 32.dp, end = 32.dp, top = 26.dp, bottom = 26.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            if (wide) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(32.dp),
                ) {
                    NextGongHero(state.next, now, settings, player.route, Modifier.weight(1f))
                    Column(
                        Modifier.width(394.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        CourseCard(vm, state, zone, settings, overlapping)
                        HealthCard(vm, state, player, slots.size, permissions)
                    }
                }

                Row(
                    Modifier.fillMaxWidth().heightIn(min = 260.dp),
                    horizontalArrangement = Arrangement.spacedBy(32.dp),
                ) {
                    NextEvents(state.upcoming, now, Modifier.weight(1f))
                    TestPanel(vm, player, Modifier.width(394.dp))
                }
            } else {
                NextGongHero(state.next, now, settings, player.route, Modifier.fillMaxWidth())
                Column(
                    Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CourseCard(vm, state, zone, settings, overlapping)
                    HealthCard(vm, state, player, slots.size, permissions)
                }
                NextEvents(state.upcoming, now, Modifier.fillMaxWidth())
                TestPanel(vm, player, Modifier.fillMaxWidth())
            }
        }
    }
}

// ---------------------------------------------------------------- hero

@Composable
private fun NextGongHero(
    next: Occurrence?,
    now: ZonedDateTime,
    settings: Map<String, String>,
    route: String,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier) {
    // 98 sp is the design size at 1280x800. Narrower panes step down rather
    // than wrapping "21:00" onto two lines.
    val heroSp = when {
        maxWidth >= 560.dp -> 98
        maxWidth >= 420.dp -> 76
        else -> 56
    }
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Eyebrow("Next gong")
            if (next != null) {
                Tag(
                    when (next.kind) {
                        Occurrence.Kind.GONG -> "GONG"
                        Occurrence.Kind.DOHA -> "DOHA"
                    },
                    Nocturne.Accent,
                )
            }
        }
        Text(
            next?.let { "%02d:%02d".format(it.fireAt.hour, it.fireAt.minute) } ?: "--:--",
            fontSize = heroSp.sp,
            lineHeight = (heroSp * 0.92f).sp,
            letterSpacing = (-heroSp * 0.03f).sp,
            fontFamily = Nocturne.Mono,
            fontWeight = FontWeight.Light,
            color = Nocturne.Text,
            maxLines = 1,
        )
        Spacer(Modifier.height(14.dp))
        Box(
            // 214 dp at the design width; never wider than the pane it sits in.
            Modifier
                .widthIn(max = 214.dp)
                .fillMaxWidth(0.6f)
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Nocturne.Accent),
        )
        Spacer(Modifier.height(11.dp))
        Text(
            next?.let { countdown(now, it.fireAt) } ?: "nothing scheduled",
            fontSize = 19.sp,
            fontFamily = Nocturne.Mono,
            color = Nocturne.Accent300,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            detailLine(next, settings, route),
            fontSize = 13.5.sp,
            color = Nocturne.Neutral500,
        )
    }
    }
}

/**
 * "in 2h 14m". Recomputed from the raw second difference each tick, which is
 * what stops the minute field from drifting.
 */
private fun countdown(now: ZonedDateTime, at: ZonedDateTime): String {
    val secs = Duration.between(now.toInstant(), at.toInstant()).seconds
    if (secs <= 0) return "now"
    val h = secs / 3600
    val m = (secs % 3600) / 60
    val s = secs % 60
    return when {
        h > 0 -> "in ${h}h ${m}m"
        m > 0 -> "in ${m}m ${s}s"
        else -> "in ${s}s"
    }
}

private fun detailLine(
    next: Occurrence?,
    settings: Map<String, String>,
    route: String,
): String {
    if (next == null) return "—"
    if (next.kind == Occurrence.Kind.DOHA) return "doha · $route"
    val gap = next.gapSeconds?.let { "gap ${it}s" }
        ?: "gap ${settings["gong_gap_seconds"]}s (default)"
    val track = next.track?.let { it } ?: "${settings["gong_track"]} (default)"
    return "${next.repeats} strikes · $gap · $track · $route"
}

// ---------------------------------------------------------------- cards

@Composable
private fun CourseCard(
    vm: AppViewModel,
    state: org.dhamma.gong.schedule.SchedulerEngine.State,
    zone: ZoneId,
    settings: Map<String, String>,
    overlapping: Boolean,
) {
    val ctx = state.course
    SurfaceCard(padding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                ctx?.let { "${it.typeName} course —" } ?: "No course",
                fontSize = 17.sp,
                color = Nocturne.Text,
            )
            if (ctx != null) {
                Text(
                    "Day ${ctx.day}",
                    fontSize = 27.sp,
                    fontFamily = Nocturne.Mono,
                    fontWeight = FontWeight.Medium,
                    color = Nocturne.Accent200,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            // The appliance zone belongs on this line whether or not a course
            // is running — staff need it to read "today" before adding one.
            ctx?.let { "zero day ${it.startDate} · ${zone.id}" }
                ?: "add a course to start the schedule · ${zone.id}",
            fontSize = 12.5.sp,
            color = Nocturne.Neutral400,
        )
        if (overlapping) {
            Spacer(Modifier.height(6.dp))
            Tag("TWO COURSES CLAIM TODAY", Nocturne.Warning)
        }

        if (ctx != null) {
            Spacer(Modifier.height(12.dp))
            DayProgress(day = ctx.day, total = ctx.totalDays)
        }

        Spacer(Modifier.height(12.dp))
        Hairline()
        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Toggle("Master", settings["enabled"] == "1") { vm.toggle("enabled") }
            Toggle("Gong", settings["gong_enabled"] == "1") { vm.toggle("gong_enabled") }
            Toggle("Doha", settings["doha_enabled"] == "1") { vm.toggle("doha_enabled") }
            // Live once a Shelly address is set; dimmed and inert until then,
            // because a relay with no host cannot switch anything (relay design,
            // "Error handling": host unset → relay logic inert).
            val relayConfigured = settings["relay_host"].orEmpty().isNotBlank()
            if (relayConfigured) {
                Toggle("Relay", settings["relay_enabled"] == "1") { vm.toggle("relay_enabled") }
            } else {
                Box(Modifier.alpha(0.5f)) { Toggle("Relay", false, enabled = false) {} }
            }
        }
    }
}

@Composable
private fun DayProgress(day: Int, total: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.fillMaxWidth()) {
        for (i in 0..total) {
            Box(
                Modifier
                    .weight(1f)
                    .height(5.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(
                        when {
                            i < day -> Nocturne.Accent700
                            i == day -> Nocturne.Accent
                            else -> Nocturne.Neutral800
                        },
                    ),
            )
        }
    }
}

@Composable
private fun Toggle(label: String, checked: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    // The painted box stays 20 dp as designed; only the hit slop grows to the
    // 44 dp minimum, so a wall tablet tap does not miss.
    Row(
        Modifier
            .heightIn(min = Nocturne.MIN_TOUCH_DP.dp)
            .clip(RoundedCornerShape(6.dp))
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Checkbox,
                onValueChange = { onClick() },
            )
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier
                .size(20.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(if (checked) Nocturne.Accent.copy(alpha = 0.26f) else Color.Transparent)
                .border(
                    1.dp,
                    if (checked) Nocturne.Accent else Nocturne.Neutral700,
                    RoundedCornerShape(4.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Text(
                    "✓",
                    fontSize = 12.sp,
                    color = Nocturne.Accent100,
                    modifier = Modifier.clearAndSetSemantics {},
                )
            }
        }
        Text(label, fontSize = 12.5.sp, color = Nocturne.Neutral300)
    }
}

@Composable
private fun HealthCard(
    vm: AppViewModel,
    state: org.dhamma.gong.schedule.SchedulerEngine.State,
    player: org.dhamma.gong.player.PlayerEngine.Status,
    mappedSlots: Int,
    permissions: AppliancePermissions.Status,
) {
    val context = LocalContext.current
    // Prefer live scheduler signal for exact alarms; fall back to Activity status.
    val exactOk = state.exactAlarmsAllowed && permissions.exactAlarmsAllowed
    SurfaceCard {
        HealthRow(
            "Scheduler",
            if (state.running) "running" else "stopped",
            if (state.running) Nocturne.Ok else Nocturne.Error,
        )
        Spacer(Modifier.height(7.dp))
        HealthRow("Audio route", player.route.ifBlank { "—" }, Nocturne.Ok)
        Spacer(Modifier.height(7.dp))
        HealthRow(
            label = "Clock",
            value = if (state.clockTrusted) {
                "trusted"
            } else {
                "UNTRUSTED — tap to confirm"
            },
            dot = if (state.clockTrusted) Nocturne.Ok else Nocturne.Warning,
            onClick = if (!state.clockTrusted) {
                { vm.confirmClock() }
            } else {
                null
            },
        )
        if (!exactOk) {
            Spacer(Modifier.height(7.dp))
            HealthRow(
                label = "Exact alarms",
                value = "denied — tap to allow",
                dot = Nocturne.Warning,
                onClick = { AppliancePermissions.openExactAlarmSettings(context) },
            )
        }
        if (!permissions.batteryUnrestricted) {
            Spacer(Modifier.height(7.dp))
            HealthRow(
                label = "Battery",
                value = "restricted — tap to free",
                dot = Nocturne.Warning,
                onClick = { AppliancePermissions.openBatterySettings(context) },
            )
        }
        if (!permissions.notificationsGranted) {
            Spacer(Modifier.height(7.dp))
            HealthRow(
                label = "Notify",
                value = "denied — tap to open",
                dot = Nocturne.Warning,
                onClick = { AppliancePermissions.openAppNotificationSettings(context) },
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (mappedSlots == 0) Tag("GONGS ONLY", Nocturne.Warning)
            Text(
                "$mappedSlots / 11 doha slots mapped",
                fontSize = 12.5.sp,
                color = Nocturne.Neutral500,
            )
        }
    }
}

@Composable
private fun HealthRow(
    label: String,
    value: String,
    dot: Color,
    onClick: (() -> Unit)? = null,
) {
    // Tappable rows are the B6/B14 permission grants — they must clear the
    // 44 dp minimum. Inert rows stay compact so the card keeps its rhythm.
    val rowMod = if (onClick != null) {
        Modifier
            .fillMaxWidth()
            .heightIn(min = Nocturne.MIN_TOUCH_DP.dp)
            .clip(RoundedCornerShape(6.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "$label: $value" }
            .padding(vertical = 2.dp)
    } else {
        Modifier
            .fillMaxWidth()
            .heightIn(min = 24.dp)
    }
    Row(
        rowMod,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.clearAndSetSemantics {}) { Dot(dot) }
        Text(label, fontSize = 12.5.sp, color = Nocturne.Neutral500, modifier = Modifier.width(100.dp))
        Text(value, fontSize = 12.5.sp, fontFamily = Nocturne.Mono, color = Nocturne.Text)
    }
}

// ---------------------------------------------------------------- events

@Composable
private fun NextEvents(
    upcoming: List<Occurrence>,
    now: ZonedDateTime,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Eyebrow("Next events")
        Spacer(Modifier.height(10.dp))
        if (upcoming.isEmpty()) {
            Text(
                "nothing scheduled in the next 2 days",
                fontSize = 12.5.sp,
                color = Nocturne.Neutral500,
            )
            return@Column
        }
        // The accent paint marks the *one* next occurrence. Both columns start
        // at index 0, so identity — not position — decides which row gets it.
        val next = upcoming.firstOrNull { it.fireAt.toInstant() > now.toInstant() }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            val left = upcoming.take(6)
            val right = upcoming.drop(6).take(6)
            EventColumn(left, now, next, Modifier.weight(1f))
            EventColumn(right, now, next, Modifier.weight(1f))
        }
    }
}

@Composable
private fun EventColumn(
    items: List<Occurrence>,
    now: ZonedDateTime,
    next: Occurrence?,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { occ ->
            val past = occ.fireAt.toInstant() <= now.toInstant()
            Row(
                Modifier.fillMaxWidth().alpha(if (past) 0.38f else 1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    if (occ.kind == Occurrence.Kind.GONG) "🔔" else "♪",
                    fontSize = 13.sp,
                    modifier = Modifier.clearAndSetSemantics {},
                )
                Text(
                    "%02d:%02d".format(occ.fireAt.hour, occ.fireAt.minute),
                    fontSize = 15.sp,
                    fontFamily = Nocturne.Mono,
                    color = if (occ == next) Nocturne.Accent300 else Nocturne.Text,
                )
                Text(
                    occ.localDate.toString(),
                    fontSize = 11.sp,
                    fontFamily = Nocturne.Mono,
                    color = Nocturne.Neutral600,
                )
                Text(
                    occ.ctx?.let { "Day ${it.day}" } ?: "no course",
                    fontSize = 12.5.sp,
                    color = Nocturne.Neutral500,
                    modifier = Modifier.weight(1f),
                )
                if (occ.kind == Occurrence.Kind.GONG) {
                    Text(
                        "×${occ.repeats}",
                        fontSize = 12.5.sp,
                        fontFamily = Nocturne.Mono,
                        color = Nocturne.Neutral400,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------- test panel

@Composable
private fun TestPanel(
    vm: AppViewModel,
    player: org.dhamma.gong.player.PlayerEngine.Status,
    modifier: Modifier = Modifier,
) {
    val rings = remember { mutableStateListOf<Int>() }
    LaunchedEffect(Unit) {
        vm.strikes.collect { n ->
            rings.add(n)
            if (rings.size > 8) rings.removeAt(0)
        }
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            // Re-entry guard: a second tap while a burst is ringing would
            // preempt it and log a `stopped`. The button stays visually
            // enabled — only the action is inert. Stop remains the way out.
            BellButton(rings.lastOrNull(), player.playing) { if (!player.playing) vm.testGong() }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { if (!player.playing) vm.testGong() },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Nocturne.Accent,
                        contentColor = Nocturne.Bg,
                    ),
                ) {
                    Text(if (player.playing) "Ringing…" else "Test gong", fontSize = 15.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SecondaryButton("Test doha", Modifier.weight(1f)) { vm.testDoha() }
                    SecondaryButton(
                        "■ Stop",
                        Modifier.weight(1f),
                        Nocturne.Error,
                        contentDescription = "Stop",
                    ) { vm.stop() }
                }
            }
        }
        Text(
            buildString {
                append(
                    if (player.lastFile.isBlank()) {
                        "no plays yet"
                    } else {
                        "last: ${player.lastFile} · ${player.lastResult.ifBlank { "—" }}"
                    },
                )
                if (player.ofStrikes > 0) append("  ·  strike ${player.strike}/${player.ofStrikes}")
            },
            fontSize = 11.sp,
            fontFamily = Nocturne.Mono,
            color = if (player.lastResult == PlayResult.ERROR) Nocturne.Error else Nocturne.Neutral500,
        )
    }
}

@Composable
private fun SecondaryButton(
    label: String,
    modifier: Modifier = Modifier,
    color: Color = Nocturne.Neutral300,
    contentDescription: String? = null,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .height(Nocturne.MIN_TOUCH_DP.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, Nocturne.Neutral700, RoundedCornerShape(8.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { if (contentDescription != null) this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 13.5.sp, color = color)
    }
}

/**
 * 78 dp bell that emits one expanding ring per strike:
 * scale 1 → 2.3, opacity 0.9 → 0, 1 s ease-out (design handoff §1).
 */
@Composable
private fun BellButton(latestStrike: Int?, playing: Boolean, onClick: () -> Unit) {
    val scale = remember { Animatable(1f) }
    val alpha = remember { Animatable(0f) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    LaunchedEffect(latestStrike) {
        if (latestStrike == null) return@LaunchedEffect
        scale.snapTo(1f)
        alpha.snapTo(0.9f)
        scope.launch { scale.animateTo(2.3f, tween(1000, easing = LinearOutSlowInEasing)) }
        alpha.animateTo(0f, tween(1000, easing = LinearOutSlowInEasing))
    }

    Box(Modifier.size(78.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(78.dp)
                .scale(scale.value)
                .alpha(alpha.value)
                .clip(CircleShape)
                .border(2.dp, Nocturne.Accent, CircleShape),
        )
        Box(
            Modifier
                .size(78.dp)
                .clip(CircleShape)
                .background(
                    if (playing) Nocturne.Accent.copy(alpha = 0.26f) else Nocturne.Surface,
                )
                .border(1.dp, Nocturne.Accent.copy(alpha = 0.5f), CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text("🔔", fontSize = 30.sp)
        }
    }
}
