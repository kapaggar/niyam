package org.dhamma.gong.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * "Render this file once, and don't come back until it has finished."
 *
 * Split out of [PlayerEngine] so the queue and burst semantics — the parts that
 * carry the Pi daemon's guarantees — can be tested without an audio device.
 */
interface AudioSink {
    /** Suspends until playback ends. Cancellation must stop the output. */
    suspend fun play(uri: Uri, volume: Int)

    /** Open the device early so the first strike is not eaten by link setup. */
    suspend fun warmUp() = Unit

    suspend fun release() = Unit
}

/** The real one: a single reused ExoPlayer on the main looper. */
class ExoAudioSink(private val context: Context) : AudioSink {

    private var player: ExoPlayer? = null

    override suspend fun warmUp() {
        ensurePlayer()
    }

    override suspend fun play(uri: Uri, volume: Int) {
        val p = ensurePlayer()
        withContext(Dispatchers.Main) {
            p.volume = volume.coerceIn(0, 100) / 100f
            p.setMediaItem(MediaItem.fromUri(uri))
            p.repeatMode = Player.REPEAT_MODE_OFF
            p.prepare()
            p.play()
        }
        try {
            // A wedged decode must not block the queue for the rest of the day.
            val finished = withTimeoutOrNull(PLAY_TIMEOUT_MS) { awaitEnded(p) }
            if (finished == null) {
                withContext(Dispatchers.Main) { p.stop() }
                error("playback did not finish within ${PLAY_TIMEOUT_MS}ms")
            }
        } catch (e: Throwable) {
            withContext(Dispatchers.Main + kotlinx.coroutines.NonCancellable) { p.stop() }
            throw e
        }
    }

    override suspend fun release() = withContext(Dispatchers.Main) {
        player?.release()
        player = null
    }

    private suspend fun ensurePlayer(): ExoPlayer = withContext(Dispatchers.Main) {
        player ?: ExoPlayer.Builder(context)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    // USAGE_MEDIA, *not* USAGE_ALARM: many OEMs force alarm
                    // usage to the built-in speaker even with Bluetooth
                    // connected — exactly the failure we cannot have
                    // (design doc §06).
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_SONIFICATION)
                    .build(),
                /* handleAudioFocus = */ false,
            )
            .setHandleAudioBecomingNoisy(false)
            .build()
            .also { player = it }
    }

    private suspend fun awaitEnded(p: ExoPlayer) = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->
            if (p.playbackState == Player.STATE_ENDED) {
                cont.resume(Unit)
                return@suspendCancellableCoroutine
            }
            val listener = object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_ENDED && cont.isActive) {
                        p.removeListener(this)
                        cont.resume(Unit)
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    if (cont.isActive) {
                        p.removeListener(this)
                        cont.cancel(error)
                    }
                }
            }
            p.addListener(listener)
            cont.invokeOnCancellation { p.removeListener(listener) }
        }
    }

    private companion object {
        const val PLAY_TIMEOUT_MS = 10 * 60_000L
    }
}
