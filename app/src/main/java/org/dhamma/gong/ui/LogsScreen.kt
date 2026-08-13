package org.dhamma.gong.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import org.dhamma.gong.domain.PlayKind
import org.dhamma.gong.domain.PlayResult
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

private val COL_GAP = 8.dp
// One timestamp column, in the appliance's own zone. The UTC `ts_utc` value is
// still what the database stores and orders by — it is simply not something a
// server standing at a wall tablet can read, and showing both invited the
// reader to reconcile two numbers that always mean the same instant.
private val LOCAL_W = 104.dp
private val KIND_W = 82.dp
private val FILE_W = 132.dp
private val STRIKES_W = 34.dp
private val RESULT_W = 92.dp

/** DETAIL grows into whatever is left, but never shrinks past this. */
private val DETAIL_MIN_W = 220.dp

private val FIXED_W = LOCAL_W + KIND_W + FILE_W + STRIKES_W + RESULT_W + COL_GAP * 5

/**
 * `play_log`, read-only, so no PIN.
 *
 * `ts_utc` is still what the database stores and orders by; the column shown is
 * that instant rendered in the appliance's zone, because a 05:50 gong logged as
 * `00:20:00Z` is unreadable at two metres. Showing both asked the reader to
 * reconcile two numbers that always mean the same moment. The detail column
 * still carries the local scheduled instant for a missed fire (handoff §4).
 *
 * The table is wider than a phone pane, so the header and every row share one
 * horizontal scroll state — the house pattern from `ScheduleScreen` — instead
 * of letting the trailing columns measure to zero and vanish.
 */
@Composable
fun LogsScreen(vm: AppViewModel) {
    val rows by vm.logs.collectAsStateWithLifecycle()
    val zone by vm.applianceZone.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf(Filter.ALL) }

    val visible = remember(rows, filter) {
        rows.filter { row ->
            when (filter) {
                Filter.ALL -> true
                Filter.GONG -> row.kind == PlayKind.GONG || row.kind == PlayKind.TEST_GONG
                Filter.DOHA -> row.kind == PlayKind.DOHA || row.kind == PlayKind.TEST_DOHA
                Filter.MISSED -> row.result == PlayResult.MISSED
                Filter.ERROR -> row.result == PlayResult.ERROR
            }
        }
    }

    // One state, two readers (header + rows). Separate states would let the
    // columns drift out of alignment the moment anyone drags a row.
    val hScroll = rememberScrollState()

    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenTitle(
            "Logs",
            "Times are ${zone.id}. Showing ${visible.size} of the latest ${rows.size}.",
        )

        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                Modifier.weight(1f).selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (f in Filter.entries) {
                    FilterChip(f.label, f == filter) { filter = f }
                }
            }
            // Nothing to clear, nothing to arm — and an always-present
            // destructive control on an empty table invites the mis-tap.
            if (rows.isNotEmpty()) {
                ClearButton(rows.size) { vm.clearLogs() }
            }
        }

        BoxWithConstraints(Modifier.fillMaxSize()) {
            val detailW = maxOf(DETAIL_MIN_W, maxWidth - FIXED_W)

            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 4.dp).horizontalScroll(hScroll),
                    horizontalArrangement = Arrangement.spacedBy(COL_GAP),
                ) {
                    Head("When", LOCAL_W)
                    Head("What", KIND_W)
                    Head("File", FILE_W)
                    Head("×", STRIKES_W, description = "strikes")
                    Head("Result", RESULT_W)
                    Head("Detail", detailW)
                }
                Hairline()

                when {
                    // Before the fix this sat BELOW a fillMaxSize list and could
                    // never render.
                    rows.isEmpty() -> Empty("Nothing logged yet.")

                    // Never claim nothing was logged when a filter is hiding it —
                    // this screen exists to diagnose, not to mislead.
                    visible.isEmpty() -> Empty(
                        "No ${filter.label} entries in the latest ${rows.size}.",
                    )

                    else -> LazyColumn(Modifier.fillMaxSize()) {
                        items(visible, key = { it.id }) { row ->
                            Column {
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp)
                                        .horizontalScroll(hScroll),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(COL_GAP),
                                ) {
                                    Mono(localStamp(row.tsUtc, zone), LOCAL_W, Nocturne.Neutral300)
                                    Mono(row.kind, KIND_W, Nocturne.Neutral300)
                                    Mono(row.file, FILE_W, Nocturne.Neutral400)
                                    Mono(row.repeats.toString(), STRIKES_W, Nocturne.Neutral400)
                                    Mono(row.result, RESULT_W, resultColor(row.result))
                                    Mono(
                                        row.detail.ifBlank { "—" },
                                        detailW,
                                        Nocturne.Neutral500,
                                    )
                                }
                                Hairline()
                            }
                        }
                    }
                }
            }
        }
    }
}

