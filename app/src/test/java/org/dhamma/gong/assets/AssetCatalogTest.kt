package org.dhamma.gong.assets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [AssetCatalog] against a byte-for-byte copy of the real bundled manifest
 * (`src/test/resources/catalog/doha_manifest.json`), so schema drift between
 * the asset and the parser fails here first.
 */
class AssetCatalogTest {

    private fun realManifest(): String =
        javaClass.getResourceAsStream("/catalog/doha_manifest.json")!!
            .bufferedReader().use { it.readText() }

    @Test
    fun `parses the real manifest with all eleven assets`() {
        val catalog = AssetCatalog.parse(realManifest())

        assertEquals("https://apt.vridhamma.org/updates/v2/", catalog.cdnBase)
        assertEquals(11, catalog.assets.size)
        assertEquals(
            "every id is the filename minus .mp3",
            catalog.assets.map { it.filename.removeSuffix(".mp3") },
            catalog.assets.map { it.id },
        )
    }

    @Test
    fun `D06 row carries both checksums and sizes`() {
        val d06 = AssetCatalog.parse(realManifest()).byId("D06_0632_Doha-Samatha-_NA_NA")!!

        assertEquals("D06_0632_Doha-Samatha-_NA_NA.mp3", d06.filename)
        assertEquals("common-general/D06_0632_Doha-Samatha-_NA_NA.mp3", d06.relativePath)
        assertEquals(
            "https://apt.vridhamma.org/updates/v2/common-general/D06_0632_Doha-Samatha-_NA_NA.mp3",
            d06.cdnUrl,
        )
        assertEquals("1ec44e183f56a9ed592b94c22c9e05ce616899e3bcf57c97162020f5c2be826b", d06.encryptedSha256)
        assertEquals(44_633_680L, d06.encryptedBytes)
        assertEquals("de91e22cf330b6f810537c53736bbc62d9e76ea92cfed6563a8abfe88f687f12", d06.decryptedSha256)
        assertEquals(44_633_659L, d06.decryptedBytes)
    }

    @Test
    fun `byId accepts the id or the full filename`() {
        val catalog = AssetCatalog.parse(realManifest())

        val byShortId = catalog.byId("D01_0632_Doha-Hin-1_NA_NA")
        val byFilename = catalog.byId("D01_0632_Doha-Hin-1_NA_NA.mp3")

        assertEquals(byShortId, byFilename)
        assertEquals("D01_0632_Doha-Hin-1_NA_NA", byShortId!!.id)
        assertNull(catalog.byId("D99_not_in_catalog"))
    }

    @Test
    fun `D01 and D09 share a title family but are distinct assets`() {
        val catalog = AssetCatalog.parse(realManifest())
        val d01 = catalog.byId("D01_0632_Doha-Hin-1_NA_NA")!!
        val d09 = catalog.byId("D09_0632_Doha-Hin-1_NA_NA")!!

        assertTrue(d01.encryptedSha256 != d09.encryptedSha256)
        assertTrue(d01.decryptedSha256 != d09.decryptedSha256)
    }

    @Test
    fun `unknown fields are ignored and a missing checksum defaults to empty`() {
        val catalog = AssetCatalog.parse(
            """
            {
              "schema_version": 1,
              "cdn_base": "https://example.invalid/base/",
              "some_future_field": {"nested": [1, 2, 3]},
              "assets": [
                {
                  "filename": "D42_test.mp3",
                  "relative_path": "common-general/D42_test.mp3",
                  "cdn_url": "https://example.invalid/base/common-general/D42_test.mp3",
                  "encrypted_bytes": 12345,
                  "decrypted_sha256": "aa",
                  "decrypted_bytes": 12321,
                  "encrypted_magic": "Salted__",
                  "decrypt_ok": true,
                  "brand_new_qa_field": "ignored",
                  "error": ""
                }
              ]
            }
            """.trimIndent(),
        )

        val asset = catalog.byId("D42_test")!!
        assertEquals("", asset.encryptedSha256)
        assertEquals(12_345L, asset.encryptedBytes)
    }

    @Test
    fun `garbage json throws instead of returning a half-catalog`() {
        assertThrows(Exception::class.java) { AssetCatalog.parse("not json at all {{{") }
        assertThrows(Exception::class.java) { AssetCatalog.parse("""{"assets": "not-a-list"}""") }
    }
}
