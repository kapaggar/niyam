package org.dhamma.gong.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.dhamma.gong.domain.PlayKind
import org.dhamma.gong.domain.PlayResult

/**
 * `play_log`, read-only, so no PIN.
 *
 * **Timestamps are UTC** and the detail column carries the local scheduled
 * instant with offset, so a missed fire is diagnosable without guessing the
 * device's timezone at the time (design handoff §4).
 */
@Composable
fun LogsScreen(vm: AppViewModel) {
    val rows by vm.logs.collectAsState()
    var filter by remember { mutableStateOf(Filter.ALL) }

    val visible = rows.filter { row ->
        when (filter) {
            Filter.ALL -> true
            Filter.GONG -> row.kind == PlayKind.GONG || row.kind == PlayKind.TEST_GONG
            Filter.DOHA -> row.kind == PlayKind.DOHA || row.kind == PlayKind.TEST_DOHA
            Filter.MISSED -> row.result == PlayResult.MISSED
            Filter.ERROR -> row.result == PlayResult.ERROR
        }
    }

    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenTitle("Logs", "Timestamps are UTC. ${rows.size} recent entries.")

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (f in Filter.entries) {
                FilterChip(f.label, f == filter) { filter = f }
            }
        }

        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Head("When (UTC)", 168.dp)
            Head("What", 96.dp)
            Head("File", 150.dp)
            Head("×", 46.dp)
            Head("Result", 110.dp)
            Text("DETAIL", fontSize = 11.sp, letterSpacing = 1.1.sp, color = Nocturne.Neutral500)
        }
        Hairline()

        LazyColumn(Modifier.fillMaxSize()) {
            items(visible, key = { it.id }) { row ->
                Column {
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Mono(row.tsUtc, 168.dp, Nocturne.Neutral400)
                        Mono(row.kind, 96.dp, Nocturne.Neutral300)
                        Mono(row.file, 150.dp, Nocturne.Neutral400)
                        Mono(row.repeats.toString(), 46.dp, Nocturne.Neutral400)
                        Text(
                            row.result,
                            fontSize = 12.5.sp,
                            fontFamily = Nocturne.Mono,
                            color = resultColor(row.result),
                            modifier = Modifier.width(110.dp),
                        )
                        Text(
                            row.detail.ifBlank { "—" },
                            fontSize = 12.5.sp,
                            fontFamily = Nocturne.Mono,
                            color = Nocturne.Neutral500,
                        )
                    }
                    Hairline()
                }
            }
        }

        if (visible.isEmpty()) {
            Text("Nothing logged yet.", fontSize = 13.5.sp, color = Nocturne.Neutral500)
        }
    }
}

private enum class Filter(val label: String) {
    ALL("all"), GONG("gong"), DOHA("doha"), MISSED("missed"), ERROR("error")
}

/** Design handoff §4 result colouring. */
private fun resultColor(result: String): Color = when (result) {
    PlayResult.OK -> Nocturne.Neutral300
    PlayResult.MISSED, PlayResult.ERROR -> Nocturne.Error
    PlayResult.STOPPED, PlayResult.SKIPPED_CLOCK -> Nocturne.Warning
    else -> Nocturne.Neutral400
}

@Composable
private fun Head(text: String, width: androidx.compose.ui.unit.Dp) {
    Text(
        text.uppercase(),
        fontSize = 11.sp,
        letterSpacing = 1.1.sp,
        color = Nocturne.Neutral500,
        modifier = Modifier.width(width),
    )
}

@Composable
private fun Mono(text: String, width: androidx.compose.ui.unit.Dp, color: Color) {
    Text(
        text,
        fontSize = 12.5.sp,
        fontFamily = Nocturne.Mono,
        color = color,
        maxLines = 1,
        modifier = Modifier.width(width),
    )
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
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
