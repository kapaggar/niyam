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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
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
import org.dhamma.gong.assets.AudioAssetManager
import org.dhamma.gong.data.MediaSlotSource
import org.dhamma.gong.domain.AudioAsset
import org.dhamma.gong.domain.DohaPackMapper
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import org.dhamma.gong.domain.DohaSlots
import org.dhamma.gong.domain.GongTracks
import org.dhamma.gong.domain.ScheduleMaterializer

/**
 * Sounds — what rings, how loud, when the doha plays, and where the doha
 * recordings come from.
 *
 * The eleven-row slot table that used to sit here is gone. It exposed the
 * internals of `DohaSlots.legacyModular` — which slot serves which course day —
 * to people who have no decision to make about it: the mapping is the verified
 * PHP port and is not theirs to change. What staff actually do is point the
 * appliance at a folder or download the tracks, and the folder count on the
 * card already says whether that worked.
 *
 * Everything writes straight through to the settings rows the scheduler and
 * player already read, so there is no save button and no second copy of the
 * state to fall out of sync.
 */
@Composable
fun DohaMediaScreen(vm: AppViewModel) {
    val pack by vm.dohaPack.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val slots by vm.mediaSlots.collectAsStateWithLifecycle()
    val treeUri by vm.dohaTreeUri.collectAsStateWithLifecycle()
    val downloadStates by vm.downloadStates.collectAsStateWithLifecycle()

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri -> vm.onDohaFolderPicked(uri) }

    // Re-read the folder on every entry: an SD card can be pulled between visits,
    // and stale rows that claim to be verified are the failure this screen exists
    // to prevent. The download index gets the same treatment — a chip claiming
    // "Ready" for a file someone deleted is the same lie.
    LaunchedEffect(Unit) {
        vm.rescanDohaFolder(announce = false)
        vm.rescanDownloads()
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 32.dp, end = 32.dp, top = 26.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        ScreenTitle(
            "Sounds",
            "What rings, how loud, and when the morning doha plays — plus where " +
                "the doha recordings come from.",
        )

        GongCard(vm, settings)
        DohaScheduleCard(vm, settings)

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

        // -------------------------------------------------------- downloads
        DownloadsCard(
            catalog = vm.downloadCatalog,
            states = downloadStates,
            isMetered = vm::downloadsMetered,
            onDownload = vm::downloadDoha,
            onDownloadAll = vm::downloadAllDohas,
            onScanStorage = vm::scanStorageForMedia,
        )
    }
}

// ---------------------------------------------------------------- gong

/**
 * The appliance-wide gong defaults.
 *
 * Everything here is the value a schedule row inherits when its own field is
 * "—". Changing the gap or the track here moves every row that has not been
 * overridden, which is the point: staff should be able to quieten the whole
 * course from one place, not edit forty cells.
 */
