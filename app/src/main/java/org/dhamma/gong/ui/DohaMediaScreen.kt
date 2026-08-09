package org.dhamma.gong.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.dhamma.gong.data.MediaSlotSource
import org.dhamma.gong.domain.DohaPackMapper
import org.dhamma.gong.domain.DohaSlots

/**
 * Sounds → **doha media** (design doc §08, spec 2026-08-09).
 *
 * This is only the slot-mapping part of Sounds: track choice, volumes, burst
 * gap, doha time and no-course mode are still unbuilt, and the screen says so
 * rather than implying a finished settings page.
 *
 * **No day column, deliberately.** `DohaSlots.legacyModular` maps a course day
 * to a slot through modular cycles, so one slot serves several days. A "day it
 * serves" column would state something false, so rows are labelled by slot only.
 */
@Composable
fun DohaMediaScreen(vm: AppViewModel) {
    val pack by vm.dohaPack.collectAsStateWithLifecycle()
    val slots by vm.mediaSlots.collectAsStateWithLifecycle()
    val treeUri by vm.dohaTreeUri.collectAsStateWithLifecycle()

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri -> vm.onDohaFolderPicked(uri) }

    // Re-read the folder on every entry: an SD card can be pulled between visits,
    // and stale rows that claim to be verified are the failure this screen exists
    // to prevent.
    LaunchedEffect(Unit) { vm.rescanDohaFolder(announce = false) }

    val bySlot = remember(slots) { slots.associateBy { it.slot } }
    var expanded by remember { mutableStateOf<Int?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 32.dp, end = 32.dp, top = 26.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        ScreenTitle(
            "Doha media",
            "Point the appliance at a folder of D01…D11 recordings. " +
                "Track choice, volumes and doha time are not built yet.",
        )

        if (pack.banner != null) {
            BannerCard(
                text = pack.banner!!,
                onRepick = { picker.launch(null) },
                onDismiss = vm::dismissDohaBanner,
            )
        }

        FolderCard(
            treeUri = treeUri,
            pack = pack,
            mappedCount = slots.size,
            onPick = { picker.launch(null) },
            onRescan = { vm.rescanDohaFolder() },
        )

        // ------------------------------------------------------------ slots
        SurfaceCard {
            Eyebrow("Doha slots")
            Spacer(Modifier.height(10.dp))
            SlotHeaderRow()
            Hairline()
            for (slot in DohaSlots.SLOTS) {
                val row = bySlot[slot]
                SlotRow(
                    slot = slot,
                    filename = row?.filename,
                    source = row?.source,
                    state = slotState(
                        mapped = row != null,
                        verified = row?.verifiedAt != null,
                        unreadable = slot in pack.unreadable,
                    ),
                    canAssign = pack.files.isNotEmpty(),
                    expanded = expanded == slot,
                    onToggle = { expanded = if (expanded == slot) null else slot },
                    onClear = {
                        expanded = null
                        vm.clearDohaSlot(slot)
                    },
                )
                if (expanded == slot) {
                    FilePickList(
                        files = pack.files,
                        onPick = {
                            expanded = null
                            vm.assignDohaSlot(slot, it)
                        },
                    )
                }
                Hairline()
            }
        }

        if (pack.skipped.isNotEmpty()) {
            ReportCard("Skipped — slot held by staff or bundled media", Nocturne.Warning) {
                for (s in pack.skipped) {
                    ReportLine(
                        "Slot %02d".format(s.slot),
                        "${s.file.name} — not applied, slot is ${s.heldBy}",
                    )
                }
            }
        }

        if (pack.conflicts.isNotEmpty()) {
            ReportCard("Conflicts — nothing was auto-assigned", Nocturne.Error) {
                for (c in pack.conflicts) {
                    ReportLine(
                        "Slot %02d".format(c.slot),
                        c.files.joinToString("  ·  ") { it.name },
                    )
                }
            }
        }

        if (pack.unassigned.isNotEmpty()) {
            ReportCard("Unassigned files", Nocturne.Neutral400) {
                for (f in pack.unassigned) {
                    ReportLine("—", f.name)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- pieces

private enum class SlotState(val label: String, val color: Color) {
    VERIFIED("verified", Nocturne.Ok),
    UNREADABLE("unreadable", Nocturne.Error),
    UNVERIFIED("unverified", Nocturne.Warning),
    EMPTY("empty", Nocturne.Neutral600),
}

/** "Mapped" is not the same claim as "will play" — keep the two apart. */
private fun slotState(mapped: Boolean, verified: Boolean, unreadable: Boolean): SlotState = when {
    !mapped -> SlotState.EMPTY
    unreadable -> SlotState.UNREADABLE
    verified -> SlotState.VERIFIED
    else -> SlotState.UNVERIFIED
}

@Composable
private fun BannerCard(text: String, onRepick: () -> Unit, onDismiss: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Nocturne.Error.copy(alpha = 0.10f))
            .border(1.dp, Nocturne.Error.copy(alpha = 0.40f), RoundedCornerShape(10.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(text, fontSize = 13.5.sp, color = Nocturne.Text, modifier = Modifier.weight(1f))
        PackButton("Re-pick folder", onClick = onRepick)
        PackButton("Dismiss", onClick = onDismiss)
    }
}

@Composable
private fun FolderCard(
    treeUri: String,
    pack: AppViewModel.DohaPack,
    mappedCount: Int,
    onPick: () -> Unit,
    onRescan: () -> Unit,
) {
    SurfaceCard {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Eyebrow("Pack folder")
                Spacer(Modifier.height(6.dp))
                Text(
                    if (treeUri.isBlank()) "No folder chosen" else pack.folderLabel,
                    fontSize = 15.sp,
                    fontFamily = Nocturne.Mono,
                    color = if (treeUri.isBlank()) Nocturne.Neutral500 else Nocturne.Text,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "$mappedCount / 11 slots mapped",
                    fontSize = 12.5.sp,
                    color = Nocturne.Neutral400,
                )
            }
            if (pack.viaDohaChild) Tag("VIA doha/", Nocturne.Accent300)
            PackButton(
                if (treeUri.isBlank()) "Choose folder" else "Change folder",
                accent = true,
                onClick = onPick,
            )
            PackButton("Rescan", onClick = onRescan)
        }

        // The wrong-parent case must be loud, not a silent no-op.
        if (treeUri.isNotBlank() && pack.scannedOnce && pack.files.isEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(
                "No .mp3 files named D01…D11 were found here. Pick the folder that " +
                    "directly contains the recordings (a single doha/ subfolder is " +
                    "also accepted) — not a folder above it.",
                fontSize = 13.sp,
                color = Nocturne.Warning,
            )
        }
    }
}

@Composable
private fun SlotHeaderRow() {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Eyebrow("Slot", Modifier.width(78.dp))
        Eyebrow("File", Modifier.weight(1f))
        Eyebrow("Source", Modifier.width(92.dp))
        Eyebrow("State", Modifier.width(120.dp))
        Spacer(Modifier.width(196.dp))
    }
}

@Composable
private fun SlotRow(
    slot: Int,
    filename: String?,
    source: String?,
    state: SlotState,
    canAssign: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
    onClear: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = Nocturne.MIN_TOUCH_DP.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "Slot %02d".format(slot),
            fontSize = 14.sp,
            fontFamily = Nocturne.Mono,
            fontWeight = FontWeight.Medium,
            color = Nocturne.Text,
            modifier = Modifier.width(70.dp),
        )
        Text(
            filename ?: "—",
            fontSize = 13.5.sp,
            color = if (filename == null) Nocturne.Neutral600 else Nocturne.Neutral300,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        Box(Modifier.width(84.dp)) {
            if (source != null) Tag(source.uppercase(), sourceColor(source))
        }
        Row(
            Modifier.width(112.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Dot(state.color)
            Text(state.label, fontSize = 12.5.sp, color = Nocturne.Neutral400)
        }
        PackButton(
            if (expanded) "Cancel" else "Reassign",
            enabled = canAssign || expanded,
            modifier = Modifier.width(104.dp),
            onClick = onToggle,
        )
        PackButton(
            "Clear",
            enabled = filename != null,
            color = Nocturne.Error,
            modifier = Modifier.width(84.dp),
            onClick = onClear,
        )
    }
}

private fun sourceColor(source: String): Color = when (source) {
    MediaSlotSource.MANUAL -> Nocturne.Accent300
    MediaSlotSource.BUNDLED -> Nocturne.Neutral400
    else -> Nocturne.Ok
}

@Composable
private fun FilePickList(
    files: List<DohaPackMapper.ScannedFile>,
    onPick: (DohaPackMapper.ScannedFile) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 70.dp, bottom = 8.dp),
    ) {
        Text(
            "Assigning by hand marks the slot manual — a rescan will not overwrite it.",
            fontSize = 12.5.sp,
            color = Nocturne.Neutral500,
            modifier = Modifier.padding(vertical = 6.dp),
        )
        for (f in files) {
            Text(
                f.name,
                fontSize = 13.5.sp,
                color = Nocturne.Accent200,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Nocturne.MIN_TOUCH_DP.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(role = Role.Button) { onPick(f) }
                    .padding(horizontal = 10.dp, vertical = 13.dp),
            )
        }
    }
}

@Composable
private fun ReportCard(
    title: String,
    accent: Color,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    SurfaceCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Dot(accent)
            Spacer(Modifier.width(8.dp))
            Eyebrow(title)
        }
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun ReportLine(lead: String, detail: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            lead,
            fontSize = 13.sp,
            fontFamily = Nocturne.Mono,
            color = Nocturne.Neutral400,
            modifier = Modifier.width(70.dp),
        )
        Text(detail, fontSize = 13.sp, color = Nocturne.Neutral300, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun PackButton(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accent: Boolean = false,
    color: Color = Nocturne.Neutral300,
    onClick: () -> Unit,
) {
    val tint = if (accent) Nocturne.Accent200 else color
    Box(
        modifier
            .height(Nocturne.MIN_TOUCH_DP.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (accent) Nocturne.Accent700.copy(alpha = 0.45f) else Color.Transparent)
            .border(
                1.dp,
                if (accent) Nocturne.Accent.copy(alpha = 0.55f) else Nocturne.Neutral700,
                RoundedCornerShape(8.dp),
            )
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontSize = 13.5.sp,
            color = if (enabled) tint else Nocturne.Neutral600,
        )
    }
}
