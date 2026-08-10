package org.dhamma.gong.assets

import android.net.Uri
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.dhamma.gong.data.GongDatabase
import org.dhamma.gong.data.MediaSlotEntity
import org.dhamma.gong.data.MediaSlotSource
import org.dhamma.gong.domain.DohaPackMapper

/**
 * Bridges the download pipeline into playback: whenever an asset becomes
 * [AudioAssetManager.TrackState.Ready], its `Dnn` filename prefix is parsed
 * to a doha slot and — only if that slot is *empty* — a `media_slots` row is
 * written with source [MediaSlotSource.DOWNLOADED], so `MediaResolver` can
 * play the file with no further wiring.
 *
 * Precedence (design: manual > bundled > folder-pack auto > downloaded):
 * this class NEVER replaces an existing row of any source. The one allowed
 * displacement — a staff-chosen folder pack claiming a `downloaded` slot on
 * rescan — happens in [DohaPackMapper.classify], not here.
 */
class DownloadedSlotRegistrar(
    private val db: GongDatabase,
    private val manager: AudioAssetManager,
    private val scope: CoroutineScope,
) {

    /**
     * Starts collecting. The state flow replays its current value, so assets
     * already ready at start are registered immediately; afterwards only a
     * change in the *set* of ready files re-runs the check (download progress
     * ticks never touch the database).
     */
    fun start(): Job = scope.launch {
        manager.states
            .map { states -> readyFiles(states) }
            .distinctUntilChanged()
            .collect { ready -> register(ready) }
    }

    private fun readyFiles(states: Map<String, AudioAssetManager.TrackState>): Map<String, File> =
        buildMap {
            for ((id, state) in states) {
                if (state is AudioAssetManager.TrackState.Ready) put(id, state.file)
            }
        }

    private suspend fun register(ready: Map<String, File>) {
        if (ready.isEmpty()) return
        val byId = manager.catalog.associateBy { it.id }
        for ((id, file) in ready.entries.sortedBy { it.key }) {
            val filename = byId[id]?.filename ?: continue
            val slot = DohaPackMapper.parseSlot(filename) ?: continue
            if (db.mediaSlots().get(slot) != null) continue // occupied = protected
            db.mediaSlots().put(
                MediaSlotEntity(
                    slot = slot,
                    uri = Uri.fromFile(file).toString(),
                    filename = filename,
                    source = MediaSlotSource.DOWNLOADED,
                ),
            )
        }
    }
}
