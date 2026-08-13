package org.dhamma.gong.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.dhamma.gong.data.CourseTypeEntity
import java.time.LocalDate

/** Well-formed ISO shape; a match that still fails to parse is a real-date problem. */
private val ISO_DATE_SHAPE = Regex("""\d{4}-\d{2}-\d{2}""")

/** Below this width the add row and the table have to shed columns to stay reachable. */
private val NARROW_WIDTH = 760.dp

/**
 * Add and remove courses. The single most important thing on this screen is
 * that **the start date is the zero day (arrival day)**, not day 1
 * (design handoff §2).
 */
@Composable
fun CoursesScreen(vm: AppViewModel) {
    // Two views of the same list: the folded one the screen shows by default,
    // and the full one behind the "older courses" row. A hidden course still
    // counts for overlap and still has to be deletable, so it must stay
    // reachable rather than merely vanish.
    val visible by vm.visibleCourseRows.collectAsState()
    val allRows by vm.courseRows.collectAsState()
    var showAllPast by rememberSaveable { mutableStateOf(false) }
    val rows = if (showAllPast) allRows else visible.rows

    val types by vm.courseTypes.collectAsState()
    // "Today" for the example date must be the appliance's zone, never the device's.
    val zone by vm.applianceZone.collectAsState()

    // Saveable: a rotation or process-death restore must not eat a half-typed course.
    var typeId by rememberSaveable { mutableStateOf<Int?>(null) }
    var dateText by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }

    val onAdd: () -> Unit = {
        val raw = dateText.trim()
        val parsed = runCatching { LocalDate.parse(raw) }.getOrNull()
        val t = typeId
        when {
            t == null -> vm.toast("Pick a course type")
            raw.isEmpty() -> vm.toast("Pick a start date")
            parsed == null && !ISO_DATE_SHAPE.matches(raw) ->
                vm.toast("Use YYYY-MM-DD, e.g. ${LocalDate.now(zone)}")
            parsed == null ->
                vm.toast("$raw isn't a real date — check the month and day")
            else -> {
                vm.addCourse(t, parsed, note.trim())
                dateText = ""
                note = ""
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
    // Below this width the fixed-dp add row would push "Add course" out of the
    // card, which clips it away entirely — collapse to two lines instead.
    val narrow = maxWidth < NARROW_WIDTH

    Column(
        Modifier.fillMaxSize().padding(if (narrow) 16.dp else 32.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        ScreenTitle(
            "Courses",
            "The start date is the course's zero day — the arrival day, not day 1. " +
                "A course stays active for its whole window, so one that began while the " +
                "device was off is still found.",
        )

        SurfaceCard(padding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
            val typePicker: @Composable (Modifier) -> Unit = { m ->
                TypePicker(
                    types = types,
                    selected = types.firstOrNull { it.id == typeId },
                    modifier = m,
                ) { typeId = it.id }
            }
            val dateField: @Composable (Modifier) -> Unit = { m ->
                Field(
                    value = dateText,
                    onValueChange = { dateText = it },
                    placeholder = "YYYY-MM-DD",
                    modifier = m,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done,
                    ),
                )
            }
            val noteField: @Composable (Modifier) -> Unit = { m ->
                Field(
                    value = note,
                    onValueChange = { note = it },
                    placeholder = "note (optional)",
                    modifier = m,
                )
            }

            if (narrow) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        typePicker(Modifier.weight(1f))
                        dateField(Modifier.width(140.dp))
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        noteField(Modifier.weight(1f))
                        PrimaryButton("Add course", onClick = onAdd)
                    }
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    typePicker(Modifier.width(190.dp))
                    dateField(Modifier.width(150.dp))
                    noteField(Modifier.weight(1f))
                    PrimaryButton("Add course", onClick = onAdd)
                }
            }
        }

        // Table
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HeaderCell("Start (zero day)", 150.dp)
                HeaderCell("Type", if (narrow) 120.dp else 170.dp)
                if (narrow) {
                    // NOTE moves under the start date; keep status and delete right-aligned.
                    Spacer(Modifier.weight(1f))
                } else {
                    Text(
                        "NOTE",
                        fontSize = 11.sp,
                        letterSpacing = 1.1.sp,
                        color = Nocturne.Neutral500,
                        modifier = Modifier.weight(1f),
                    )
                }
                HeaderCell("Status", 92.dp)
                Spacer(Modifier.width(44.dp))
            }
            Hairline()

            LazyColumn {
                if (rows.isEmpty()) {
                    item {
                        Column(Modifier.fillMaxWidth().padding(vertical = 22.dp)) {
                            Text(
                                "No courses yet.",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = Nocturne.Text,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Add one above. Enter the arrival day as the start date — that " +
                                    "day is day 0, not day 1.",
                                fontSize = 13.sp,
                                color = Nocturne.Neutral500,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Until a course is running the appliance keeps the no-course " +
                                    "schedule, so those gongs still ring.",
                                fontSize = 13.sp,
                                color = Nocturne.Neutral500,
                            )
                        }
                    }
                }

                // The fold sits at the top, because in calendar order that is
                // exactly where the hidden courses belong — expanding grows the
                // list upwards from here instead of shuffling what is on screen.
                if (visible.hiddenPast > 0) {
                    item(key = "older-courses") {
                        OlderCourses(visible.hiddenPast, showAllPast) {
                            showAllPast = !showAllPast
                        }
                    }
                }

                items(rows, key = { it.course.id }) { row ->
                    val active = row.status == AppViewModel.CourseRow.Status.ACTIVE
                    Column {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .background(
                                    if (active) Nocturne.Accent.copy(alpha = 0.12f) else Color.Transparent,
                                )
                                .padding(vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Column(Modifier.width(150.dp)) {
                                Text(
                                    row.course.startDate.toString(),
                                    fontSize = 13.5.sp,
                                    fontFamily = Nocturne.Mono,
                                    color = if (active) Nocturne.Accent200 else Nocturne.Text,
                                )
                                // The window, not just the start: the subtitle promises it.
                                row.type?.let { t ->
                                    Text(
                                        "→ ${row.course.startDate.plusDays(t.totalDays.toLong())}",
                                        fontSize = 11.5.sp,
                                        fontFamily = Nocturne.Mono,
                                        color = Nocturne.Neutral600,
                                    )
                                }
                                if (narrow && row.course.note.isNotBlank()) {
                                    Text(
                                        row.course.note,
                                        fontSize = 11.5.sp,
                                        color = Nocturne.Neutral500,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            Text(
                                row.type?.name ?: "type ${row.course.courseTypeId}",
                                fontSize = 13.5.sp,
                                color = Nocturne.Text,
                                modifier = Modifier.width(if (narrow) 120.dp else 170.dp),
                            )
                            if (narrow) {
                                Spacer(Modifier.weight(1f))
                            } else {
                                Text(
                                    row.course.note.ifBlank { "—" },
                                    fontSize = 13.5.sp,
                                    color = Nocturne.Neutral500,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            Text(
                                when (row.status) {
                                    AppViewModel.CourseRow.Status.ACTIVE -> "ACTIVE"
                                    AppViewModel.CourseRow.Status.OVERLAP -> "OVERLAP"
                                    AppViewModel.CourseRow.Status.UPCOMING -> "upcoming"
                                    AppViewModel.CourseRow.Status.PAST -> "past"
                                },
                                fontSize = 12.5.sp,
                                fontFamily = Nocturne.Mono,
                                color = when (row.status) {
                                    AppViewModel.CourseRow.Status.ACTIVE -> Nocturne.Accent200
                                    AppViewModel.CourseRow.Status.OVERLAP -> Nocturne.Warning
                                    else -> Nocturne.Neutral600
                                },
                                modifier = Modifier.width(92.dp),
                            )
                            DeleteButton(
                                label = row.course.startDate.toString(),
                                modifier = Modifier.width(44.dp),
                            ) { vm.deleteCourse(row.course.id) }
                        }
                        Hairline()
                    }
                }
            }
        }
    }
    }
}

// ---------------------------------------------------------------- widgets

/**
 * The one row that stands for every folded-away finished course. It has to be a
 * control, not a caption: a hidden course still counts towards an OVERLAP
 * warning and still has to be deletable, and a list that silently drops rows
 * reads as data loss.
 */
@Composable
private fun OlderCourses(hidden: Int, expanded: Boolean, onClick: () -> Unit) {
    val label =
        if (expanded) "Hide the $hidden older ${plural(hidden)}"
        else "Show $hidden older finished ${plural(hidden)}"
    Column {
        Box(
            Modifier
                .fillMaxWidth()
                .height(Nocturne.MIN_TOUCH_DP.dp)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics { contentDescription = label },
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                (if (expanded) "▴  " else "▾  ") + label,
                fontSize = 12.5.sp,
                color = Nocturne.Neutral500,
            )
        }
        Hairline()
    }
}