@Composable
private fun GongCard(vm: AppViewModel, settings: Map<String, String>) {
    val track = settings["gong_track"].orEmpty().ifBlank { GongTracks.SINGLE }
    val gap = settings["gong_gap_seconds"]?.toIntOrNull() ?: 4
    val volume = settings["gong_volume"]?.toIntOrNull() ?: 90

    SurfaceCard(Modifier.fillMaxWidth()) {
        Eyebrow("Gong")
        Spacer(Modifier.height(4.dp))
        Text(
            "Defaults for every schedule row that has not overridden them.",
            fontSize = 12.5.sp,
            color = Nocturne.Neutral500,
        )
        Spacer(Modifier.height(14.dp))

        ChipRow("Track") {
            for (stem in listOf(GongTracks.SINGLE, GongTracks.SIKKIM)) {
                ChoiceChip(
                    label = GongTracks.label(stem),
                    selected = track.equals(stem, ignoreCase = true),
                    description = "Use the ${GongTracks.label(stem)} recording",
                ) { vm.setSetting("gong_track", stem, "Track set to ${GongTracks.label(stem)}") }
                Spacer(Modifier.width(8.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            // The sikkim recording contains three hits, so "repeats" and
            // "plays" are not the same number. Staff who do not know that will
            // read a 6-repeat row as six files.
            "The sikkim gong rings three times per play, so a 6-strike burst " +
                "plays that file twice. Strike counts always mean audible hits.",
            fontSize = 12.5.sp,
            color = Nocturne.Neutral500,
        )

        Spacer(Modifier.height(14.dp))
        Stepper("Gap", gap, min = 0, max = 60, unit = "s", labelWidth = 96) {
            vm.setSetting("gong_gap_seconds", it.toString(), "Gap set to ${it}s")
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Silence after each strike finishes — a long recording still gets " +
                "its full gap, so bursts never overlap.",
            fontSize = 12.5.sp,
            color = Nocturne.Neutral500,
        )

        Spacer(Modifier.height(14.dp))
        Stepper("Volume", volume, min = 0, max = 100, unit = "%", step = 5, labelWidth = 96) {
            vm.setSetting("gong_volume", it.toString(), "Gong volume ${it}%")
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "App-level gain. The tablet's own media volume multiplies this, so a " +
                "quiet hall is worth checking in Android's volume too.",
            fontSize = 12.5.sp,
            color = Nocturne.Neutral500,
        )

        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryButton("Test gong") { vm.testGong() }
            OutlineButton("Stop") { vm.stop() }
        }
    }
}

// ---------------------------------------------------------------- doha timing

/**
 * When the morning doha plays, and which one plays between courses.
 *
 * The between-courses choice is the interesting one. `random` is deterministic
 * per date (see `DohaSlots.randomSlotFor`) — the same day always resolves to
 * the same recording, and eleven consecutive days walk the whole set without
 * repeating.
 */
@Composable
private fun DohaScheduleCard(vm: AppViewModel, settings: Map<String, String>) {
    val stored = settings["doha_time"].orEmpty().ifBlank { "06:37" }
    val volume = settings["doha_volume"]?.toIntOrNull() ?: 75
    val noCourse = settings["no_course_doha"].orEmpty().ifBlank { "random" }
    val fixedSlot = noCourse.removePrefix("slot:").toIntOrNull()?.takeIf { it in DohaSlots.SLOTS }

    SurfaceCard(Modifier.fillMaxWidth()) {
        Eyebrow("Morning doha")
        Spacer(Modifier.height(14.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "Time",
                fontSize = 12.5.sp,
                color = Nocturne.Neutral500,
                modifier = Modifier.width(96.dp),
            )
            var typed by remember(stored) { mutableStateOf(stored) }
            Field(
                value = typed,
                onValueChange = { typed = it },
                placeholder = "06:37",
                modifier = Modifier
                    .width(120.dp)
                    .semantics { contentDescription = "Doha time, 24 hour HH:MM" },
            )
            PrimaryButton("Save") {
                // Validate through the scheduler's own parser, so the screen
                // can never accept a string the materializer would silently
                // fall back on.
                val parsed = ScheduleMaterializer.parseHhMm(typed.trim())
                if (parsed == null) {
                    vm.toast("Doha time must be 24-hour HH:MM, e.g. 06:37")
                } else {
                    vm.setSetting("doha_time", typed.trim(), "Doha time set to ${typed.trim()}")
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "24-hour. Fires once a day and obeys the same 120 s grace as a gong — " +
                "a doha missed by more than that is logged, never played late.",
            fontSize = 12.5.sp,
            color = Nocturne.Neutral500,
        )

        Spacer(Modifier.height(14.dp))
        ChipRow("Between courses") {
            ChoiceChip("rotate daily", noCourse == "random", "Rotate the doha each day") {
                vm.setSetting("no_course_doha", "random", "Doha rotates daily between courses")
            }
            Spacer(Modifier.width(8.dp))
            ChoiceChip("fixed", fixedSlot != null, "Always play one chosen doha") {
                vm.setSetting("no_course_doha", "slot:1", "Between courses: always D01")
            }
            Spacer(Modifier.width(8.dp))
            ChoiceChip("off", noCourse == "off", "No doha between courses") {
                vm.setSetting("no_course_doha", "off", "No doha between courses")
            }
        }
        if (fixedSlot != null) {
            Spacer(Modifier.height(10.dp))
            Stepper(
                "Which",
                fixedSlot,
                min = DohaSlots.SLOTS.first,
                max = DohaSlots.SLOTS.last,
                unit = "",
                labelWidth = 96,
            ) { vm.setSetting("no_course_doha", "slot:$it", "Between courses: always D%02d".format(it)) }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            when {
                noCourse == "off" -> "Nothing plays between courses. Gongs are unaffected."
                fixedSlot != null -> "Always D%02d until a course starts.".format(fixedSlot)
                else ->
                    "A different doha each day, but the same one every time a given " +
                        "date is resolved. Eleven days covers the whole set with no repeat."
            },
            fontSize = 12.5.sp,
            color = Nocturne.Neutral500,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "During a course the day decides the doha and this setting is ignored.",
            fontSize = 12.5.sp,
            color = Nocturne.Neutral500,
        )

        Spacer(Modifier.height(14.dp))
        Stepper("Volume", volume, min = 0, max = 100, unit = "%", step = 5, labelWidth = 96) {
            vm.setSetting("doha_volume", it.toString(), "Doha volume ${it}%")
        }

        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryButton("Test doha") { vm.testDoha() }
            OutlineButton("Stop") { vm.stop() }
        }
    }
}

// ---------------------------------------------------------------- pieces




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

// ---------------------------------------------------------------- downloads

/** What a metered-network confirm dialog is asking permission for. */
private sealed interface MeteredConfirm {
    data object All : MeteredConfirm
    data class One(val id: String) : MeteredConfirm
}

/**
 * The on-demand download pipeline's face. Every row is one catalog asset;
 * the chip is the pipeline's state verbatim — except that error text is
 * already plain words by contract, and nothing else (paths, hashes, headers)
 * is ever rendered here.
 */
@Composable
private fun DownloadsCard(
    catalog: List<AudioAsset>,
    states: Map<String, AudioAssetManager.TrackState>,
    isMetered: () -> Boolean,
    onDownload: (String) -> Unit,
    onDownloadAll: (Boolean) -> Unit,
    onScanStorage: () -> Unit,
) {
    val rows = remember(catalog) { catalog.sortedBy { it.filename } }
    val noKey = states.values.any { it is AudioAssetManager.TrackState.NoKey }
    // First use = nothing downloaded, nothing in flight, nothing failed.
    val firstUse = !noKey && rows.isNotEmpty() && rows.all {
        stateOf(states, it) is AudioAssetManager.TrackState.NotDownloaded
    }
    var confirm by remember { mutableStateOf<MeteredConfirm?>(null) }

    SurfaceCard {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Eyebrow("Downloads")
                Spacer(Modifier.height(6.dp))
                Text(
                    "Fetch the doha recordings straight to this device — no pack folder needed.",
                    fontSize = 12.5.sp,
                    color = Nocturne.Neutral400,
                )
            }
            PackButton(
                "Download all dohas (~470 MB)",
                accent = true,
                enabled = !noKey,
                onClick = {
                    if (isMetered()) confirm = MeteredConfirm.All else onDownloadAll(false)
                },
            )
        }

        if (noKey) {
            Spacer(Modifier.height(12.dp))
            Text(
                "This build has no media key — downloads are disabled.",
                fontSize = 13.sp,
                color = Nocturne.Warning,
            )
        } else if (firstUse) {
            Spacer(Modifier.height(12.dp))
            Text(
                "These recordings are large — about 45 MB each, ~470 MB for all " +
                    "eleven. WiFi is recommended. Downloaded files stay on this device.",
                fontSize = 12.5.sp,
                color = Nocturne.Neutral500,
            )
        }

        Spacer(Modifier.height(10.dp))
        Hairline()
        for (asset in rows) {
            DownloadRow(
                asset = asset,
                state = if (noKey) null else stateOf(states, asset),
                onDownload = {
                    if (isMetered()) confirm = MeteredConfirm.One(asset.id)
                    else onDownload(asset.id)
                },
            )
            Hairline()
        }

        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            PackButton("Scan storage for existing media", onClick = onScanStorage)
            Text(
                "Looks for already-downloaded doha files in common folders on this device.",
                fontSize = 12.5.sp,
                color = Nocturne.Neutral500,
                modifier = Modifier.weight(1f),
            )
        }
    }

    confirm?.let { asking ->
        MeteredConfirmDialog(
            sizeLabel = when (asking) {
                MeteredConfirm.All -> "about 470 MB"
                is MeteredConfirm.One -> "about 45 MB"
            },
            onConfirm = {
                when (asking) {
                    MeteredConfirm.All -> onDownloadAll(true)
                    is MeteredConfirm.One -> onDownload(asking.id)
                }
                confirm = null
            },
            onDismiss = { confirm = null },
        )
    }
}

