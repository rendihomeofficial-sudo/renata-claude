package com.renata.claude.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * The tools the model can call, and the code that runs them.
 *
 * Everything runs on the device: the clock, the calculator and the web page
 * reader. No account and no third party key are involved.
 */
object ToolRegistry {

    const val NAME_DATETIME = "get_datetime"
    const val NAME_CALCULATE = "calculate"
    const val NAME_FETCH_URL = "fetch_url"

    /** Longest page excerpt handed back to the model. */
    private const val MAX_FETCH_CHARS = 8000

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    /** Declarations of every tool, ready to be sent to a provider. */
    fun specs(): List<ToolSpec> = listOf(
        ToolSpec(
            name = NAME_DATETIME,
            description = "Ambil tanggal dan jam saat ini, termasuk zona waktu yang dipilih. " +
                "Pakai alat ini setiap kali jawaban bergantung pada waktu sekarang, jangan menebak tanggal.",
            inputSchema = JSONObject()
                .put("type", "object")
                .put(
                    "properties",
                    JSONObject()
                        .put(
                            "timezone",
                            JSONObject()
                                .put("type", "string")
                                .put("description", "Zona waktu IANA, contoh Asia/Jakarta. Kosong memakai zona waktu perangkat.")
                        )
                ),
        ),
        ToolSpec(
            name = NAME_CALCULATE,
            description = "Hitung nilai sebuah ekspresi aritmetika, contoh (12 + 8) * 3.5. " +
                "Pakai alat ini untuk setiap perhitungan angka, hasilnya selalu lebih bisa dipercaya daripada hitungan manual.",
            inputSchema = JSONObject()
                .put("type", "object")
                .put(
                    "properties",
                    JSONObject()
                        .put(
                            "expression",
                            JSONObject()
                                .put("type", "string")
                                .put("description", "Ekspresi aritmetika dengan operator + - * / % ^ dan fungsi seperti sqrt, sin, ln, min, max.")
                        )
                )
                .put("required", JSONArray().put("expression")),
        ),
        ToolSpec(
            name = NAME_FETCH_URL,
            description = "Ambil isi teks sebuah halaman web dan kirimkan sebagai bacaan. " +
                "Pakai alat ini untuk membaca sumber yang diberikan pengguna atau memeriksa isi sebuah tautan.",
            inputSchema = JSONObject()
                .put("type", "object")
                .put(
                    "properties",
                    JSONObject()
                        .put(
                            "url",
                            JSONObject()
                                .put("type", "string")
                                .put("description", "Alamat halaman, diawali http:// atau https://")
                        )
                        .put(
                            "max_chars",
                            JSONObject()
                                .put("type", "integer")
                                .put("description", "Batas karakter yang dibaca, maksimal 8000.")
                        )
                )
                .put("required", JSONArray().put("url")),
        ),
    )

    /** Runs one tool call and always answers with a result the model can read. */
    suspend fun execute(call: ToolCall): ToolResult = when (call.name) {
        NAME_DATETIME -> ToolResult(call.id, call.name, currentDateTime(call.argument("timezone")))
        NAME_CALCULATE -> calculate(call)
        NAME_FETCH_URL -> fetchUrl(call)
        else -> ToolResult(
            toolCallId = call.id,
            name = call.name,
            content = "Alat tidak dikenal: ${call.name}",
            isError = true,
        )
    }

    /** The date and time of the device, or of the requested time zone. */
    fun currentDateTime(timeZoneId: String): String {
        val zone = if (timeZoneId.isBlank()) {
            TimeZone.getDefault()
        } else {
            TimeZone.getTimeZone(timeZoneId.trim())
        }
        val now = Calendar.getInstance(zone)
        fun two(value: Int) = value.toString().padStart(2, '0')
        val date = "${now.get(Calendar.YEAR)}-${two(now.get(Calendar.MONTH) + 1)}-${two(now.get(Calendar.DAY_OF_MONTH))}"
        val time = "${two(now.get(Calendar.HOUR_OF_DAY))}:${two(now.get(Calendar.MINUTE))}:${two(now.get(Calendar.SECOND))}"
        val offset = zone.getOffset(now.timeInMillis) / 60000
        val sign = if (offset < 0) "-" else "+"
        val absolute = abs(offset)
        val utc = "UTC$sign${two(absolute / 60)}:${two(absolute % 60)}"
        return "$date $time ($utc, zona ${zone.id})"
    }

