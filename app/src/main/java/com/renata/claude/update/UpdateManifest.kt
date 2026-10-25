package com.renata.claude.update

import org.json.JSONObject

/**
 * Deskripsi satu rilis aplikasi, dibaca dari berkas `version.json`.
 *
 * Contoh isi berkas:
 * ```json
 * {
 *   "versionName": "1.2.0",
 *   "versionCode": 3,
 *   "downloadUrl": "https://github.com/.../Renata-Claude-1.2.0.apk",
 *   "releaseNotes": "Perbaikan kecil.",
 *   "publishedAt": "2026-10-25"
 * }
 * ```
 */
data class AppRelease(
    val versionName: String,
    val versionCode: Int,
    val downloadUrl: String,
    val releaseNotes: String = "",
    val publishedAt: String = "",
) {
    /** Rilis dianggap lebih baru bila kode versinya lebih besar dari yang terpasang. */
    fun isNewerThan(currentVersionCode: Int): Boolean = versionCode > currentVersionCode
}

/** Membaca dan memvalidasi manifest pembaruan. */
object UpdateManifest {

    /**
     * Mengubah isi `version.json` menjadi [AppRelease].
     * Melempar [IllegalArgumentException] bila isinya tidak lengkap atau tidak masuk akal.
     */
    fun parse(json: String): AppRelease {
        val obj = try {
            JSONObject(json)
        } catch (e: Exception) {
            throw IllegalArgumentException("Manifest bukan JSON yang valid.", e)
        }
        val versionName = obj.optString("versionName").trim()
        if (versionName.isEmpty()) throw IllegalArgumentException("versionName tidak ditemukan.")
        val versionCode = obj.optInt("versionCode", 0)
        if (versionCode <= 0) throw IllegalArgumentException("versionCode harus angka positif.")
        val downloadUrl = obj.optString("downloadUrl").trim()
        if (downloadUrl.isEmpty()) throw IllegalArgumentException("downloadUrl tidak ditemukan.")
        if (!downloadUrl.startsWith("http://") && !downloadUrl.startsWith("https://")) {
            throw IllegalArgumentException("downloadUrl harus tautan http atau https.")
        }
        return AppRelease(
            versionName = versionName,
            versionCode = versionCode,
            downloadUrl = downloadUrl,
            releaseNotes = obj.optString("releaseNotes").trim(),
            publishedAt = obj.optString("publishedAt").trim(),
        )
    }

    /** Versi [parse] yang mengembalikan null bila manifest tidak valid. */
    fun parseOrNull(json: String): AppRelease? = runCatching { parse(json) }.getOrNull()
}