private fun plural(n: Int) = if (n == 1) "course" else "courses"

@Composable
private fun HeaderCell(text: String, width: androidx.compose.ui.unit.Dp) {
    Text(
        text.uppercase(),
        fontSize = 11.sp,
        letterSpacing = 1.1.sp,
        color = Nocturne.Neutral500,
        modifier = Modifier.width(width),
    )
}

@Composable
fun Field(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    // 38 dp painted box (design handoff) centred in a 44 dp touch target.
    Box(
        modifier.height(Nocturne.MIN_TOUCH_DP.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(38.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Nocturne.SurfaceHigh)
                .border(1.dp, Nocturne.Neutral700, RoundedCornerShape(8.dp)),
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            keyboardOptions = keyboardOptions,
            textStyle = TextStyle(
                fontSize = 13.5.sp,
                color = Nocturne.Text,
                fontFamily = Nocturne.Mono,
            ),
            cursorBrush = SolidColor(Nocturne.Accent),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(horizontal = 10.dp),
            decorationBox = { inner ->
                Box(
                    Modifier.fillMaxHeight(),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (value.isEmpty()) {
                        Text(
                            placeholder,
                            fontSize = 13.5.sp,
                            color = Nocturne.Neutral600,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    inner()
                }
            },
        )
    }
}

@Composable
fun PrimaryButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    // 38 dp painted pill inside a 44 dp hit target.
    Box(
        modifier
            .height(Nocturne.MIN_TOUCH_DP.dp)
            .clickable(onClick = onClick, role = Role.Button),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .height(38.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Nocturne.Accent)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(label, fontSize = 13.5.sp, fontWeight = FontWeight.Medium, color = Nocturne.Bg)
        }
    }
}