    private fun calculate(call: ToolCall): ToolResult {
        val expression = call.argument("expression").ifBlank { call.argument("expr") }
        if (expression.isBlank()) {
            return ToolResult(call.id, call.name, "Ekspresi tidak boleh kosong.", isError = true)
        }
        return try {
            val value = MathExpression.evaluate(expression)
            ToolResult(call.id, call.name, "${formatNumber(value)} = $expression")
        } catch (e: Exception) {
            ToolResult(call.id, call.name, "Ekspresi tidak bisa dihitung: ${e.message}", isError = true)
        }
    }

    private suspend fun fetchUrl(call: ToolCall): ToolResult = withContext(Dispatchers.IO) {
        val url = call.argument("url").trim()
        if (url.isEmpty()) {
            return@withContext ToolResult(call.id, call.name, "URL wajib diisi.", isError = true)
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return@withContext ToolResult(
                call.id,
                call.name,
                "URL harus diawali http:// atau https://",
                isError = true,
            )
        }
        val limit = call.argument("max_chars").toIntOrNull()?.coerceIn(200, MAX_FETCH_CHARS)
            ?: MAX_FETCH_CHARS
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "RenataClaude/1.0 (Android)")
                .build()
            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext ToolResult(
                        call.id,
                        call.name,
                        "Halaman gagal dibuka: HTTP ${response.code}",
                        isError = true,
                    )
                }
                val text = htmlToText(body)
                if (text.isBlank()) {
                    ToolResult(call.id, call.name, "Halaman $url tidak punya teks yang bisa dibaca.")
                } else {
                    ToolResult(
                        call.id,
                        call.name,
                        "URL: $url\n\n${text.take(limit)}",
                    )
                }
            }
        } catch (e: Exception) {
            ToolResult(call.id, call.name, "Gagal membuka halaman: ${e.message}", isError = true)
        }
    }

    /** Turns an HTML document into readable plain text. */
    fun htmlToText(html: String): String {
        var text = html
        text = text.replace(Regex("(?is)<!--.*?-->"), " ")
        text = text.replace(Regex("(?is)<(script|style|noscript)[^>]*>.*?</\\1>"), " ")
        text = text.replace(Regex("(?is)<br\\s*/?>"), "\n")
        text = text.replace(Regex("(?is)</(p|div|section|article|li|tr|h[1-6])>"), "\n")
        text = text.replace(Regex("(?is)<[^>]+>"), " ")
        text = text
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
        text = text.replace(Regex("[ \\t\\u00A0]+"), " ")
        text = text.replace(Regex("\\n\\s*\\n+"), "\n")
        return text.trim()
    }

    /** Formats a number without a useless tail of zeros. */
    fun formatNumber(value: Double): String {
        if (value.isNaN()) return "NaN"
        if (value.isInfinite()) return if (value > 0) "Infinity" else "-Infinity"
        if (value == Math.floor(value) && Math.abs(value) < 1e15) {
            return value.toLong().toString()
        }
        val rounded = Math.round(value * 1e10) / 1e10
        return rounded.toString()
    }
}

/**
 * A small arithmetic parser.
 *
 * It understands numbers, the operators `+ - * / % ^`, parentheses, the
 * constants `pi` and `e`, and the usual math functions. Only arithmetic is
 * allowed, so there is no way to reach the file system or the network.
 */
internal object MathExpression {

    fun evaluate(source: String): Double {
        val parser = Parser(source)
        val value = parser.parseExpression()
        parser.skipSpaces()
        if (!parser.atEnd()) {
            throw IllegalArgumentException("karakter tak terduga di posisi ${parser.position}")
        }
        return value
    }

    private class Parser(private val source: String) {

        var position = 0

        fun atEnd(): Boolean = position >= source.length

        fun skipSpaces() {
            while (position < source.length && source[position].isWhitespace()) position++
        }

        fun parseExpression(): Double {
            var value = parseTerm()
            while (true) {
                skipSpaces()
                if (atEnd()) return value
                val c = source[position]
                if (c == '+') {
                    position++
                    value += parseTerm()
                } else if (c == '-') {
                    position++
                    value -= parseTerm()
                } else {
                    return value
                }
            }
        }

