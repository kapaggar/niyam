package org.dhamma.gong.domain

/**
 * What the appliance can honestly say about its own network.
 *
 * Network is informational in v1 — the schedule is fully offline (design doc
 * §08, §10: "Airplane mode, no network, forever: fully operational"). The only
 * thing that wants a connection is the on-demand doha download. So this screen
 * exists to answer a support question over the phone — "is that tablet on the
 * centre WiFi, and what is its address?" — and nothing here is allowed to imply
 * that a red state threatens the gong.
 *
 * The parsing lives here, on the JVM, because every interesting case is a
 * string-mangling one: Android quotes SSIDs, hides them behind a location
 * permission this appliance deliberately does not request, and reports link
 * addresses with prefix lengths and scope ids attached.
 */
object NetworkFacts {

    /** What Android hands back instead of an SSID when it will not tell you. */
    const val UNKNOWN_SSID = "<unknown ssid>"

    enum class Mode { OFFLINE, WIFI, ETHERNET, CELLULAR, OTHER }

    /** Raw readings, exactly as the Android layer found them. */
    data class Probe(
        val hasWifi: Boolean = false,
        val hasEthernet: Boolean = false,
        val hasCellular: Boolean = false,
        /** A link exists at all — capabilities came back non-null. */
        val connected: Boolean = false,
        /** Android probed and found real internet, not just a hop. */
        val validated: Boolean = false,
        val metered: Boolean = false,
        /** `WifiInfo.getSSID()`, quotes and all. */
        val rawSsid: String? = null,
        /** Addresses on the active link, e.g. "192.168.1.44/24". */
        val linkAddresses: List<String> = emptyList(),
        /** Names of every up interface, for the hotspot sniff. */
        val interfaceNames: List<String> = emptyList(),
    )

    data class Facts(
        val mode: Mode = Mode.OFFLINE,
        val online: Boolean = false,
        val validated: Boolean = false,
        val metered: Boolean = false,
        /** Null when there is no WiFi, or when Android refused to name it. */
        val ssid: String? = null,
        /** True when there is a WiFi link but the name is withheld. */
        val ssidWithheld: Boolean = false,
        val ip: String? = null,
        /** An interface that looks like this device is serving its own hotspot. */
        val hotspot: Boolean = false,
    )

    fun of(p: Probe): Facts {
        val online = p.connected && (p.hasWifi || p.hasEthernet || p.hasCellular)
        val ssid = ssidOf(p.rawSsid)
        return Facts(
            mode = when {
                !online -> Mode.OFFLINE
                // Ethernet first: a wall tablet on a dock is the most reliable
                // link a centre can give it, and it wins over WiFi when both
                // are up.
                p.hasEthernet -> Mode.ETHERNET
                p.hasWifi -> Mode.WIFI
                p.hasCellular -> Mode.CELLULAR
                else -> Mode.OTHER
            },
            online = online,
            validated = p.validated,
            metered = p.metered,
            ssid = ssid,
            ssidWithheld = p.hasWifi && ssid == null,
            ip = ipOf(p.linkAddresses),
            hotspot = p.interfaceNames.any { isHotspotInterface(it) },
        )
    }

    /**
     * Unwrap `WifiInfo.getSSID()`.
     *
     * Android wraps a UTF-8 SSID in double quotes, returns [UNKNOWN_SSID] when
     * the caller holds no location permission (API 29+), and hex-encodes an
     * unprintable one. All three become null: an SSID the appliance cannot read
     * is better shown as "not available" than as a literal `<unknown ssid>`.
     */
    fun ssidOf(raw: String?): String? {
        val s = raw?.trim().orEmpty()
        if (s.isEmpty()) return null
        val unquoted = if (s.length >= 2 && s.startsWith('"') && s.endsWith('"')) {
            s.substring(1, s.length - 1)
        } else {
            s
        }
        return when {
            unquoted.isBlank() -> null
            unquoted.equals(UNKNOWN_SSID, ignoreCase = true) -> null
            unquoted.equals("unknown ssid", ignoreCase = true) -> null
            // A hex SSID is not a name anyone at a centre can act on.
            unquoted.startsWith("0x") -> null
            else -> unquoted
        }
    }

    /**
     * The address worth reading out over the phone.
     *
     * IPv4 wins: it is what someone types into a browser or a router's client
     * list. Loopback and link-local (169.254.x, fe80::) are dropped — they mean
     * "no usable address", so reporting one would be worse than reporting none.
     */
    fun ipOf(addresses: List<String>): String? {
        val cleaned = addresses.mapNotNull { bare(it) }
        return cleaned.firstOrNull { it.isUsableV4() }
            ?: cleaned.firstOrNull { it.isUsableV6() }
    }

    /** Strip the "/24" prefix length and any "%wlan0" scope id. */
    private fun bare(address: String): String? =
        address.trim().substringBefore('/').substringBefore('%')
            .takeIf { it.isNotBlank() }

    private fun String.isUsableV4(): Boolean =
        !contains(':') && contains('.') &&
            !startsWith("127.") && !startsWith("169.254.") && this != "0.0.0.0"

    /**
     * A routable IPv6 address. The check is deliberately positive: falling back
     * to "anything that is not obviously junk" would let an unusable IPv4 —
     * loopback, or the 169.254 address a failed DHCP leaves behind — through as
     * if it were a real address.
     */
    private fun String.isUsableV6(): Boolean =
        contains(':') && this != "::1" && this != "::" &&
            !startsWith("fe80", ignoreCase = true)

    /**
     * Does this interface name mean the device is serving its own hotspot?
     *
     * A heuristic, and labelled as one everywhere it surfaces. Android has no
     * public API to ask "am I tethering" — `WifiManager.isWifiApEnabled` has
     * been hidden since API 28 — and the interface names OEMs use are
     * convention, not contract. The screen says "looks like", never "is".
     */
    fun isHotspotInterface(name: String): Boolean {
        val n = name.trim().lowercase()
        if (n.isEmpty()) return false
        return HOTSPOT_PREFIXES.any { n.startsWith(it) }
    }

    private val HOTSPOT_PREFIXES = listOf(
        "ap0", "ap_br", "softap", "swlan", "wlan1", "wlan2",
    )
}
