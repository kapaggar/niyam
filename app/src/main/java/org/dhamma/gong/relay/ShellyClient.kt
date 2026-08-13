package org.dhamma.gong.relay

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Shelly Gen2+ JSON-RPC over plain HTTP on the centre LAN.
 *
 * Verified against the Shelly Gen4 documentation:
 *  - `GET /rpc/Switch.Set?id=<n>&on=true|false&toggle_after=<seconds>`
 *  - response `{"was_on": bool}`
 *  - auth, when enabled, is digest SHA-256 (RFC 7616), user `admin`,
 *    realm = device id. `Shelly.GetDeviceInfo` stays unauthenticated, which is
 *    why it is the reachability probe.
 *
 * Deliberately dependency-free (`HttpURLConnection`, no OkHttp/Retrofit): the
 * app ships one short request to one LAN device and nothing else.
 *
 * **Every call is bounded by [connectTimeoutMs] + [readTimeoutMs] and is meant
 * to be launched off the play path.** This class never retries: one attempt per
 * transition, so an unreachable Shelly cannot become a retry storm.
 */
class ShellyClient(
    private val connectTimeoutMs: Int = DEFAULT_TIMEOUT_MS,
    private val readTimeoutMs: Int = DEFAULT_TIMEOUT_MS,
) {

    sealed interface Result {
        /** 2xx. [body] is the raw JSON, e.g. `{"was_on":false}`. */
        data class Ok(val body: String) : Result

        /** 401 that digest could not satisfy — wrong or missing password. */
        data class AuthRequired(val realm: String) : Result

        /** Unreachable, timed out, refused, or a non-2xx status. */
        data class Failed(val reason: String) : Result
    }

    /** Switch a relay channel, always carrying the device-side auto-off watchdog. */
    suspend fun setSwitch(
        host: String,
        switchId: Int,
        on: Boolean,
        toggleAfterSeconds: Long? = null,
        user: String = DEFAULT_USER,
        password: String = "",
    ): Result {
        val params = buildString {
            append("id=").append(switchId)
            append("&on=").append(on)
            // toggle_after only makes sense on the ON edge; an OFF with a timer
            // would flip the amp back on, which is the opposite of the point.
            if (on && toggleAfterSeconds != null && toggleAfterSeconds > 0) {
                append("&toggle_after=").append(toggleAfterSeconds)
            }
        }
        return rpc(host, "Switch.Set", params, user, password)
    }

    /**
     * Unauthenticated identity probe — the Test button and the reachability dot.
     * Returns model, id and MAC.
     */
    suspend fun deviceInfo(host: String): Result = rpc(host, "Shelly.GetDeviceInfo", null, "", "")

    // ------------------------------------------------------------ transport

    private suspend fun rpc(
        host: String,
        method: String,
        query: String?,
        user: String,
        password: String,
    ): Result = withContext(Dispatchers.IO) {
        val cleanHost = host.trim().removePrefix("http://").removePrefix("https://").trimEnd('/')
        if (cleanHost.isEmpty()) return@withContext Result.Failed("no host configured")
        val path = "/rpc/$method" + if (query.isNullOrEmpty()) "" else "?$query"
        val url = runCatching { URL("http://$cleanHost$path") }
            .getOrElse { return@withContext Result.Failed("bad host: $cleanHost") }

        when (val first = attempt(url, path, null)) {
            is Attempt.Success -> Result.Ok(first.body)
            is Attempt.Error -> Result.Failed(first.reason)
            is Attempt.Challenge -> {
                if (password.isEmpty()) return@withContext Result.AuthRequired(first.realm)
                val header = digestHeader(first, path, user.ifBlank { DEFAULT_USER }, password)
                    ?: return@withContext Result.AuthRequired(first.realm)
                when (val second = attempt(url, path, header)) {
                    is Attempt.Success -> Result.Ok(second.body)
                    is Attempt.Error -> Result.Failed(second.reason)
                    // A second 401 means the credentials are wrong; do not loop.
                    is Attempt.Challenge -> Result.AuthRequired(second.realm)
                }
            }
        }
    }

    private sealed interface Attempt {
        data class Success(val body: String) : Attempt
        data class Challenge(
            val realm: String,
            val nonce: String,
            val qop: String,
            val opaque: String?,
            val algorithm: String,
        ) : Attempt

        data class Error(val reason: String) : Attempt
    }

    private fun attempt(url: URL, path: String, authorization: String?): Attempt {
        var conn: HttpURLConnection? = null
        return try {
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                useCaches = false
                instanceFollowRedirects = false
                authorization?.let { setRequestProperty("Authorization", it) }
            }
            when (val code = conn.responseCode) {
                in 200..299 -> Attempt.Success(
                    conn.inputStream.bufferedReader().use { it.readText() },
                )

                HttpURLConnection.HTTP_UNAUTHORIZED ->
                    parseChallenge(conn.getHeaderField("WWW-Authenticate"))
                        ?: Attempt.Error("401 without a usable digest challenge")

                else -> Attempt.Error("HTTP $code")
            }
        } catch (e: IOException) {
            // Unreachable, refused, or timed out. The gong still rings.
            Attempt.Error(e.message ?: e.javaClass.simpleName)
        } catch (e: SecurityException) {
            Attempt.Error(e.message ?: "blocked")
        } finally {
            runCatching { conn?.disconnect() }
        }
    }

    // ------------------------------------------------------------ digest auth

    private fun parseChallenge(header: String?): Attempt.Challenge? {
        if (header == null || !header.trimStart().startsWith("Digest", ignoreCase = true)) return null
        val fields = HashMap<String, String>()
        // key=value or key="value", comma separated. Values here (nonce, realm)
        // never contain commas on Shelly firmware.
        Regex("""(\w+)\s*=\s*(?:"([^"]*)"|([^,\s]+))""")
            .findAll(header.substringAfter("Digest"))
            .forEach { m ->
                fields[m.groupValues[1].lowercase()] =
                    m.groupValues[2].ifEmpty { m.groupValues[3] }
            }
        val realm = fields["realm"] ?: return null
        val nonce = fields["nonce"] ?: return null
        return Attempt.Challenge(
            realm = realm,
            nonce = nonce,
            qop = fields["qop"] ?: "auth",
            opaque = fields["opaque"],
            algorithm = fields["algorithm"] ?: "SHA-256",
        )
    }

    /** RFC 7616 digest, SHA-256. Never logged, and never echoed into state. */
    private fun digestHeader(
        challenge: Attempt.Challenge,
        uri: String,
        user: String,
        password: String,
    ): String? {
        if (!challenge.algorithm.startsWith("SHA-256", ignoreCase = true)) return null
        val cnonce = randomHex()
        val nc = "00000001"
        val qop = if (challenge.qop.contains("auth")) "auth" else return null
        val ha1 = sha256("$user:${challenge.realm}:$password")
        val ha2 = sha256("GET:$uri")
        val response = sha256("$ha1:${challenge.nonce}:$nc:$cnonce:$qop:$ha2")
        return buildString {
            append("Digest username=\"").append(user).append('"')
            append(", realm=\"").append(challenge.realm).append('"')
            append(", nonce=\"").append(challenge.nonce).append('"')
            append(", uri=\"").append(uri).append('"')
            append(", algorithm=SHA-256")
            append(", qop=").append(qop)
            append(", nc=").append(nc)
            append(", cnonce=\"").append(cnonce).append('"')
            append(", response=\"").append(response).append('"')
            challenge.opaque?.let { append(", opaque=\"").append(it).append('"') }
        }
    }

    private fun sha256(s: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(s.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun randomHex(): String {
        val bytes = ByteArray(8)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    companion object {
        const val DEFAULT_USER = "admin"

        /**
         * Short on purpose: the relay is on the same LAN, and a slow answer must
         * never be something a play could ever be waiting on.
         */
        const val DEFAULT_TIMEOUT_MS = 2_000

        /** Hard ceiling applied by [RelayController] around any single call. */
        const val CALL_BUDGET_MS = 5_000L

        /** Pull a field out of a Shelly JSON body without a JSON dependency. */
        fun field(body: String, name: String): String? =
            Regex(""""$name"\s*:\s*"([^"]*)"""").find(body)?.groupValues?.get(1)
    }
}
