package com.renata.claude.data

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import kotlin.concurrent.thread

/**
 * Minimal HTTP/1.1 stub server built on plain sockets.
 *
 * Android's unit test classpath has no `com.sun.net.httpserver`, so the stub is
 * implemented here with `java.net` only.
 */
private class MiniHttpServer(
    private val handler: (method: String, path: String, headers: Map<String, String>, body: String) -> Triple<Int, String, String>,
) : AutoCloseable {

    private val serverSocket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
    @Volatile private var running = true

    val port: Int get() = serverSocket.localPort

    init {
        thread(isDaemon = true, name = "mini-http-server") {
            while (running) {
                val socket = try {
                    serverSocket.accept()
                } catch (e: Exception) {
                    if (running) continue else break
                }
                thread(isDaemon = true, name = "mini-http-conn") {
                    try {
                        handle(socket)
                    } catch (e: Exception) {
                        // ignore connection level failures
                    }
                }
            }
        }
    }

    private fun handle(socket: Socket) {
        socket.use { s ->
            val reader = BufferedReader(InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8))
            val requestLine = reader.readLine() ?: return
            val parts = requestLine.split(' ')
            val method = parts.getOrNull(0) ?: "GET"
            val path = parts.getOrNull(1) ?: "/"

            val headers = LinkedHashMap<String, String>()
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isEmpty()) break
                val idx = line.indexOf(':')
                if (idx <= 0) continue
                headers[line.substring(0, idx).trim().lowercase()] = line.substring(idx + 1).trim()
            }

            val length = headers["content-length"]?.toIntOrNull() ?: 0
            val body = if (length > 0) {
                val buf = CharArray(length)
                var read = 0
                while (read < length) {
                    val n = reader.read(buf, read, length - read)
                    if (n < 0) break
                    read += n
                }
                String(buf, 0, maxOf(read, 0))
            } else {
                ""
            }

            val (status, contentType, responseBody) = handler(method, path, headers, body)
            val payload = responseBody.toByteArray(StandardCharsets.UTF_8)
            val head = buildString {
                append("HTTP/1.1 ").append(status).append(' ').append(statusText(status)).append("\r\n")
                append("Content-Type: ").append(contentType).append("\r\n")
                append("Content-Length: ").append(payload.size).append("\r\n")
                append("Connection: close\r\n\r\n")
            }
            s.getOutputStream().write(head.toByteArray(StandardCharsets.UTF_8))
            s.getOutputStream().write(payload)
            s.getOutputStream().flush()
        }
    }

    private fun statusText(code: Int) = when (code) {
        200 -> "OK"
        401 -> "Unauthorized"
        404 -> "Not Found"
        429 -> "Too Many Requests"
        500 -> "Internal Server Error"
        else -> "Status"
    }

    override fun close() {
        running = false
        try { serverSocket.close() } catch (e: Exception) { /* ignore */ }
    }
}

/** Verifies the wire format the app speaks to any Anthropic compatible endpoint. */
class AnthropicClientTest {

    private lateinit var server: MiniHttpServer
    private var status = 200
    private var responseBody = ""
    private var requestBody = ""
    private var requestPath = ""
    private val requestHeaders = HashMap<String, String>()

