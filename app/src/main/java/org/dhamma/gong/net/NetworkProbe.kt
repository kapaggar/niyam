package org.dhamma.gong.net

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.provider.Settings
import android.util.Log
import org.dhamma.gong.domain.NetworkFacts
import java.net.NetworkInterface

/**
 * Reads what Android will tell an ordinary app about its own network.
 *
 * Deliberately shallow: it takes one snapshot on demand and never registers a
 * callback, holds a lock, or retries. Nothing on the critical path reads it —
 * the schedule is fully offline (design doc §10) — so a probe that fails is a
 * blank row on an informational screen, never a missed gong. Every lookup is
 * wrapped accordingly.
 *
 * The parsing all lives in [NetworkFacts] so it can be unit-tested on the JVM;
 * this class only fetches.
 */
class NetworkProbe(private val context: Context) {

    fun read(): NetworkFacts.Facts = NetworkFacts.of(probe())

    private fun probe(): NetworkFacts.Probe = runCatching {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val active = cm?.activeNetwork
        val caps = active?.let { cm.getNetworkCapabilities(it) }
        val link = active?.let { cm.getLinkProperties(it) }

        val hasWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true

        NetworkFacts.Probe(
            hasWifi = hasWifi,
            hasEthernet = caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true,
            hasCellular = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true,
            connected = caps != null,
            validated = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true,
            // Android states the negative, so a missing capability means metered
            // — which is the safe way round for a screen that warns about data.
            metered = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) != true,
            rawSsid = if (hasWifi) ssid() else null,
            linkAddresses = link?.linkAddresses.orEmpty()
                .mapNotNull { it.address?.hostAddress },
            interfaceNames = upInterfaceNames(),
        )
    }.getOrElse {
        Log.w(TAG, "network probe failed", it)
        NetworkFacts.Probe()
    }

    /**
     * The connected network's name, if Android is willing to say.
     *
     * From API 29 this returns the `<unknown ssid>` placeholder unless the
     * caller holds a location permission. This appliance does not request one:
     * a location grant to put a cosmetic label on an informational screen is a
     * bad trade, and the screen explains the blank instead.
     */
    @Suppress("DEPRECATION")
    private fun ssid(): String? = runCatching {
        context.getSystemService(WifiManager::class.java)?.connectionInfo?.ssid
    }.getOrNull()

    /**
     * Interface names, for the hotspot sniff.
     *
     * A guess, and labelled as one wherever it surfaces: Android has had no
     * public "am I tethering" API since `isWifiApEnabled` was hidden in API 28,
     * and the names OEMs give the AP interface are convention rather than
     * contract.
     */
    private fun upInterfaceNames(): List<String> = runCatching {
        NetworkInterface.getNetworkInterfaces()
            ?.toList()
            ?.filter { runCatching { it.isUp }.getOrDefault(false) }
            ?.map { it.name }
            .orEmpty()
    }.getOrDefault(emptyList())

    private companion object {
        const val TAG = "NetworkProbe"
    }
}

/**
 * Doors into the system UI. Joining a WiFi network and starting a hotspot both
 * need system screens — an app cannot do either for the user on a modern
 * Android, and this one does not try (design doc §02: "deep links where system
 * UI is required").
 */
object NetworkSettings {

    fun openWifi(context: Context) {
        launch(context, Intent(Settings.ACTION_WIFI_SETTINGS))
    }

    /**
     * Tethering has no public settings action. The direct component works on
     * most builds; [Settings.ACTION_WIRELESS_SETTINGS] is the fallback that
     * always exists, and lands staff one tap away.
     */
    fun openHotspot(context: Context) {
        val direct = Intent(Intent.ACTION_MAIN).setClassName(
            "com.android.settings",
            "com.android.settings.TetherSettings",
        )
        if (!launch(context, direct)) {
            launch(context, Intent(Settings.ACTION_WIRELESS_SETTINGS))
        }
    }

    private fun launch(context: Context, intent: Intent): Boolean =
        runCatching {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess
}