/** A missing entry means the manager has not looked yet — same as not downloaded. */
private fun stateOf(
    states: Map<String, AudioAssetManager.TrackState>,
    asset: AudioAsset,
): AudioAssetManager.TrackState =
    states[asset.id] ?: AudioAssetManager.TrackState.NotDownloaded

@Composable
private fun DownloadRow(
    asset: AudioAsset,
    state: AudioAssetManager.TrackState?,
    onDownload: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = Nocturne.MIN_TOUCH_DP.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            slotTag(asset.filename),
            fontSize = 14.sp,
            fontFamily = Nocturne.Mono,
            fontWeight = FontWeight.Medium,
            color = Nocturne.Text,
            modifier = Modifier.width(56.dp),
        )
        Text(
            dohaTitle(asset.filename),
            fontSize = 13.5.sp,
            color = Nocturne.Neutral300,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        Box(Modifier.width(200.dp)) { DownloadStateCell(state) }
        when (state) {
            is AudioAssetManager.TrackState.NotDownloaded ->
                PackButton("Download", modifier = Modifier.width(104.dp), onClick = onDownload)
            is AudioAssetManager.TrackState.Error ->
                PackButton("Retry", modifier = Modifier.width(104.dp), onClick = onDownload)
            else -> Spacer(Modifier.width(104.dp))
        }
    }
}

