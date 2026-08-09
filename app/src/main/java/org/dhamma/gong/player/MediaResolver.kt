package org.dhamma.gong.player

import android.content.Context
import android.net.Uri
import org.dhamma.gong.data.GongDatabase
import org.dhamma.gong.domain.PlayCommand
import org.dhamma.gong.domain.PlayKind

/**
 * Turns a [PlayCommand] into something ExoPlayer can open.
 *
 * Gongs are bundled in the APK (`assets/media/gongs/<stem>.mp3`). Doha comes
 * from `media_slots`, whose `uri` is a persisted SAF document URI for a
 * sideloaded pack — or, in debug builds only, one of the synthetic test tones
 * (design doc §07).
 */
class MediaResolver(
    private val context: Context,
    private val db: GongDatabase,
) {

    sealed interface Resolved {
        data class Ok(val uri: Uri, val displayName: String) : Resolved
        data class Missing(val displayName: String, val reason: String) : Resolved
    }

    suspend fun resolve(command: PlayCommand): Resolved = when (command.kind) {
        PlayKind.GONG, PlayKind.TEST_GONG -> gong(command.trackStem ?: DEFAULT_TRACK)
        PlayKind.DOHA, PlayKind.TEST_DOHA -> doha(command.dohaSlot)
        else -> Resolved.Missing(command.kind, "unknown play kind")
    }

    fun gong(stem: String): Resolved {
        val name = "$stem.mp3"
        val path = "$GONG_DIR/$name"
        return if (assetExists(path)) {
            Resolved.Ok(Uri.parse("asset:///$path"), name)
        } else {
            Resolved.Missing(name, "gong track not bundled")
        }
    }

    suspend fun doha(slot: Int?): Resolved {
        if (slot == null) return Resolved.Missing("doha", "no slot resolved")
        val row = db.mediaSlots().get(slot)
            ?: return Resolved.Missing("slot $slot", "slot not mapped")
        val uri = runCatching { Uri.parse(row.uri) }.getOrNull()
            ?: return Resolved.Missing(row.filename, "unreadable uri")
        return Resolved.Ok(uri, row.filename)
    }

    /** Track stems bundled in this build, for the sounds screen. */
    fun availableTracks(): List<String> = runCatching {
        context.assets.list(GONG_DIR).orEmpty()
            .filter { it.endsWith(".mp3") }
            .map { it.removeSuffix(".mp3") }
            .sorted()
    }.getOrDefault(listOf(DEFAULT_TRACK))

    private fun assetExists(path: String): Boolean = runCatching {
        context.assets.open(path).close()
        true
    }.getOrDefault(false)

    companion object {
        const val GONG_DIR = "media/gongs"
        const val DEFAULT_TRACK = "ting"

        /** Debug-only synthetic doha tones; absent from release builds. */
        const val DOHA_TEST_DIR = "media/doha-test"
    }
}
