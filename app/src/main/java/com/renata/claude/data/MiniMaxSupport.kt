package com.renata.claude.data

import org.json.JSONObject

/**
 * Everything that is specific to the MiniMax models.
 *
 * MiniMax speaks both wire formats the app supports and it brings its own
 * reasoning controls: adaptive thinking and a thinking depth ("effort").
 * The rules differ per model, so they are collected in one place instead of
 * being spread over the two clients.
 *
 * Reference: platform.minimax.io docs for the Anthropic and OpenAI APIs.
 */
object MiniMaxSupport {

    /** "Don't send a depth, let the provider use its own default (max)." */
    const val EFFORT_AUTO = "auto"

    /** Thinking depths accepted by MiniMax, from the cheapest to the deepest. */
    val effortLevels = listOf(EFFORT_AUTO, "low", "medium", "high", "xhigh", "max")

    /** The label shown for every depth in the settings screen. */
    val effortLabels: Map<String, String> = mapOf(
        EFFORT_AUTO to "Otomatis (max)",
        "low" to "Rendah",
        "medium" to "Sedang",
        "high" to "Tinggi",
        "xhigh" to "Sangat tinggi",
        "max" to "Maksimal",
    )

    /** True when the model is served by MiniMax. */
    fun isMiniMax(model: String): Boolean = model.trim().lowercase().startsWith("minimax")

    /**
     * True when the model accepts a thinking depth.
     *
     * Only the MiniMax-M3.1 family reads `effort` / `reasoning_effort`; the
     * older M3 and M2.x models simply ignore it.
     */
    fun supportsEffort(model: String): Boolean {
        val m = model.trim().lowercase()
        return m.startsWith("minimax-m3.1") || m.contains("m3.1-flash")
    }

    /** True for the MiniMax-M2 family. */
    fun isM2(model: String): Boolean {
        val m = model.trim().lowercase()
        return m.startsWith("minimax-m2") || m.contains("m2.7") || m.contains("m2.5")
    }

    /**
     * True when the model always thinks, whatever the app asks for.
     *
     * MiniMax-M3.1-Flash answers `400` when the request asks for
     * `thinking.type=disabled`, and the M2.x models ignore the request and
     * keep thinking. In both cases the thinking tokens are counted against
     * `max_tokens`, so the limit has to leave room for them.
     */
    fun alwaysThinks(model: String): Boolean =
        isMiniMax(model) && (supportsEffort(model) || isM2(model))

    /**
     * The thinking depth to send, or null when the provider default applies.
     */
    fun effortValue(setting: String): String? {
        val value = setting.trim().lowercase()
        if (value.isEmpty() || value == EFFORT_AUTO) return null
        return if (value in effortLevels) value else null
    }

    /**
     * The `thinking` parameter for a MiniMax request.
     *
     * The behaviour differs per model and per wire format:
     *  - M3.1 always thinks, `adaptive` is the only safe value.
     *  - M2.x cannot be switched off, `adaptive` is accepted and ignored.
     *  - M3 thinks only when asked in the Anthropic format, and always
     *    thinks in the OpenAI format unless `disabled` is sent.
     */
    fun thinkingParam(settings: AppSettings): JSONObject? {
        if (!isMiniMax(settings.model)) return null
        if (alwaysThinks(settings.model)) return adaptive()
        if (settings.thinking) return adaptive()
        // MiniMax-M3 in the OpenAI format thinks by default, so the toggle
        // has to be translated into an explicit switch off.
        if (settings.apiFormat == Providers.FORMAT_OPENAI) {
            return JSONObject().put("type", "disabled")
        }
        return null
    }

    private fun adaptive(): JSONObject = JSONObject().put("type", "adaptive")

    /** True when the request will make the model think. */
    fun thinkingActive(settings: AppSettings): Boolean {
        val param = thinkingParam(settings) ?: return false
        return !param.optString("type").equals("disabled", ignoreCase = true)
    }