/** null state = the build has no key; the row is simply unavailable. */
@Composable
private fun DownloadStateCell(state: AudioAssetManager.TrackState?) {
    when (state) {
        null -> Text("Unavailable", fontSize = 12.5.sp, color = Nocturne.Neutral500)
        is AudioAssetManager.TrackState.NotDownloaded -> StateLabel("Not downloaded", Nocturne.Neutral600)
        is AudioAssetManager.TrackState.Preparing -> StateLabel("Preparing…", Nocturne.Warning)
        is AudioAssetManager.TrackState.Ready -> StateLabel("Ready", Nocturne.Ok, textColor = Nocturne.Ok)
        is AudioAssetManager.TrackState.NoKey ->
            Text("Unavailable", fontSize = 12.5.sp, color = Nocturne.Neutral500)
        is AudioAssetManager.TrackState.Error -> Text(
            state.message,
            fontSize = 12.5.sp,
            color = Nocturne.Error,
            modifier = Modifier.padding(vertical = 6.dp),
        )
        is AudioAssetManager.TrackState.Downloading -> Column(Modifier.padding(vertical = 6.dp)) {
            Text(
                "${mb(state.received)} / ${mb(state.total)} MB",
                fontSize = 12.5.sp,
                fontFamily = Nocturne.Mono,
                color = Nocturne.Neutral300,
            )
            if (state.total > 0) {
                Spacer(Modifier.height(5.dp))
                LinearProgressIndicator(
                    progress = {
                        (state.received.toFloat() / state.total).coerceIn(0f, 1f)
                    },
                    color = Nocturne.Accent,
                    trackColor = Nocturne.Neutral800,
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                )
            }
        }
    }
}

@Composable
private fun StateLabel(text: String, dot: Color, textColor: Color = Nocturne.Neutral400) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Dot(dot)
        Text(text, fontSize = 12.5.sp, color = textColor)
    }
}

@Composable
private fun MeteredConfirmDialog(
    sizeLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Nocturne.SurfaceHigh,
        text = {
            Text(
                "You are on mobile data. Download $sizeLabel now?",
                fontSize = 15.sp,
                color = Nocturne.Text,
            )
        },
        confirmButton = { PackButton("Download", accent = true, onClick = onConfirm) },
        dismissButton = { PackButton("Cancel", onClick = onDismiss) },
    )
}

/** Whole decimal megabytes, no decimals — "12 / 43 MB" territory. */
private fun mb(bytes: Long): String = (bytes / 1_000_000).toString()

/** "D06_0632_Doha-Samatha-_NA_NA.mp3" → "D06". */
private fun slotTag(filename: String): String = filename.substringBefore('_')

/** "D06_0632_Doha-Samatha-_NA_NA.mp3" → "Samatha"; "D01_…Doha-Hin-1…" → "Hin 1". */
private fun dohaTitle(filename: String): String {
    val mid = filename.removeSuffix(".mp3").split('_').getOrNull(2) ?: return filename
    return mid.removePrefix("Doha-").trim('-').replace('-', ' ').ifBlank { filename }
}
