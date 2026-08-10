package org.dhamma.gong.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkFactsTest {

    // ------------------------------------------------------------ mode

    @Test
    fun noTransportIsOffline() {
        val f = NetworkFacts.of(NetworkFacts.Probe())
        assertEquals(NetworkFacts.Mode.OFFLINE, f.mode)
        assertFalse(f.online)
    }

    @Test
    fun aTransportWithoutAConnectedLinkIsStillOffline() {
        // Capabilities that never came back must not paint the appliance online.
        val f = NetworkFacts.of(NetworkFacts.Probe(hasWifi = true, connected = false))
        assertEquals(NetworkFacts.Mode.OFFLINE, f.mode)
    }

    @Test
    fun ethernetOutranksWifiWhenBothAreUp() {
        val f = NetworkFacts.of(
            NetworkFacts.Probe(hasWifi = true, hasEthernet = true, connected = true),
        )
        assertEquals(NetworkFacts.Mode.ETHERNET, f.mode)
    }

    @Test
    fun wifiOutranksCellular() {
        val f = NetworkFacts.of(
            NetworkFacts.Probe(hasWifi = true, hasCellular = true, connected = true),
        )
        assertEquals(NetworkFacts.Mode.WIFI, f.mode)
    }

    @Test
    fun connectedButUnvalidatedIsOnlineAndSaysSoSeparately() {
        // A captive portal at a centre: linked, but nothing will download.
        val f = NetworkFacts.of(
            NetworkFacts.Probe(hasWifi = true, connected = true, validated = false),
        )
        assertTrue(f.online)
        assertFalse(f.validated)
    }

    @Test
    fun meteringIsCarriedThroughForTheDownloadWarning() {
        val f = NetworkFacts.of(
            NetworkFacts.Probe(hasCellular = true, connected = true, metered = true),
        )
        assertEquals(NetworkFacts.Mode.CELLULAR, f.mode)
        assertTrue(f.metered)
    }

    // ------------------------------------------------------------ ssid

    @Test
    fun quotedSsidsAreUnwrapped() {
        assertEquals("Dhamma Centre", NetworkFacts.ssidOf("\"Dhamma Centre\""))
    }

    @Test
    fun anUnquotedSsidSurvivesUntouched() {
        assertEquals("DhammaWiFi", NetworkFacts.ssidOf("DhammaWiFi"))
    }

    @Test
    fun withheldOrEmptySsidsBecomeNull() {
        // API 29+ returns the literal placeholder to callers with no location
        // permission — which this appliance deliberately never asks for.
        assertNull(NetworkFacts.ssidOf(NetworkFacts.UNKNOWN_SSID))
        assertNull(NetworkFacts.ssidOf("\"${NetworkFacts.UNKNOWN_SSID}\""))
        assertNull(NetworkFacts.ssidOf(null))
        assertNull(NetworkFacts.ssidOf(""))
        assertNull(NetworkFacts.ssidOf("\"\""))
        assertNull(NetworkFacts.ssidOf("0x466f6f"))
    }

    @Test
    fun aWifiLinkWithNoReadableNameReportsWithheldRatherThanAbsent() {
        val f = NetworkFacts.of(
            NetworkFacts.Probe(
                hasWifi = true,
                connected = true,
                rawSsid = NetworkFacts.UNKNOWN_SSID,
            ),
        )
        assertNull(f.ssid)
        assertTrue(f.ssidWithheld)
    }

    @Test
    fun ethernetWithNoSsidIsNotWithheld() {
        // Nothing is being hidden — there is simply no WiFi to name.
        val f = NetworkFacts.of(NetworkFacts.Probe(hasEthernet = true, connected = true))
        assertFalse(f.ssidWithheld)
    }

    // ------------------------------------------------------------ address

    @Test
    fun prefixLengthsAndScopeIdsAreStripped() {
        assertEquals("192.168.1.44", NetworkFacts.ipOf(listOf("192.168.1.44/24")))
        assertEquals("192.168.1.44", NetworkFacts.ipOf(listOf("192.168.1.44%wlan0")))
    }

    @Test
    fun ipv4IsPreferredOverIpv6() {
        val addresses = listOf("2001:db8::5/64", "192.168.1.44/24")
        assertEquals("192.168.1.44", NetworkFacts.ipOf(addresses))
    }

    @Test
    fun loopbackAndLinkLocalAreNotReportedAsAnAddress() {
        assertNull(NetworkFacts.ipOf(listOf("127.0.0.1", "169.254.3.9", "fe80::1%wlan0", "::1")))
    }

    @Test
    fun aGlobalIpv6AddressIsBetterThanNothing() {
        assertEquals("2001:db8::5", NetworkFacts.ipOf(listOf("fe80::1", "2001:db8::5/64")))
    }

    @Test
    fun noAddressesMeansNoAddress() {
        assertNull(NetworkFacts.ipOf(emptyList()))
        assertNull(NetworkFacts.ipOf(listOf("", "   ")))
    }

    // ------------------------------------------------------------ hotspot

    @Test
    fun knownHotspotInterfaceNamesAreRecognised() {
        for (name in listOf("ap0", "AP0", "softap0", "swlan0", "wlan1", " ap_br0 ")) {
            assertTrue(name, NetworkFacts.isHotspotInterface(name))
        }
    }

    @Test
    fun ordinaryInterfacesAreNotMistakenForAHotspot() {
        for (name in listOf("wlan0", "eth0", "lo", "dummy0", "rmnet0", "")) {
            assertFalse(name, NetworkFacts.isHotspotInterface(name))
        }
    }

    @Test
    fun aHotspotInterfaceOnTheDeviceSetsTheFlag() {
        val f = NetworkFacts.of(
            NetworkFacts.Probe(
                hasWifi = true,
                connected = true,
                interfaceNames = listOf("lo", "wlan0", "ap0"),
            ),
        )
        assertTrue(f.hotspot)
    }
}