/**
 * Two-tap delete: the first tap arms the button ("sure?"), the second within
 * 3 s deletes. Keeps the design's no-dialogs feel while making a mis-tap
 * harmless — re-entering a deleted course costs staff real time.
 */
@Composable
private fun DeleteButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(armed) {
        if (armed) {
            delay(3_000)
            armed = false
        }
    }
    // 30 dp painted glyph centred in a 44 dp hit target; the description changes
    // with the armed state so a screen reader can tell the two taps apart.
    Box(
        modifier
            .height(Nocturne.MIN_TOUCH_DP.dp)
            .clickable(role = Role.Button) {
                if (armed) {
                    armed = false
                    onClick()
                } else {
                    armed = true
                }
            }
            .semantics {
                contentDescription =
                    if (armed) "Confirm delete of course starting $label"
                    else "Delete course starting $label"
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .height(30.dp)
                .widthIn(min = 30.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Nocturne.Error.copy(alpha = if (armed) 0.30f else 0.12f))
                .border(1.dp, Nocturne.Error.copy(alpha = if (armed) 0.85f else 0.34f), RoundedCornerShape(6.dp))
                .padding(horizontal = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (armed) "sure?" else "✕",
                fontSize = 13.sp,
                color = Nocturne.Error,
                maxLines = 1,
            )
        }
    }
}

@Composable
fun TypePicker(
    types: List<CourseTypeEntity>,
    selected: CourseTypeEntity?,
    modifier: Modifier = Modifier,
    includeNoCourse: Boolean = false,
    onNoCourse: () -> Unit = {},
    onSelect: (CourseTypeEntity) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        // 38 dp painted control centred in a 44 dp hit target.
        Box(
            Modifier
                .fillMaxWidth()
                .height(Nocturne.MIN_TOUCH_DP.dp)
                .clickable(role = Role.Button) { open = true },
            contentAlignment = Alignment.Center,
        ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(38.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Nocturne.SurfaceHigh)
                .border(1.dp, Nocturne.Neutral700, RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                selected?.let { "${it.name} (${it.totalDays})" }
                    ?: if (includeNoCourse) "No course" else "Course type…",
                fontSize = 13.5.sp,
                color = if (selected == null) Nocturne.Neutral600 else Nocturne.Text,
                modifier = Modifier.weight(1f),
            )
            Text("▾", fontSize = 12.sp, color = Nocturne.Neutral500)
        }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (includeNoCourse) {
                DropdownMenuItem(
                    text = { Text("No course", fontSize = 13.5.sp) },
                    onClick = {
                        onNoCourse()
                        open = false
                    },
                )
            }
            for (t in types) {
                DropdownMenuItem(
                    text = { Text("${t.name} (${t.totalDays})", fontSize = 13.5.sp) },
                    onClick = {
                        onSelect(t)
                        open = false
                    },
                )
            }
        }
    }
}
