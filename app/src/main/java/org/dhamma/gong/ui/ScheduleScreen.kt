package org.dhamma.gong.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.dhamma.gong.data.ScheduleEventEntity
import java.time.LocalTime

/** Narrowest a day column may ever be; it grows to fill the pane (handoff `minmax(46px, 1fr)`). */
private val MIN_COL_W = 46.dp

/** The frozen wall-clock gutter on the left of the grid. */
private val GUTTER_W = 60.dp

private val ROW_H = 44.dp

/** Tolerant parse: the seed and the Pi daemon both emit "HH:mm" and "HH:mm:ss". */
private fun parseTime(raw: String): LocalTime? = runCatching { LocalTime.parse(raw) }.getOrNull()

private fun hhmm(t: LocalTime): String = "%02d:%02d".format(t.hour, t.minute)

private fun dayLabel(typeId: Int?, day: Int?): String = when {
    typeId == null -> "No course"
    day == null -> "Default pattern"
    else -> "Day $day"
}

/**
 * The day-column grid: days across, wall-clock times down, the whole
 * course-type matrix at once — so a day-3 edit visibly reads as day 3
 * (design handoff §3, design doc §13).
 *
 * The **DEF** column is `day_no IS NULL`, the mid-course default pattern used
 * for any day with no rows of its own. For "No course" the grid collapses to a
 * single **N/C** column (`course_type_id IS NULL`).
 *
 * Inheritance is **`ScheduleMaterializer.eventsFor`'s law**: a day with at
 * least one explicit row uses *only* its explicit rows; a day with none fires
 * the whole DEF pattern. The grid therefore *ghosts* the DEF rows into every
 * inheriting day (alpha 0.38, no accent) and makes the first override a
 * two-tap — creating one row silently replaces that day's entire inherited
 * pattern. Nothing here materialises DEF rows into a day; the ghosts are paint
 * only.
 */
