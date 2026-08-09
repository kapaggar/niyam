package org.dhamma.gong.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.dhamma.gong.data.ScheduleEventEntity
import java.time.LocalTime

/**
 * The day-column grid: days across, wall-clock times down, the whole
 * course-type matrix at once — so a day-3 edit visibly reads as day 3
 * (design handoff §3, design doc §13).
 *
 * The **DEF** column is `day_no IS NULL`, the mid-course default pattern used
 * for any day with no rows of its own. For "No course" the grid collapses to a
 * single **N/C** column (`course_type_id IS NULL`).
 */
@Composable
fun ScheduleScreen(vm: AppViewModel) {
    val types by vm.courseTypes.collectAsState()
    val allEvents by vm.events.collectAsState()
    val settings by vm.settings.collectAsState()

    // null = the "No course" schedule.
    var typeId by remember { mutableStateOf<Int?>(1) }
    var selected by remember { mutableStateOf<CellKey?>(null) }

    val type = types.firstOrNull { it.id == typeId }
    val events = allEvents.filter { it.courseTypeId == typeId }

    // Columns: days 0..total, then DEF. No course collapses to one column.
    val dayColumns: List<Int?> = if (typeId == null) {
        listOf(null)
    } else {
        (0..(type?.totalDays ?: 0)).map { it as Int? } + listOf<Int?>(null)
    }
    val times = events.mapNotNull { runCatching { LocalTime.parse(it.timeLocal) }.getOrNull() }
        .distinct()
        .sorted()

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
                TypePicker(
                    types = types,
                    selected = type,
                    modifier = Modifier.width(220.dp),
                    includeNoCourse = true,
                    onNoCourse = {
                        typeId = null
                        selected = null
                    },
                ) {
                    typeId = it.id
                    selected = null
                }
            }

            Grid(
                times = times,
                dayColumns = dayColumns,
                events = events,
                typeId = typeId,
                selected = selected,
                onSelect = { key ->
                    selected = key
                    // Tapping an empty cell creates a default event
                    // (×6, inherit gap, inherit track) — design handoff.
                    val existing = events.firstOrNull {
                        it.dayNo == key.dayNo && it.timeLocal == key.time.toString()
                    }
                    if (existing == null) {
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
                },
                modifier = Modifier.weight(1f),
            )

            AddRow(typeId = typeId, selectedDay = selected?.dayNo, onAdd = vm::addEvent, vm = vm)
        }

        Inspector(
            event = selected?.let { key ->
                events.firstOrNull { it.dayNo == key.dayNo && it.timeLocal == key.time.toString() }
            },
            typeName = if (typeId == null) "No course" else type?.name ?: "",
            settings = settings,
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
    events: List<ScheduleEventEntity>,
    typeId: Int?,
    selected: CellKey?,
    onSelect: (CellKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    val byCell = events.associateBy { it.dayNo to it.timeLocal }
    val hScroll = rememberScrollState()

    Column(modifier.fillMaxSize()) {
        Row(Modifier.horizontalScroll(hScroll)) {
            Column {
                // Header row
                Row(Modifier.height(36.dp)) {
                    Box(Modifier.width(60.dp))
                    for (day in dayColumns) {
                        Box(
                            Modifier
                                .width(46.dp)
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
                                fontSize = 11.5.sp,
                                fontFamily = Nocturne.Mono,
                                color = Nocturne.Neutral400,
                            )
                        }
                    }
                }
                Hairline()

                Column(Modifier.verticalScroll(rememberScrollState())) {
                    for (t in times) {
                        Row(Modifier.height(44.dp)) {
                            Box(
                                Modifier.width(60.dp).fillMaxHeight(),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                Text(
                                    "%02d:%02d".format(t.hour, t.minute),
                                    fontSize = 12.5.sp,
                                    fontFamily = Nocturne.Mono,
                                    color = Nocturne.Neutral400,
                                )
                            }
                            for (day in dayColumns) {
                                val row = byCell[day to t.toString()]
                                val isSelected = selected != null &&
                                    selected.dayNo == day && selected.time == t
                                Cell(
                                    label = row?.let { "×${it.repeats}" } ?: "·",
                                    filled = row != null,
                                    selected = isSelected,
                                    onClick = { onSelect(CellKey(day, t)) },
                                )
                            }
                        }
                    }
                }
            }
        }
        if (times.isEmpty()) {
            Text(
                "No events for this course type yet — add one below.",
                fontSize = 13.5.sp,
                color = Nocturne.Neutral500,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}

@Composable
private fun Cell(label: String, filled: Boolean, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .width(46.dp)
            .height(44.dp)
            .background(if (filled) Nocturne.Accent.copy(alpha = 0.14f) else Color.Transparent)
            .border(1.dp, Nocturne.Hairline)
            .then(
                if (selected) Modifier.border(2.dp, Nocturne.Accent) else Modifier,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontSize = 12.5.sp,
            fontFamily = Nocturne.Mono,
            color = if (filled) Nocturne.Accent200 else Nocturne.Neutral700,
        )
    }
}

@Composable
private fun AddRow(
    typeId: Int?,
    selectedDay: Int?,
    onAdd: (ScheduleEventEntity) -> Unit,
    vm: AppViewModel,
) {
    var time by remember { mutableStateOf("") }
    var repeats by remember { mutableStateOf("6") }
    var gap by remember { mutableStateOf("") }

    SurfaceCard(padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Field(time, { time = it }, "HH:MM", Modifier.width(110.dp))
            Field(repeats, { repeats = it }, "repeats", Modifier.width(90.dp))
            Field(gap, { gap = it }, "gap s", Modifier.width(90.dp))
            PrimaryButton("Add") {
                val t = runCatching { LocalTime.parse(time.trim()) }.getOrNull()
                if (t == null) {
                    vm.toast("Pick a time first")
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
                    selectedDay == null -> "the DEF column (mid-course default)"
                    else -> "day $selectedDay"
                },
                fontSize = 12.5.sp,
                color = Nocturne.Neutral500,
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
    onChange: (ScheduleEventEntity) -> Unit,
    onDelete: (Long) -> Unit,
) {
    Column(
        Modifier
            .width(272.dp)
            .fillMaxHeight()
            .background(Nocturne.NavRail)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (event == null) {
            Eyebrow("Inspector")
            Text(
                "Select a cell to edit its strike count, gap and track.",
                fontSize = 13.5.sp,
                color = Nocturne.Neutral500,
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
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Stepper("−") { onChange(event.copy(repeats = (event.repeats - 1).coerceIn(1, 32))) }
            Text(
                event.repeats.toString(),
                fontSize = 25.sp,
                fontFamily = Nocturne.Mono,
                color = Nocturne.Text,
                modifier = Modifier.width(48.dp),
            )
            Stepper("+") { onChange(event.copy(repeats = (event.repeats + 1).coerceIn(1, 32))) }
        }

        Eyebrow("Gap")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            // "—" is null: inherit gong_gap_seconds.
            Chip("—", event.gapSeconds == null) { onChange(event.copy(gapSeconds = null)) }
            for (g in listOf(2, 4, 6, 8)) {
                Chip("${g}s", event.gapSeconds == g) { onChange(event.copy(gapSeconds = g)) }
            }
        }

        Eyebrow("Track")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Chip("—", event.track == null) { onChange(event.copy(track = null)) }
            for (t in listOf("ting", "drum")) {
                Chip(t, event.track == t) { onChange(event.copy(track = t)) }
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

        Spacer(Modifier.weight(1f))
        Box(
            Modifier
                .fillMaxWidth()
                .height(Nocturne.MIN_TOUCH_DP.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Nocturne.Error.copy(alpha = 0.12f))
                .border(1.dp, Nocturne.Error.copy(alpha = 0.34f), RoundedCornerShape(8.dp))
                .clickable { onDelete(event.id) },
            contentAlignment = Alignment.Center,
        ) {
            Text("Remove event", fontSize = 13.5.sp, color = Nocturne.Error)
        }
    }
}

@Composable
private fun Stepper(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Nocturne.SurfaceHigh)
            .border(1.dp, Nocturne.Neutral700, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 17.sp, color = Nocturne.Text)
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .height(32.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) Nocturne.Accent.copy(alpha = 0.22f) else Color.Transparent)
            .border(
                1.dp,
                if (selected) Nocturne.Accent else Nocturne.Neutral700,
                RoundedCornerShape(6.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
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
