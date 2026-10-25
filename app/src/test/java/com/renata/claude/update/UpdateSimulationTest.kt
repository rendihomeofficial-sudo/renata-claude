package com.renata.claude.update

import com.renata.claude.ui.UpdateUiState
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

/**
 * Tes simulasi pembaruan menyeluruh (end-to-end simulation):
 * Menjalankan server HTTP lokal murni, menyajikan manifest rilis baru (versionCode lebih tinggi),
 * dan memverifikasi bahwa alur pemeriksaan pembaruan menghasilkan status [UpdateUiState.Available]
 * sehingga dialog popup pembaruan muncul di UI.
 */
class UpdateSimulationTest {

    private var serverSocket: ServerSocket? = null
    private var serverPort = 0
    @Volatile
    private var isRunning = true

    @Before
    fun setUp() {
        serverSocket = ServerSocket(0)
        serverPort = serverSocket!!.localPort
        isRunning = true
    }

    @After
    fun tearDown() {
        isRunning = false
        runCatching { serverSocket?.close() }
    }

    private fun startLocalServer(responseBody: String, statusCode: Int = 200) {
        thread(isDaemon = true) {
            while (isRunning && !serverSocket!!.isClosed) {
                val client: Socket = try {
                    serverSocket!!.accept()
                } catch (e: Exception) {
                    break
                }
                thread(isDaemon = true) {
                    client.use { s ->
                        val reader = BufferedReader(InputStreamReader(s.getInputStream()))
                        // Baca request line & headers
                        while (true) {
                            val line = reader.readLine() ?: break
                            if (line.isEmpty()) break
                        }
                        val writer = OutputStreamWriter(s.getOutputStream(), Charsets.UTF_8)
                        val statusLine = if (statusCode == 200) "HTTP/1.1 200 OK" else "HTTP/1.1 $statusCode Error"
                        val bytes = responseBody.toByteArray(Charsets.UTF_8)
                        writer.write("$statusLine\r\n")
                        writer.write("Content-Type: application/json; charset=utf-8\r\n")
                        writer.write("Content-Length: ${bytes.size}\r\n")
                        writer.write("Connection: close\r\n\r\n")
                        writer.flush()
                        s.getOutputStream().write(bytes)
                        s.getOutputStream().flush()
                    }
                }
            }
        }
    }

    @Test
    fun `simulasi deteksi versi baru menghasilkan UpdateUiState Available untuk popup`() = runBlocking {
        val currentInstalledVersionCode = 4 // v1.3.0
        val newerVersionManifest = """
            {
              "versionName": "1.4.0",
              "versionCode": 5,
              "downloadUrl": "https://github.com/OWNER/REPO/releases/download/v1.4.0/Renata-Claude-1.4.0.apk",
              "releaseNotes": "1. Fitur baru: popup update otomatis bekerja sempurna.\n2. Perbaikan stabilitas.",
              "publishedAt": "2026-11-01"
            }
        """.trimIndent()

        startLocalServer(newerVersionManifest)
        val manifestUrl = "http://127.0.0.1:$serverPort/version.json"

        // 1. Fetch release dari server
        val release = UpdateChecker.fetchRelease(manifestUrl)
        assertEquals("1.4.0", release.versionName)
        assertEquals(5, release.versionCode)

        // 2. Evaluasi apakah versi lebih baru dari yang terpasang
        val isNewer = release.isNewerThan(currentInstalledVersionCode)
        assertTrue("Versi 5 harus lebih baru dari versi 4 yang terpasang", isNewer)

        // 3. Simulasikan state machine yang dipakai di AppRoot.kt
        val uiState: UpdateUiState = if (release.isNewerThan(currentInstalledVersionCode)) {
            UpdateUiState.Available(release)
        } else {
            UpdateUiState.UpToDate(release)
        }

        // 4. Pastikan UpdateUiState adalah Available (yang memicu dialog UpdateDialog muncul)
        assertTrue(uiState is UpdateUiState.Available)
        val availableState = uiState as UpdateUiState.Available
        assertEquals("1.4.0", availableState.release.versionName)
        assertEquals(5, availableState.release.versionCode)
        assertTrue(availableState.release.releaseNotes.contains("popup update otomatis"))
    }

    @Test
    fun `simulasi ketika versi di server sama atau lebih rendah tidak memunculkan popup available`() = runBlocking {
        val currentInstalledVersionCode = 4 // v1.3.0
        val sameVersionManifest = """
            {
              "versionName": "1.3.0",
              "versionCode": 4,
              "downloadUrl": "https://github.com/OWNER/REPO/releases/download/v1.3.0/Renata-Claude-1.3.0.apk",
              "releaseNotes": "Versi saat ini."
            }
        """.trimIndent()

        startLocalServer(sameVersionManifest)
        val manifestUrl = "http://127.0.0.1:$serverPort/version.json"

        val release = UpdateChecker.fetchRelease(manifestUrl)
        assertFalse("Versi sama tidak boleh dianggap lebih baru", release.isNewerThan(currentInstalledVersionCode))

        val uiState: UpdateUiState = if (release.isNewerThan(currentInstalledVersionCode)) {
            UpdateUiState.Available(release)
        } else {
            UpdateUiState.UpToDate(release)
        }

        assertTrue(uiState is UpdateUiState.UpToDate)
    }
}
