package org.dhamma.gong.player

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * "Render this file once, and don't come back until it has finished."
 *
 * Split out of [PlayerEngine] so the queue and burst semantics — the parts that
 * carry the Pi daemon's guarantees — can be tested without an audio device.
 */
interface AudioSink {
    /**
     * Suspends until playback ends. Cancellation must stop the output.
     *
     * @param preferredDeviceId an `AudioDeviceInfo` id to render through, or
     *   null for whatever Android would pick. A device that has gone away
     *   between resolution and here must not fail the play — the fallback is
     *   the system default, because a gong from the wrong speaker beats no gong.
     */
    suspend fun play(uri: Uri, volume: Int, preferredDeviceId: Int? = null)

    /** Open the device early so the first strike is not eaten by link setup. */
    suspend fun warmUp() = Unit

    suspend fun release() = Unit
}

/** The real one: a single reused ExoPlayer on the main looper. */
class ExoAudioSink(private val context: Context) : AudioSink {

    private var player: ExoPlayer? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    override suspend fun warmUp() {
        ensurePlayer()
    }

    override suspend fun play(uri: Uri, volume: Int, preferredDeviceId: Int?) {
        val p = ensurePlayer()
        withContext(Dispatchers.Main.immediate) {
            p.setPreferredAudioDevice(findDevice(preferredDeviceId))
            p.volume = volume.coerceIn(0, 100) / 100f
            p.setMediaItem(MediaItem.fromUri(uri))
            p.repeatMode = Player.REPEAT_MODE_OFF
            p.prepare()
            p.play()
        }
        try {
            // The wedge timeout lives in PlayerEngine, which knows whether it
            // is capping a strike or a 45-minute chant; this layer only waits.
            awaitEnded(p)
        } catch (e: Throwable) {
            withContext(Dispatchers.Main.immediate + kotlinx.coroutines.NonCancellable) { p.stop() }
            throw e
        }
    }

    override suspend fun release() = withContext(Dispatchers.Main.immediate) {
        player?.release()
        player = null
    }

    private suspend fun ensurePlayer(): ExoPlayer = withContext(Dispatchers.Main.immediate) {
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

    /**
     * Look the id up again at play time rather than holding the
     * `AudioDeviceInfo` from resolution.
     *
     * Between the scheduler resolving a route and the burst starting, a
     * Bluetooth amp can drop or a USB DAC can be unplugged. Passing a stale
     * device to ExoPlayer is how a gong goes silent; passing null puts it out
     * of the built-in speaker instead, which is the documented fallback.
     */
    private fun findDevice(deviceId: Int?): AudioDeviceInfo? {
        val id = deviceId ?: return null
        val am = context.getSystemService(AudioManager::class.java) ?: return null
        return runCatching {
            am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull { it.id == id }
        }.getOrNull()
    }

    private suspend fun awaitEnded(p: ExoPlayer) = withContext(Dispatchers.Main.immediate) {
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
            cont.invokeOnCancellation {
                // Cancellation is delivered on whichever thread cancelled —
                // the engine's wedge timer or a preempting submit — and
                // ExoPlayer asserts its own thread. A throw inside this
                // handler is fatal by kotlinx contract: exactly the crash
                // that took the whole service down mid-doha and swallowed the
                // queued 21:00 gong (dropbox 2026-08-15 21:03:17). The player
                // may only be touched from main, so post the removal.
                mainHandler.post { p.removeListener(listener) }
            }
        }
    }
}
