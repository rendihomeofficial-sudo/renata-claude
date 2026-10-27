package com.renata.claude.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/** Kesalahan yang bisa ditampilkan ke pengguna apa adanya. */
class UpdateException(message: String) : Exception(message)

/** Mengambil manifest rilis dan mengunduh berkas APK. */
object UpdateChecker {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    /** Membaca manifest pembaruan dari [manifestUrl]. */
    suspend fun fetchRelease(manifestUrl: String): AppRelease = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(manifestUrl)
            .header("Cache-Control", "no-cache, no-store")
            .header("Pragma", "no-cache")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw UpdateException("Server membalas ${response.code}.")
            }
            UpdateManifest.parseOrNull(body)
                ?: throw UpdateException("Manifest pembaruan tidak valid.")
        }
    }

    /**
     * Menyusun daftar URL manifest yang dicoba berurutan.
     *
     * Urutannya: URL kustom pengguna, URL bawaan dari build, lalu cermin cadangan
     * yang dipisah koma. URL kosong dibuang dan duplikat dihapus.
     */
    fun buildManifestUrls(custom: String, builtIn: String, fallbackCsv: String): List<String> {
        val mirrors = fallbackCsv.split(',').map { it.trim() }
        return (listOf(custom.trim(), builtIn.trim()) + mirrors)
            .filter { it.isNotBlank() }
            .distinct()
    }

    /**
     * Mencoba beberapa URL manifest satu per satu dan memakai hasil pertama yang berhasil.
     *
     * Dipakai karena `raw.githubusercontent.com` kadang tidak bisa dijangkau dari
     * jaringan tertentu, sementara cermin lain (jsDelivr, GitHub Releases) tetap bisa.
     */
    suspend fun fetchReleaseFromAny(urls: List<String>): AppRelease {
        var lastError: Throwable? = null
        for (url in urls) {
            if (url.isBlank()) continue
            try {
                return fetchRelease(url)
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: UpdateException("Tidak ada URL manifest yang bisa diakses.")
    }

    /**
     * Mengunduh APK ke [target] dan melaporkan progres 0..100 lewat [onProgress].
     */
    suspend fun downloadApk(
        url: String,
        target: File,
        onProgress: suspend (Int) -> Unit = {},
    ): File = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw UpdateException("Unduhan gagal, server membalas ${response.code}.")
            }
            val body = response.body ?: throw UpdateException("Berkas APK tidak ditemukan.")
            val total = body.contentLength()
            target.parentFile?.mkdirs()
            var downloaded = 0L
            body.byteStream().use { input ->
                FileOutputStream(target).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (total > 0) {
                            onProgress(((downloaded * 100) / total).toInt().coerceIn(0, 100))
                        }
                    }
                }
            }
            if (downloaded <= 0L) throw UpdateException("Berkas APK kosong.")
            onProgress(100)
            target
        }
    }
}