    /**
     * The number of tokens to reserve.
     *
     * Thinking tokens are counted against `max_tokens`, so a small limit ends
     * the answer with `stop_reason: max_tokens` and no text at all.
     */
    fun effectiveMaxTokens(settings: AppSettings): Int {
        val requested = maxOf(settings.maxTokens, 1)
        if (!isMiniMax(settings.model)) return requested
        if (!thinkingActive(settings)) return requested
        val deep = effortValue(settings.reasoningEffort) in listOf("high", "xhigh", "max")
        return maxOf(requested, if (deep) 16384 else 8192)
    }

    /**
     * The system prompt used when the user did not write one.
     *
     * It is what makes a plain MiniMax answer read like a professional
     * assistant instead of a chatty model.
     */
    val professionalPrompt: String = """
        Kamu adalah asisten AI profesional. Selalu jawab dalam bahasa yang dipakai pengguna.

        Cara menjawab:
        1. Pahami pertanyaan sampai tuntas. Kalau ada ambiguitas yang mengubah hasil, ambil asumsi paling wajar dan sebutkan singkat di awal.
        2. Langsung menjawab pada kalimat pertama, lalu kembangkan dengan struktur yang sesuai: langkah demi langkah, rumus, atau poin-poin singkat. Jangan mengulang kalimat pembuka atau meringkas jawaban sendiri di akhir.
        3. Bedakan dengan tegas antara fakta, hasil hitungan, dan dugaan. Kalau tidak yakin atau sumbernya tidak ditemukan, katakan terus terang apa yang belum diketahui dan bagaimana cara memverifikasinya.
        4. Sertakan satuan, asumsi, dan konteks pada jawaban teknis. Untuk kode, berikan kode yang siap dijalankan dan cara memakainya.
        5. Ringkas dan lugas: hindari basa-basi, pengulangan, dan daftar yang isinya cuma mengulang jawabanmu sendiri.
        6. Kalau jawabannya bergantung pada fakta yang berubah cepat, sebutkan waktu dan sumbernya.
    """.trimIndent()

    /**
     * The instructions that tell the model which skills it may use.
     *
     * They are only added when the tools are actually enabled, so the model
     * never asks for a tool it cannot get.
     */
    val toolPrompt: String = """
        Kamu punya beberapa alat yang berjalan di perangkat pengguna. Pakai alat yang tepat kalau memang perlu, satu per satu atau sekaligus:
        - get_datetime: tanggal dan jam saat ini, termasuk zona waktu. Selalu pakai alat ini kalau jawaban bergantung pada waktu sekarang, jangan menebak tanggal.
        - calculate: kalkulator aritmetika. Selalu pakai alat ini untuk setiap perhitungan angka, jangan menebak hasil hitungan. Masukkan ekspresinya apa adanya, misalnya (12 + 8) * 3.5.
        - fetch_url: membaca isi teks sebuah halaman web. Pakai untuk membaca sumber yang diberikan pengguna atau untuk memeriksa isi sebuah tautan.

        Aturan pemakaian alat:
        - Satu pesan bisa meminta beberapa alat sekaligus. Jangan memanggil alat yang sama berulang kali untuk hal yang sama.
        - Setelah hasil alat masuk, lanjutkan dengan jawaban akhir; jangan hanya menempel hasil mentah tanpa penjelasan.
        - Kalau alat gagal, katakan apa yang gagal dan jawab dengan yang kamu tahu.
    """.trimIndent()

    /** The system prompt to send for the given settings. */
    fun systemPromptFor(settings: AppSettings, toolsEnabled: Boolean = false): String {
        val custom = settings.systemPrompt.trim()
        if (custom.isNotEmpty()) return custom
        if (!isMiniMax(settings.model)) return ""
        if (!toolsEnabled) return professionalPrompt
        return professionalPrompt + "\n\n" + toolPrompt
    }
}
