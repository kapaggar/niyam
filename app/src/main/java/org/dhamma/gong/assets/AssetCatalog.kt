package org.dhamma.gong.assets

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.dhamma.gong.domain.AudioAsset

/**
 * The doha catalog bundled at `assets/doha_manifest.json` (schema_version 1),
 * parsed into the pinned [AudioAsset] shape.
 *
 * The manifest carries extra QA fields (`encrypted_magic`, `decrypt_ok`,
 * `error`, …) that the app has no use for; parsing ignores anything it does
 * not recognise so the catalog can grow without breaking old builds. A row
 * with a missing checksum still parses (the field defaults to "") — it is the
 * downloader's job to refuse to fetch such a row, not the parser's.
 */
class AssetCatalog(val cdnBase: String, val assets: List<AudioAsset>) {

    private val index: Map<String, AudioAsset> = assets.associateBy { it.id }

    /** Looks up by asset id; a full filename (with `.mp3`) is tolerated too. */
    fun byId(id: String): AudioAsset? = index[id] ?: index[id.removeSuffix(MP3_SUFFIX)]

    companion object {
        private const val MP3_SUFFIX = ".mp3"

        private val decoder = Json { ignoreUnknownKeys = true }

        /**
         * Parses the manifest JSON. Throws (kotlinx.serialization's
         * `SerializationException` / `IllegalArgumentException`) on malformed
         * input — the caller treats a broken bundled catalog as a build error.
         */
        fun parse(json: String): AssetCatalog {
            val manifest = decoder.decodeFromString(ManifestDto.serializer(), json)
            return AssetCatalog(
                cdnBase = manifest.cdnBase,
                assets = manifest.assets.map { row ->
                    AudioAsset(
                        id = row.filename.removeSuffix(MP3_SUFFIX),
                        filename = row.filename,
                        relativePath = row.relativePath,
                        cdnUrl = row.cdnUrl,
                        encryptedSha256 = row.encryptedSha256,
                        encryptedBytes = row.encryptedBytes,
                        decryptedSha256 = row.decryptedSha256,
                        decryptedBytes = row.decryptedBytes,
                    )
                },
            )
        }
    }

    @Serializable
    private data class ManifestDto(
        @SerialName("schema_version") val schemaVersion: Int = 1,
        @SerialName("cdn_base") val cdnBase: String = "",
        val assets: List<AssetDto> = emptyList(),
    )

    @Serializable
    private data class AssetDto(
        val filename: String,
        @SerialName("relative_path") val relativePath: String = "",
        @SerialName("cdn_url") val cdnUrl: String = "",
        @SerialName("encrypted_sha256") val encryptedSha256: String = "",
        @SerialName("encrypted_bytes") val encryptedBytes: Long = 0,
        @SerialName("decrypted_sha256") val decryptedSha256: String = "",
        @SerialName("decrypted_bytes") val decryptedBytes: Long = 0,
    )
}
