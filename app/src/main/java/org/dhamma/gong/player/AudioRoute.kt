package org.dhamma.gong.player

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager

/**
 * The route abstraction from design doc §06. Adding a wired amp in v1.1 is a
 * new case here, not a rewrite of the player.
 */
sealed interface AudioRoute {
    val key: String
    val label: String

    data object Speaker : AudioRoute {
        override val key = "speaker"
        override val label = "Speaker"
    }

    data class Bluetooth(val deviceId: Int, val name: String) : AudioRoute {
        override val key = "bluetooth"
        override val label = name.ifBlank { "Bluetooth" }
    }

    data class UsbDac(val deviceId: Int, val name: String) : AudioRoute {
        override val key = "usb"
        override val label = name.ifBlank { "USB audio" }
    }

    /** Reserved for the v1.1 wired amplifier; never selected in v1. */
    data object WiredAmp : AudioRoute {
        override val key = "wired_amp"
        override val label = "Wired amp"
    }

    companion object {
        const val SETTING_KEY = "audio_route"

        /** `state` key recording the last route that actually rendered a fire. */
        const val LAST_OK_KEY = "route_last_ok"
    }
}

/**
 * Resolves the preferred route against what is actually plugged in *at fire
 * time*. A missing route falls back to the speaker and reports it — a gong
 * from the wrong speaker beats no gong (design doc §06, §10).
 */
class AudioRouter(private val context: Context) {

    data class Resolution(
        val route: AudioRoute,
        val requested: String,
        /** True when the requested route was unavailable and we fell back. */
        val fellBack: Boolean,
    )

    fun available(): List<AudioRoute> {
        val am = context.getSystemService(AudioManager::class.java)
            ?: return listOf(AudioRoute.Speaker)
        val out = mutableListOf<AudioRoute>(AudioRoute.Speaker)
        for (d in am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
            val name = d.productName?.toString().orEmpty()
            when (d.type) {
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                -> out += AudioRoute.Bluetooth(d.id, name)

                AudioDeviceInfo.TYPE_USB_DEVICE,
                AudioDeviceInfo.TYPE_USB_HEADSET,
                AudioDeviceInfo.TYPE_USB_ACCESSORY,
                -> out += AudioRoute.UsbDac(d.id, name)
            }
        }
        return out.distinctBy { it.key + it.label }
    }

    fun resolve(preferredKey: String): Resolution {
        if (preferredKey.isBlank() || preferredKey == AudioRoute.Speaker.key) {
            return Resolution(AudioRoute.Speaker, AudioRoute.Speaker.key, fellBack = false)
        }
        val match = available().firstOrNull { it.key == preferredKey }
        return if (match == null) {
            Resolution(AudioRoute.Speaker, preferredKey, fellBack = true)
        } else {
            Resolution(match, preferredKey, fellBack = false)
        }
    }
}