    @Before
    fun setUp() {
        server = MiniHttpServer { _, path, headers, body ->
            requestPath = path
            requestBody = body
            requestHeaders.clear()
            requestHeaders.putAll(headers)
            Triple(status, "text/event-stream", responseBody)
        }
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun baseUrl() = "http://127.0.0.1:${server.port}"

    @Test
    fun resolvesEndpoints() {
        assertEquals("https://api.anthropic.com/v1/messages", AnthropicClient.endpointFor("https://api.anthropic.com"))
        assertEquals("https://api.anthropic.com/v1/messages", AnthropicClient.endpointFor("https://api.anthropic.com/"))
        assertEquals("https://api.anthropic.com/v1/messages", AnthropicClient.endpointFor("https://api.anthropic.com/v1"))
        assertEquals("https://api.anthropic.com/v1/messages", AnthropicClient.endpointFor("https://api.anthropic.com/v1/messages"))
        assertEquals("http://10.0.0.5:8080/v1/messages", AnthropicClient.endpointFor("http://10.0.0.5:8080"))
    }

    @Test
    fun buildsStandardMessagesBody() {
        val settings = AppSettings(baseUrl = "https://x.test", apiKey = "k", model = "my-model", systemPrompt = "be brief")
        val chat = Chat(
            messages = listOf(
                Message(role = "user", text = "hello"),
                Message(role = "assistant", text = ""),
                Message(role = "assistant", text = "hi"),
            )
        )
        val body = JSONObject(AnthropicClient.buildBody(chat, settings, stream = true).toString())
        assertEquals("my-model", body.getString("model"))
        assertTrue(body.getBoolean("stream"))
        assertEquals("be brief", body.getString("system"))
        val messages = body.getJSONArray("messages")
        assertEquals(2, messages.length())
        assertEquals("user", messages.getJSONObject(0).getString("role"))
        assertEquals("hello", messages.getJSONObject(0).getString("content"))
        assertEquals("assistant", messages.getJSONObject(1).getString("role"))
    }

    @Test
    fun streamsTextDeltas() = runBlocking {
        responseBody = buildString {
            append("event: message_start\ndata: {\"type\":\"message_start\",\"message\":{}}\n\n")
            append("event: content_block_delta\ndata: {\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"text_delta\",\"text\":\"Hel\"}}\n\n")
            append("event: content_block_delta\ndata: {\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"text_delta\",\"text\":\"lo\"}}\n\n")
            append("event: message_delta\ndata: {\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"end_turn\"}}\n\n")
        }
        val settings = AppSettings(baseUrl = baseUrl(), apiKey = "secret-key", model = "m")
        val chat = Chat(messages = listOf(Message(role = "user", text = "hi")))
        val out = StringBuilder()
        var stop: String? = null
        AnthropicClient.streamChat(chat, settings, onDelta = { delta ->
            when (delta) {
                is StreamDelta.Text -> out.append(delta.text)
                is StreamDelta.Status -> stop = delta.stopReason
                else -> Unit
            }
        })
        assertEquals("Hello", out.toString())
        assertEquals("end_turn", stop)
        assertEquals("/v1/messages", requestPath)
        assertEquals("secret-key", requestHeaders["x-api-key"])
        assertTrue(requestBody.contains("\"model\":\"m\""))
    }

    @Test
    fun mapsUnauthorizedToKeyHint() = runBlocking {
        status = 401
        responseBody = "{\"type\":\"error\",\"error\":{\"type\":\"authentication_error\",\"message\":\"invalid x-api-key\"}}"
        val settings = AppSettings(baseUrl = baseUrl(), apiKey = "bad", model = "m")
        val chat = Chat(messages = listOf(Message(role = "user", text = "hi")))
        val error = runCatching {
            AnthropicClient.streamChat(chat, settings, onDelta = { })
        }.exceptionOrNull()
        assertTrue(error is ApiException)
        assertEquals(401, (error as ApiException).statusCode)
        assertTrue(error.message!!.contains("invalid x-api-key"))
        assertTrue(error.message!!.contains("Kunci API"))
    }

    @Test
    fun sendsCustomHeaders() = runBlocking {
        responseBody = "event: message_stop\ndata: {\"type\":\"message_stop\"}\n\n"
        val settings = AppSettings(
            baseUrl = baseUrl(),
            apiKey = "k",
            model = "m",
            extraHeaders = "{\"Authorization\":\"Bearer abc\"}",
        )
        val chat = Chat(messages = listOf(Message(role = "user", text = "hi")))
        AnthropicClient.streamChat(chat, settings, onDelta = { })
        assertEquals("Bearer abc", requestHeaders["authorization"])
    }
}
