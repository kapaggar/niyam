package org.dhamma.gong.domain

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * The app-open PIN, stored in the `admin_pin_hash` setting.
 *
 * Salted PBKDF2-HmacSHA256, `pbkdf2:<iterations>:<saltB64>:<hashB64>`. The
 * threat model is a centre tablet on a wall: keep casual fingers out of the
 * schedule, and never keep the PIN recoverable from a pulled `gong.db`.
 * Pure JVM — no Android imports.
 */
object PinCode {

    const val ITERATIONS = 60_000
    private const val SALT_BYTES = 16
    private const val KEY_BITS = 256
    private val PIN_SHAPE = Regex("[0-9]{4,8}")

    /** 4–8 digits: long enough to matter, short enough for a wall keypad. */
    fun isValidPin(pin: String): Boolean = PIN_SHAPE.matches(pin)

    fun isSet(stored: String?): Boolean = !stored.isNullOrBlank()

    fun hash(pin: String, iterations: Int = ITERATIONS): String {
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val b64 = Base64.getEncoder()
        return "pbkdf2:$iterations:${b64.encodeToString(salt)}:" +
            b64.encodeToString(derive(pin, salt, iterations))
    }

    /** Constant-time compare; any malformed stored value is simply "no". */
    fun verify(pin: String, stored: String): Boolean {
        val parts = stored.split(":")
        if (parts.size != 4 || parts[0] != "pbkdf2") return false
        val iterations = parts[1].toIntOrNull()?.takeIf { it > 0 } ?: return false
        val decoder = Base64.getDecoder()
        val salt = runCatching { decoder.decode(parts[2]) }.getOrNull() ?: return false
        val expected = runCatching { decoder.decode(parts[3]) }.getOrNull() ?: return false
        return MessageDigest.isEqual(expected, derive(pin, salt, iterations))
    }

    private fun derive(pin: String, salt: ByteArray, iterations: Int): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(pin.toCharArray(), salt, iterations, KEY_BITS))
            .encoded
}
