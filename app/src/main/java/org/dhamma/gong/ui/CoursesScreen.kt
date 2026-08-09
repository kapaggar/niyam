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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.dhamma.gong.data.CourseTypeEntity
import java.time.LocalDate

/**
 * Add and remove courses. The single most important thing on this screen is
 * that **the start date is the zero day (arrival day)**, not day 1
 * (design handoff §2).
 */
@Composable
fun CoursesScreen(vm: AppViewModel) {
    val rows by vm.courseRows.collectAsState()
    val types by vm.courseTypes.collectAsState()

    var typeId by remember { mutableStateOf<Int?>(null) }
    var dateText by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        ScreenTitle(
            "Courses",
            "The start date is the course's zero day — the arrival day, not day 1. " +
                "A course stays active for its whole window, so one that began while the " +
                "device was off is still found.",
        )

        SurfaceCard(padding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TypePicker(
                    types = types,
                    selected = types.firstOrNull { it.id == typeId },
                    modifier = Modifier.width(190.dp),
                ) { typeId = it.id }

                Field(
                    value = dateText,
                    onValueChange = { dateText = it },
                    placeholder = "YYYY-MM-DD",
                    modifier = Modifier.width(150.dp),
                )
                Field(
                    value = note,
                    onValueChange = { note = it },
                    placeholder = "note (optional)",
                    modifier = Modifier.weight(1f),
                )
                PrimaryButton("Add course") {
                    val parsed = runCatching { LocalDate.parse(dateText.trim()) }.getOrNull()
                    val t = typeId
                    when {
                        t == null -> vm.toast("Pick a course type")
                        parsed == null -> vm.toast("Pick a start date")
                        else -> {
                            vm.addCourse(t, parsed, note.trim())
                            dateText = ""
                            note = ""
                        }
                    }
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
                HeaderCell("Type", 170.dp)
                Text(
                    "NOTE",
                    fontSize = 11.sp,
                    letterSpacing = 1.1.sp,
                    color = Nocturne.Neutral500,
                    modifier = Modifier.weight(1f),
                )
                HeaderCell("Status", 92.dp)
                Spacer(Modifier.width(44.dp))
            }
            Hairline()

            LazyColumn {
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
                            Text(
                                row.course.startDate.toString(),
                                fontSize = 13.5.sp,
                                fontFamily = Nocturne.Mono,
                                color = if (active) Nocturne.Accent200 else Nocturne.Text,
                                modifier = Modifier.width(150.dp),
                            )
                            Text(
                                row.type?.name ?: "type ${row.course.courseTypeId}",
                                fontSize = 13.5.sp,
                                color = Nocturne.Text,
                                modifier = Modifier.width(170.dp),
                            )
                            Text(
                                row.course.note.ifBlank { "—" },
                                fontSize = 13.5.sp,
                                color = Nocturne.Neutral500,
                                modifier = Modifier.weight(1f),
                            )
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
                            DeleteButton { vm.deleteCourse(row.course.id) }
                        }
                        Hairline()
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- widgets

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
) {
    Box(
        modifier
            .height(38.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Nocturne.SurfaceHigh)
            .border(1.dp, Nocturne.Neutral700, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) {
            Text(
                placeholder,
                fontSize = 13.5.sp,
                color = Nocturne.Neutral600,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(
                fontSize = 13.5.sp,
                color = Nocturne.Text,
                fontFamily = Nocturne.Mono,
            ),
            cursorBrush = SolidColor(Nocturne.Accent),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun PrimaryButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(38.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Nocturne.Accent)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 13.5.sp, fontWeight = FontWeight.Medium, color = Nocturne.Bg)
    }
}

/**
 * Two-tap delete: the first tap arms the button ("sure?"), the second within
 * 3 s deletes. Keeps the design's no-dialogs feel while making a mis-tap
 * harmless — re-entering a deleted course costs staff real time.
 */
@Composable
private fun DeleteButton(onClick: () -> Unit) {
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(armed) {
        if (armed) {
            delay(3_000)
            armed = false
        }
    }
    Box(
        Modifier
            .height(30.dp)
            .widthIn(min = 30.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Nocturne.Error.copy(alpha = if (armed) 0.30f else 0.12f))
            .border(1.dp, Nocturne.Error.copy(alpha = if (armed) 0.85f else 0.34f), RoundedCornerShape(6.dp))
            .clickable {
                if (armed) {
                    armed = false
                    onClick()
                } else {
                    armed = true
                }
            }
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(if (armed) "sure?" else "✕", fontSize = 13.sp, color = Nocturne.Error)
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
        Row(
            Modifier
                .fillMaxWidth()
                .height(38.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Nocturne.SurfaceHigh)
                .border(1.dp, Nocturne.Neutral700, RoundedCornerShape(8.dp))
                .clickable { open = true }
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