@Composable
fun ScheduleScreen(vm: AppViewModel) {
    val types by vm.courseTypes.collectAsState()
    val allEvents by vm.events.collectAsState()
    val settings by vm.settings.collectAsState()

    // null = the "No course" schedule.
    var typeId by remember { mutableStateOf<Int?>(1) }
    var selected by remember { mutableStateOf<CellKey?>(null) }

    // First tap on an inheriting day only arms the override; the second writes.
    var armedOverrideDay by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(armedOverrideDay) {
        if (armedOverrideDay != null) {
            delay(4_000)
            armedOverrideDay = null
        }
    }

    val type = types.firstOrNull { it.id == typeId }
    val events = remember(allEvents, typeId) { allEvents.filter { it.courseTypeId == typeId } }

    // Columns: days 0..total, then DEF. No course collapses to one column.
    val dayColumns: List<Int?> = if (typeId == null) {
        listOf(null)
    } else {
        (0..(type?.totalDays ?: 0)).map { it as Int? } + listOf<Int?>(null)
    }

    // Everything below keys on a parsed LocalTime, never on the raw string —
    // "04:00" and "04:00:00" are the same slot and must collide, not duplicate.
    val byCell: Map<Pair<Int?, LocalTime>, ScheduleEventEntity> = remember(events) {
        buildMap { for (e in events) parseTime(e.timeLocal)?.let { put(e.dayNo to it, e) } }
    }
    val defByTime: Map<LocalTime, ScheduleEventEntity> = remember(events) {
        buildMap {
            for (e in events) {
                if (e.dayNo == null) parseTime(e.timeLocal)?.let { put(it, e) }
            }
        }
    }
    val daysWithExplicit: Set<Int> = remember(events) {
        events.mapNotNull { it.dayNo }.toSet()
    }
    val times = remember(events) {
        events.mapNotNull { parseTime(it.timeLocal) }.distinct().sorted()
    }

    /** True when this day column currently paints DEF rows it does not own. */
    fun inheritsDef(day: Int?): Boolean =
        typeId != null && day != null && day !in daysWithExplicit && defByTime.isNotEmpty()

    Row(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ScreenTitle("Schedule")
                Spacer(Modifier.weight(1f))
                if (types.isEmpty() && typeId != null) {
                    // Room has not emitted course types yet — do not flash "No course".
                    Box(Modifier.width(220.dp).height(38.dp), contentAlignment = Alignment.CenterStart) {
                        Text("Loading…", fontSize = 13.5.sp, color = Nocturne.Neutral600)
                    }
                } else {
                    TypePicker(
                        types = types,
                        selected = type,
                        modifier = Modifier.width(220.dp),
                        includeNoCourse = true,
                        onNoCourse = {
                            typeId = null
                            selected = null
                            armedOverrideDay = null
                        },
                    ) {
                        typeId = it.id
                        selected = null
                        armedOverrideDay = null
                    }
                }
            }

            Grid(
                times = times,
                dayColumns = dayColumns,
                byCell = byCell,
                defByTime = defByTime,
                daysWithExplicit = daysWithExplicit,
                typeId = typeId,
                selected = selected,
                armedOverrideDay = armedOverrideDay,
                onSelect = { key ->
                    selected = key
                    val existing = byCell[key.dayNo to key.time]
                    when {
                        existing != null -> armedOverrideDay = null

                        // The destructive case: this day fires the whole DEF
                        // pattern, and one insert replaces all of it. Arm first.
                        inheritsDef(key.dayNo) && armedOverrideDay != key.dayNo -> {
                            armedOverrideDay = key.dayNo
                            vm.toast(
                                "Day ${key.dayNo} inherits the DEF pattern — " +
                                    "tap again to override it",
                            )
                        }

                        else -> {
                            armedOverrideDay = null
                            // Tapping an empty cell creates a default event
                            // (×6, inherit gap, inherit track) — design handoff.
                            vm.addEvent(
                                ScheduleEventEntity(
                                    courseTypeId = typeId,
                                    dayNo = key.dayNo,
                                    timeLocal = key.time.toString(),
                                    repeats = 6,
                                    gapSeconds = null,
                                    track = null,
                                ),
                            )
                        }
                    }
                },
                modifier = Modifier.weight(1f),
            )

            AddRow(
                typeId = typeId,
                selectedDay = selected?.dayNo,
                events = events,
                onAdd = vm::addEvent,
                vm = vm,
            )
        }

        Inspector(
            event = selected?.let { byCell[it.dayNo to it.time] },
            typeName = if (typeId == null) "No course" else type?.name ?: "",
            settings = settings,
            emptyHint = selected?.let { key ->
                if (byCell[key.dayNo to key.time] == null &&
                    inheritsDef(key.dayNo) &&
                    defByTime[key.time] != null
                ) {
                    "Day ${key.dayNo} has no schedule of its own, so it fires the whole " +
                        "DEF pattern — including this ${hhmm(key.time)} gong. Creating one " +
                        "event here replaces every inherited row for this day."
                } else {
                    null
                }
            },
            onChange = vm::updateEvent,
            onDelete = {
                vm.deleteEvent(it)
                selected = null
            },
        )
    }
}

data class CellKey(val dayNo: Int?, val time: LocalTime)

