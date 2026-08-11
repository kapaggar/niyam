package org.dhamma.gong.player

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import org.dhamma.gong.domain.RoutePlan

/**
 * The route abstraction from design doc §06. Adding a wired amp in v1.1 is a
 * new case here, not a rewrite of the player.
 */
sealed interface AudioRoute {
    val key: String
    val label: String

    /**
     * The `AudioDeviceInfo.getId()` to hand ExoPlayer, or null to let Android
     * pick. Null is right for the speaker — asking for the built-in device by
     * id and missing would be worse than accepting the system default.
     */
    val deviceId: Int? get() = null

    data object Speaker : AudioRoute {
        override val key = RoutePlan.SPEAKER
        override val label = "Speaker"
    }

    data class Bluetooth(override val deviceId: Int, val name: String) : AudioRoute {
        override val key = RoutePlan.BLUETOOTH
        override val label = name.ifBlank { "Bluetooth" }
    }

    data class UsbDac(override val deviceId: Int, val name: String) : AudioRoute {
        override val key = RoutePlan.USB
        override val label = name.ifBlank { "USB audio" }
    }

    /** Reserved for the v1.1 wired amplifier; never selected in v1. */
    data object WiredAmp : AudioRoute {
        override val key = RoutePlan.WIRED_AMP
        override val label = "Wired amp"
    }

    companion object {
        const val SETTING_KEY = "audio_route"

        /** `state` key recording the last route that actually rendered a fire. */
        const val LAST_OK_KEY = "route_last_ok"

        /**
         * When that happened, ISO-8601. "Bluetooth worked" is a much weaker
         * claim than "Bluetooth worked this morning" — a timestamp from three
         * weeks ago is how staff spot an amp that has been quietly dead.
         */
        const val LAST_OK_AT_KEY = "route_last_ok_at"
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
        val devices = available()
        // The rule itself is pure and unit-tested in RoutePlan; this class only
        // supplies what Android currently reports and maps the answer back to a
        // concrete device (its id is what actually steers ExoPlayer).
        val choice = RoutePlan.choose(devices.map { it.key }, preferredKey)
        val route = devices.firstOrNull { it.key == choice.key } ?: AudioRoute.Speaker
        return Resolution(route, choice.requested, choice.fellBack)
    }
}