private enum class Filter(val label: String) {
    ALL("all"), GONG("gong"), DOHA("doha"), MISSED("missed"), ERROR("error")
}

/**
 * The stored instant in the appliance zone. `ts_utc` is untouched; when the
 * local calendar date differs from the UTC one the shift is spelled out, so a
 * 19:00Z row cannot read as "today 00:30" by accident.
 */
private fun localStamp(tsUtc: String, zone: ZoneId): String {
    val instant = runCatching { Instant.parse(tsUtc) }.getOrNull() ?: return "—"
    val local = instant.atZone(zone)
    val shift = local.toLocalDate().toEpochDay() -
        instant.atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
    val marker = when {
        shift > 0L -> " +${shift}d"
        shift < 0L -> " ${shift}d"
        else -> ""
    }
    return "%02d:%02d:%02d".format(local.hour, local.minute, local.second) + marker
}

/** Design handoff §4 result colouring. */
private fun resultColor(result: String): Color = when (result) {
    PlayResult.OK -> Nocturne.Neutral300
    PlayResult.MISSED, PlayResult.ERROR -> Nocturne.Error
    PlayResult.STOPPED, PlayResult.SKIPPED_CLOCK -> Nocturne.Warning
    else -> Nocturne.Neutral400
}

@Composable
private fun Empty(text: String) {
    Text(
        text,
        fontSize = 13.5.sp,
        color = Nocturne.Neutral500,
        modifier = Modifier.padding(top = 12.dp),
    )
}

@Composable
private fun Head(text: String, width: Dp, description: String? = null) {
    Text(
        text.uppercase(),
        fontSize = 9.5.sp,
        letterSpacing = 0.6.sp,
        color = Nocturne.Neutral500,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .width(width)
            .then(
                if (description == null) {
                    Modifier
                } else {
                    Modifier.semantics { contentDescription = description }
                },
            ),
    )
}

@Composable
private fun Mono(text: String, width: Dp, color: Color) {
    Text(
        text,
        // 11 sp, not the 12.5 sp the rest of the app uses. This is the one
        // table with six columns competing for a phone-width pane, and losing
        // RESULT and DETAIL off the right edge costs more than the extra
        // legibility of a larger glyph — those two columns are the whole reason
        // someone opens Logs.
        fontSize = 11.sp,
        fontFamily = Nocturne.Mono,
        color = color,
        maxLines = 1,
        // Every column is fixed-width, so without this a long value is silently
        // cut mid-glyph and reads as a shorter, wrong value.
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.width(width),
    )
}

/**
 * Two-tap clear, matching the Courses delete: the first tap arms ("clear all?"),
 * a second within 3 s empties `play_log`. Logs is the one unlocked screen that
 * can now destroy something, so a single stray tap must not be enough — and the
 * count is in the label so nobody clears 300 rows thinking they clear the eight
 * a filter is showing.
 */
@Composable
private fun ClearButton(count: Int, onClick: () -> Unit) {
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(armed) {
        if (armed) {
            delay(3_000)
            armed = false
        }
    }
    val label = if (armed) "clear all $count?" else "clear log"
    Box(
        Modifier
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
                    if (armed) "Confirm clearing all $count log entries"
                    else "Clear all $count log entries"
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .height(32.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Nocturne.Error.copy(alpha = if (armed) 0.30f else 0.12f))
                .border(
                    1.dp,
                    Nocturne.Error.copy(alpha = if (armed) 0.85f else 0.34f),
                    RoundedCornerShape(6.dp),
                )
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(label, fontSize = 12.5.sp, color = Nocturne.Error, maxLines = 1)
        }
    }
}

/**
 * A 32 dp painted pill inside a 44 dp touch target (`Nocturne.MIN_TOUCH_DP`).
 * `selectable` + `Role.Tab` is what tells TalkBack which filter is active;
 * colour alone did not. `filter_<label>` doubles as the automation hook, matching
 * the nav rail's `nav_<TAB>`.
 */
@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val description = "filter_$label"
    Box(
        Modifier
            .height(Nocturne.MIN_TOUCH_DP.dp)
            .widthIn(min = Nocturne.MIN_TOUCH_DP.dp)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .height(32.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(
                    if (selected) Nocturne.Accent.copy(alpha = 0.22f) else Color.Transparent,
                )
                .border(
                    1.dp,
                    if (selected) Nocturne.Accent else Nocturne.Neutral700,
                    RoundedCornerShape(6.dp),
                )
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label,
                fontSize = 12.5.sp,
                color = if (selected) Nocturne.Accent200 else Nocturne.Neutral400,
            )
        }
    }
}