@Composable
private fun Grid(
    times: List<LocalTime>,
    dayColumns: List<Int?>,
    byCell: Map<Pair<Int?, LocalTime>, ScheduleEventEntity>,
    defByTime: Map<LocalTime, ScheduleEventEntity>,
    daysWithExplicit: Set<Int>,
    typeId: Int?,
    selected: CellKey?,
    armedOverrideDay: Int?,
    onSelect: (CellKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    // One horizontal state shared by the header and the body; the time gutter
    // sits *outside* it so wall-clock times never scroll away (20/30/45 Day
    // types are far wider than 1280 dp).
    val hScroll = rememberScrollState()
    val vScroll = rememberScrollState()

    BoxWithConstraints(modifier.fillMaxSize()) {
        val avail = (maxWidth - GUTTER_W).coerceAtLeast(0.dp)
        val colW: Dp = if (dayColumns.isEmpty()) {
            MIN_COL_W
        } else {
            maxOf(MIN_COL_W, avail / dayColumns.size)
        }

        Column(Modifier.fillMaxSize()) {
            Row(Modifier.height(36.dp)) {
                Box(Modifier.width(GUTTER_W).fillMaxHeight())
                Row(Modifier.horizontalScroll(hScroll)) {
                    for (day in dayColumns) {
                        Box(
                            Modifier
                                .width(colW)
                                .fillMaxHeight()
                                .background(
                                    if (day == null) {
                                        Nocturne.Text.copy(alpha = 0.05f)
                                    } else {
                                        Color.Transparent
                                    },
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                when {
                                    typeId == null -> "N/C"
                                    day == null -> "DEF"
                                    else -> day.toString()
                                },
                                fontSize = 12.sp,
                                fontFamily = Nocturne.Mono,
                                color = Nocturne.Neutral400,
                            )
                        }
                    }
                }
            }
            Hairline()

            if (times.isEmpty()) {
                Text(
                    "No events for this course type yet — add one below.",
                    fontSize = 13.5.sp,
                    color = Nocturne.Neutral500,
                    modifier = Modifier.padding(top = 16.dp),
                )
            } else {
                // weight() bounds the height BEFORE verticalScroll; every row is a
                // fixed 44 dp and nothing under the scroll uses weight().
                Row(Modifier.weight(1f).verticalScroll(vScroll)) {
                    Column(Modifier.width(GUTTER_W)) {
                        for (t in times) {
                            Box(
                                Modifier.width(GUTTER_W).height(ROW_H),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                Text(
                                    hhmm(t),
                                    fontSize = 12.5.sp,
                                    fontFamily = Nocturne.Mono,
                                    color = Nocturne.Neutral400,
                                )
                            }
                        }
                    }
                    Column(Modifier.horizontalScroll(hScroll)) {
                        for (t in times) {
                            Row(Modifier.height(ROW_H)) {
                                for (day in dayColumns) {
                                    val row = byCell[day to t]
                                    val inherits = typeId != null && day != null &&
                                        day !in daysWithExplicit && defByTime.isNotEmpty()
                                    val ghost = if (row == null && inherits) defByTime[t] else null
                                    val isSelected = selected != null &&
                                        selected.dayNo == day && selected.time == t
                                    val where = dayLabel(typeId, day)
                                    val strikes = row?.repeats ?: ghost?.repeats
                                    Cell(
                                        label = strikes?.let { "×$it" } ?: "·",
                                        filled = row != null,
                                        ghost = ghost != null,
                                        selected = isSelected,
                                        width = colW,
                                        contentDescription = when {
                                            row != null ->
                                                "$where, ${hhmm(t)}, ${row.repeats} strikes"
                                            ghost != null ->
                                                "$where, ${hhmm(t)}, ${ghost.repeats} strikes " +
                                                    "inherited from the default pattern"
                                            else -> "$where, ${hhmm(t)}, no event"
                                        },
                                        onClickLabel = when {
                                            row != null -> "Edit event"
                                            ghost != null && armedOverrideDay == day ->
                                                "Confirm override of the inherited pattern"
                                            inherits -> "Override the inherited pattern"
                                            else -> "Add event"
                                        },
                                        onClick = { onSelect(CellKey(day, t)) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Cell(
    label: String,
    filled: Boolean,
    ghost: Boolean,
    selected: Boolean,
    width: Dp,
    contentDescription: String,
    onClickLabel: String,
    onClick: () -> Unit,
) {
    val desc = contentDescription
    Box(
        Modifier
            .width(width)
            .height(ROW_H)
            .background(if (filled) Nocturne.Accent.copy(alpha = 0.14f) else Color.Transparent)
            .border(1.dp, Nocturne.Hairline)
            .then(
                if (selected) Modifier.border(2.dp, Nocturne.Accent) else Modifier,
            )
            .clickable(onClickLabel = onClickLabel, onClick = onClick)
            .semantics { this.contentDescription = desc },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontSize = 12.5.sp,
            fontFamily = Nocturne.Mono,
            color = when {
                filled -> Nocturne.Accent200
                // Inherited DEF rows: visible, but plainly not this day's own.
                ghost -> Nocturne.Text.copy(alpha = 0.38f)
                else -> Nocturne.Neutral700
            },
        )
    }
}

@Composable
private fun AddRow(
    typeId: Int?,
    selectedDay: Int?,
    events: List<ScheduleEventEntity>,
    onAdd: (ScheduleEventEntity) -> Unit,
    vm: AppViewModel,
) {
    var time by remember { mutableStateOf("") }
    var repeats by remember { mutableStateOf("6") }
    var gap by remember { mutableStateOf("") }

    val defTarget = typeId != null && selectedDay == null

    SurfaceCard(padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Field(time, { time = it }, "HH:MM", Modifier.width(110.dp))
            Field(repeats, { repeats = it }, "repeats", Modifier.width(90.dp))
            Field(gap, { gap = it }, "gap s", Modifier.width(90.dp))
            PrimaryButton("Add") {
                val t = parseTime(time.trim())
                if (t == null) {
                    vm.toast("Pick a time first")
                    return@PrimaryButton
                }
                // Insert is REPLACE on the unique (type, day, time) index: a clash
                // would wipe the existing row's strikes/gap/track and rotate its
                // primary key, which the fired guard is keyed on. Refuse instead.
                val clash = events.any {
                    it.dayNo == selectedDay && parseTime(it.timeLocal) == t
                }
                if (clash) {
                    vm.toast("${hhmm(t)} already exists here — select the cell to edit it")
                    return@PrimaryButton
                }
                onAdd(
                    ScheduleEventEntity(
                        courseTypeId = typeId,
                        dayNo = selectedDay,
                        timeLocal = t.toString(),
                        repeats = repeats.toIntOrNull()?.coerceIn(1, 32) ?: 6,
                        gapSeconds = gap.trim().toIntOrNull(),
                        track = null,
                    ),
                )
                time = ""
            }
            Text(
                "adds to " + when {
                    typeId == null -> "the no-course schedule"
                    selectedDay == null -> "the DEF column — every day that has no rows of its own"
                    else -> "day $selectedDay"
                },
                fontSize = 12.5.sp,
                fontWeight = if (defTarget) FontWeight.Medium else FontWeight.Normal,
                color = if (defTarget) Nocturne.Accent200 else Nocturne.Neutral500,
            )
        }
    }
}

/**
 * The 272 dp right aside. The **em-dash option is null, meaning "inherit the
 * setting"** — this nullability is load-bearing and survives into the data
 * model (design handoff §3).
 */
@Composable
private fun Inspector(
    event: ScheduleEventEntity?,
    typeName: String,
    settings: Map<String, String>,
    emptyHint: String?,
    onChange: (ScheduleEventEntity) -> Unit,
    onDelete: (Long) -> Unit,
) {
    Column(
        Modifier
            .width(272.dp)
            .fillMaxHeight()
            .background(Nocturne.NavRail)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (event == null) {
            Eyebrow("Inspector")
            Text(
                emptyHint ?: "Select a cell to edit its strike count, gap and track.",
                fontSize = 13.5.sp,
                color = if (emptyHint != null) Nocturne.Warning else Nocturne.Neutral500,
            )
            return@Column
        }

        Text(
            event.timeLocal,
            fontSize = 30.sp,
            fontFamily = Nocturne.Mono,
            fontWeight = FontWeight.Light,
            color = Nocturne.Text,
        )
        Text(
            "$typeName · " + (event.dayNo?.let { "Day $it" } ?: "default pattern"),
            fontSize = 12.5.sp,
            color = Nocturne.Neutral500,
        )

        Hairline()

        Eyebrow("Strikes")
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Stepper("−", "One fewer strike") {
                onChange(event.copy(repeats = (event.repeats - 1).coerceIn(1, 32)))
            }
            Text(
                event.repeats.toString(),
                fontSize = 25.sp,
                fontFamily = Nocturne.Mono,
                color = Nocturne.Text,
                modifier = Modifier.width(48.dp),
            )
            Stepper("+", "One more strike") {
                onChange(event.copy(repeats = (event.repeats + 1).coerceIn(1, 32)))
            }
        }

        Eyebrow("Gap")
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            // "—" is null: inherit gong_gap_seconds.
            Chip("—", event.gapSeconds == null, "Inherit gap from settings") {
                onChange(event.copy(gapSeconds = null))
            }
            for (g in listOf(2, 4, 6, 8)) {
                Chip("${g}s", event.gapSeconds == g, "Gap of $g seconds") {
                    onChange(event.copy(gapSeconds = g))
                }
            }
        }

        Eyebrow("Track")
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Chip("—", event.track == null, "Inherit track from settings") {
                onChange(event.copy(track = null))
            }
            for (t in listOf("ting", "drum")) {
                Chip(t, event.track == t, "Play the $t track") {
                    onChange(event.copy(track = t))
                }
            }
        }

        val gap = event.gapSeconds ?: settings["gong_gap_seconds"]?.toIntOrNull() ?: 4
        Text(
            "burst ≈ ${(event.repeats - 1) * gap}s" +
                if (event.gapSeconds == null) " (inherited gap)" else "",
            fontSize = 12.5.sp,
            fontFamily = Nocturne.Mono,
            color = Nocturne.Neutral500,
        )

        // No weight() here — this Column is vertically scrollable, so its height
        // is unbounded and weight() would throw.
        Spacer(Modifier.height(8.dp))
        RemoveEventButton { onDelete(event.id) }
    }
}

/**
 * Two-tap remove, matching the Courses table's DeleteButton: the first tap arms
 * ("remove — sure?"), the second within 3 s deletes. Re-entering a lost event
 * costs staff real time.
 */
@Composable
private fun RemoveEventButton(onClick: () -> Unit) {
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(armed) {
        if (armed) {
            delay(3_000)
            armed = false
        }
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(Nocturne.MIN_TOUCH_DP.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Nocturne.Error.copy(alpha = if (armed) 0.30f else 0.12f))
            .border(
                1.dp,
                Nocturne.Error.copy(alpha = if (armed) 0.85f else 0.34f),
                RoundedCornerShape(8.dp),
            )
            .clickable(
                onClickLabel = if (armed) "Confirm remove event" else "Remove event",
            ) {
                if (armed) {
                    armed = false
                    onClick()
                } else {
                    armed = true
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (armed) "Remove event — sure?" else "Remove event",
            fontSize = 13.5.sp,
            color = Nocturne.Error,
        )
    }
}

@Composable
private fun Stepper(label: String, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(Nocturne.MIN_TOUCH_DP.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Nocturne.SurfaceHigh)
            .border(1.dp, Nocturne.Neutral700, RoundedCornerShape(8.dp))
            .clickable(onClickLabel = description, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 17.sp, color = Nocturne.Text)
    }
}

@Composable
private fun Chip(
    label: String,
    selected: Boolean,
    description: String,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .height(Nocturne.MIN_TOUCH_DP.dp)
            .widthIn(min = 40.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) Nocturne.Accent.copy(alpha = 0.22f) else Color.Transparent)
            .border(
                1.dp,
                if (selected) Nocturne.Accent else Nocturne.Neutral700,
                RoundedCornerShape(6.dp),
            )
            .clickable(onClickLabel = description, onClick = onClick)
            .semantics { contentDescription = description }
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontSize = 12.5.sp,
            fontFamily = Nocturne.Mono,
            color = if (selected) Nocturne.Accent200 else Nocturne.Neutral400,
        )
    }
}