        private fun parseTerm(): Double {
            var value = parseUnary()
            while (true) {
                skipSpaces()
                if (atEnd()) return value
                when (source[position]) {
                    '*' -> {
                        position++
                        value *= parseUnary()
                    }
                    '/' -> {
                        position++
                        val divisor = parseUnary()
                        if (divisor == 0.0) throw IllegalArgumentException("pembagian dengan nol")
                        value /= divisor
                    }
                    '%' -> {
                        position++
                        val divisor = parseUnary()
                        if (divisor == 0.0) throw IllegalArgumentException("sisa bagi dengan nol")
                        value %= divisor
                    }
                    else -> return value
                }
            }
        }

        private fun parseUnary(): Double {
            skipSpaces()
            if (atEnd()) throw IllegalArgumentException("ekspresi berakhir mendadak")
            val c = source[position]
            if (c == '-') {
                position++
                return -parseUnary()
            }
            if (c == '+') {
                position++
                return parseUnary()
            }
            return parsePower()
        }

        private fun parsePower(): Double {
            val base = parsePrimary()
            skipSpaces()
            if (!atEnd() && source[position] == '^') {
                position++
                val exponent = parseUnary()
                return base.pow(exponent)
            }
            return base
        }

        private fun parsePrimary(): Double {
            skipSpaces()
            if (atEnd()) throw IllegalArgumentException("ekspresi berakhir mendadak")
            val c = source[position]
            if (c == '(') {
                position++
                val value = parseExpression()
                skipSpaces()
                if (atEnd() || source[position] != ')') {
                    throw IllegalArgumentException("kurung tidak tertutup")
                }
                position++
                return value
            }
            if (c.isDigit() || c == '.') return parseNumber()
            if (c.isLetter()) return parseIdentifier()
            throw IllegalArgumentException("karakter '$c' tidak dikenali")
        }

        private fun parseNumber(): Double {
            val start = position
            while (!atEnd() && (source[position].isDigit() || source[position] == '.')) position++
            val text = source.substring(start, position)
            return text.toDoubleOrNull() ?: throw IllegalArgumentException("angka '$text' tidak valid")
        }

        private fun parseIdentifier(): Double {
            val start = position
            while (!atEnd() && source[position].isLetter()) position++
            val name = source.substring(start, position).lowercase()
            skipSpaces()
            if (!atEnd() && source[position] == '(') {
                position++
                val args = mutableListOf<Double>()
                skipSpaces()
                if (!atEnd() && source[position] == ')') {
                    position++
                } else {
                    while (true) {
                        args.add(parseExpression())
                        skipSpaces()
                        if (atEnd()) throw IllegalArgumentException("kurung fungsi tidak ditutup")
                        when (source[position]) {
                            ',' -> position++
                            ')' -> {
                                position++
                                return applyFunction(name, args)
                            }
                            else -> throw IllegalArgumentException("karakter tak terduga di fungsi $name")
                        }
                    }
                }
                return applyFunction(name, args)
            }
            return when (name) {
                "pi", "π" -> Math.PI
                "e" -> Math.E
                "tau" -> 2.0 * Math.PI
                else -> throw IllegalArgumentException("nama '$name' tidak dikenal")
            }
        }

        private fun applyFunction(name: String, args: List<Double>): Double {
            fun one() = args.firstOrNull() ?: throw IllegalArgumentException("fungsi $name butuh satu argumen")
            fun two() {
                if (args.size < 2) throw IllegalArgumentException("fungsi $name butuh dua argumen")
            }
            return when (name) {
                "sqrt" -> sqrt(one())
                "abs" -> abs(one())
                "round" -> round(one())
                "floor" -> floor(one())
                "ceil" -> ceil(one())
                "sin" -> sin(one())
                "cos" -> cos(one())
                "tan" -> tan(one())
                "asin" -> asin(one())
                "acos" -> acos(one())
                "atan" -> atan(one())
                "ln" -> ln(one())
                "log" -> log10(one())
                "log2" -> log2(one())
                "exp" -> exp(one())
                "min" -> {
                    if (args.isEmpty()) throw IllegalArgumentException("fungsi min butuh argumen")
                    args.min()
                }
                "max" -> {
                    if (args.isEmpty()) throw IllegalArgumentException("fungsi max butuh argumen")
                    args.max()
                }
                "pow" -> {
                    two()
                    args[0].pow(args[1])
                }
                "hypot" -> {
                    two()
                    kotlin.math.hypot(args[0], args[1])
                }
                else -> throw IllegalArgumentException("fungsi '$name' tidak dikenal")
            }
        }
    }
}
