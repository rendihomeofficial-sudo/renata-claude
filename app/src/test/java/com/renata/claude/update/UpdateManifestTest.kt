package com.renata.claude.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Menguji pembacaan manifest pembaruan dan perbandingan versi. */
class UpdateManifestTest {

    private val validJson = """
        {
          "versionName": "1.2.0",
          "versionCode": 3,
          "downloadUrl": "https://example.com/Renata-Claude-1.2.0.apk",
          "releaseNotes": "Perbaikan kecil.",
          "publishedAt": "2026-10-25"
        }
    """.trimIndent()

    @Test
    fun `membaca manifest yang valid`() {
        val release = UpdateManifest.parse(validJson)
        assertEquals("1.2.0", release.versionName)
        assertEquals(3, release.versionCode)
        assertEquals("https://example.com/Renata-Claude-1.2.0.apk", release.downloadUrl)
        assertEquals("Perbaikan kecil.", release.releaseNotes)
        assertEquals("2026-10-25", release.publishedAt)
    }

    @Test
    fun `kategori opsional boleh kosong`() {
        val release = UpdateManifest.parse(
            """{"versionName":"1.0.1","versionCode":2,"downloadUrl":"https://example.com/a.apk"}"""
        )
        assertEquals("", release.releaseNotes)
        assertEquals("", release.publishedAt)
    }

    @Test
    fun `manifest tanpa url ditolak`() {
        assertNull(UpdateManifest.parseOrNull("""{"versionName":"1.0.0","versionCode":1}"""))
    }

    @Test
    fun `manifest dengan url bukan http ditolak`() {
        assertNull(
            UpdateManifest.parseOrNull(
                """{"versionName":"1.0.0","versionCode":1,"downloadUrl":"ftp://example.com/a.apk"}"""
            )
        )
    }

    @Test
    fun `manifest rusak ditolak`() {
        assertNull(UpdateManifest.parseOrNull("bukan json"))
    }

    @Test
    fun `rilis baru bila version code lebih besar`() {
        val release = UpdateManifest.parse(validJson)
        assertTrue(release.isNewerThan(2))
        assertFalse(release.isNewerThan(3))
        assertFalse(release.isNewerThan(4))
    }
}
