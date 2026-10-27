package com.renata.claude.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Menguji penyusunan daftar URL manifest pembaruan.
 *
 * Inicovering kasus yang membuat update tidak terdeteksi di perangkat:
 * URL utama gagal dijangkau, jadi harus ada cermin cadangan yang dicoba
 * setelahnya.
 */
class UpdateManifestUrlsTest {

    @Test
    fun `url kustom dipakai lebih dulu dari url bawaan`() {
        val urls = UpdateChecker.buildManifestUrls(
            custom = "https://custom.example/version.json",
            builtIn = "https://built-in.example/version.json",
            fallbackCsv = "",
        )
        assertEquals(
            listOf("https://custom.example/version.json", "https://built-in.example/version.json"),
            urls,
        )
    }

    @Test
    fun `cermin cadangan ditambahkan setelah url utama dan dipecah per koma`() {
        val urls = UpdateChecker.buildManifestUrls(
            custom = "https://custom.example/version.json",
            builtIn = "https://built-in.example/version.json",
            fallbackCsv = "https://cdn.example/version.json, https://releases.example/version.json",
        )
        assertEquals(
            listOf(
                "https://custom.example/version.json",
                "https://built-in.example/version.json",
                "https://cdn.example/version.json",
                "https://releases.example/version.json",
            ),
            urls,
        )
    }

    @Test
    fun `url kosong dibuang dan duplikat dihapus`() {
        val urls = UpdateChecker.buildManifestUrls(
            custom = "",
            builtIn = "https://built-in.example/version.json",
            fallbackCsv = " , https://built-in.example/version.json , https://cdn.example/version.json ",
        )
        assertEquals(
            listOf("https://built-in.example/version.json", "https://cdn.example/version.json"),
            urls,
        )
    }

    @Test
    fun `pembaruan tetap punya sumber cadangan saat url bawaan kosong`() {
        val urls = UpdateChecker.buildManifestUrls(
            custom = "",
            builtIn = "",
            fallbackCsv = "https://cdn.example/version.json",
        )
        assertTrue(urls.isNotEmpty())
        assertEquals("https://cdn.example/version.json", urls.first())
    }
}
