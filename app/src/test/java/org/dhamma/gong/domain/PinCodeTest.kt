package org.dhamma.gong.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The app-open PIN. Stored in the `admin_pin_hash` setting as a salted
 * PBKDF2 string — never plaintext, never a bare digest.
 */
class PinCodeTest {

    @Test
    fun hashThenVerifyRoundTrips() {
        val stored = PinCode.hash("4821")
        assertTrue(PinCode.verify("4821", stored))
    }

    @Test
    fun wrongPinFailsVerify() {
        val stored = PinCode.hash("4821")
        assertFalse(PinCode.verify("4822", stored))
        assertFalse(PinCode.verify("", stored))
    }

    @Test
    fun hashesAreSalted() {
        // Same PIN twice must not produce the same string, or the stored
        // value leaks PIN equality across resets.
        assertNotEquals(PinCode.hash("4821"), PinCode.hash("4821"))
    }

    @Test
    fun storedFormatIsVersioned() {
        // pbkdf2:<iterations>:<saltB64>:<hashB64> — parseable forever.
        val parts = PinCode.hash("4821").split(":")
        assertEquals(4, parts.size)
        assertEquals("pbkdf2", parts[0])
        assertTrue(parts[1].toInt() >= 10_000)
    }

    @Test
    fun malformedStoredValueNeverVerifies() {
        assertFalse(PinCode.verify("4821", "garbage"))
        assertFalse(PinCode.verify("4821", "pbkdf2:notanint:xx:yy"))
        assertFalse(PinCode.verify("4821", ""))
    }

    @Test
    fun emptyStoredHashMeansNoPin() {
        assertFalse(PinCode.isSet(""))
        assertFalse(PinCode.isSet(null))
        assertTrue(PinCode.isSet(PinCode.hash("4821")))
    }

    @Test
    fun pinValidityIsFourToEightDigits() {
        assertTrue(PinCode.isValidPin("4821"))
        assertTrue(PinCode.isValidPin("48213765"))
        assertFalse(PinCode.isValidPin("482"))       // too short
        assertFalse(PinCode.isValidPin("482137651")) // too long
        assertFalse(PinCode.isValidPin("48a1"))      // not digits
        assertFalse(PinCode.isValidPin(""))
    }
}
