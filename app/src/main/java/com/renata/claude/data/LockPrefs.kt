package com.renata.claude.data

import android.content.Context
import android.content.SharedPreferences
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Hashing PIN, ditulis tanpa kelas Android supaya bisa diuji di JVM.
 *
 * PIN tidak pernah disimpan apa adanya. Yang tersimpan hanya garam acak dan
 * hasil PBKDF2, jadi berkas preferensi tidak langsung memberi tahu PIN.
 */
object PinHasher {

    private const val ITERATIONS = 120_000
    private const val KEY_LENGTH = 256
    private const val SALT_BYTES = 16

    /** Garam acak baru, ditulis sebagai heksadesimal. */
    fun newSalt(): String {
        val bytes = ByteArray(SALT_BYTES)
        SecureRandom().nextBytes(bytes)
        return bytes.toHex()
    }

    /** Hash PIN dengan garam tertentu. */
    fun hash(pin: String, salt: String): String {
        val spec = PBEKeySpec(pin.toCharArray(), salt.hexToBytes(), ITERATIONS, KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
        return factory.generateSecret(spec).encoded.toHex()
    }

    /** True bila [pin] cocok dengan hash tersimpan. */
    fun matches(pin: String, salt: String, expected: String): Boolean {
        if (expected.isBlank() || salt.isBlank()) return false
        val actual = hash(pin, salt)
        return constantTimeEquals(actual, expected)
    }

    /** Perbandingan waktu tetap supaya tidak bocor lewat waktu. */
    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var diff = 0
        for (i in a.indices) {
            diff = diff or (a[i].code xor b[i].code)
        }
        return diff == 0
    }
}

/**
 * Menyimpan PIN kunci aplikasi di perangkat.
 *
 * Tidak ada akun dan tidak ada cadangan: PIN yang lupa hanya bisa dihapus
 * dengan menghapus data aplikasi dari pengaturan sistem.
 */
object LockPrefs {

    private const val PREFS_NAME = "renata_claude_lock"
    private const val KEY_SALT = "pinSalt"
    private const val KEY_HASH = "pinHash"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /** True bila sudah ada PIN yang tersimpan. */
    fun hasPin(): Boolean {
        if (!::prefs.isInitialized) return false
        return prefs.getString(KEY_HASH, "").orEmpty().isNotBlank()
    }

    /** Menyimpan PIN baru, mengganti PIN lama. */
    fun setPin(pin: String) {
        val salt = PinHasher.newSalt()
        val hash = PinHasher.hash(pin, salt)
        prefs.edit()
            .putString(KEY_SALT, salt)
            .putString(KEY_HASH, hash)
            .apply()
    }

    /** Menghapus PIN. Aplikasi tidak lagi terkunci. */
    fun clearPin() {
        prefs.edit()
            .remove(KEY_SALT)
            .remove(KEY_HASH)
            .apply()
    }

    /** True bila [pin] cocok dengan PIN yang tersimpan. */
    fun verify(pin: String): Boolean {
        if (!::prefs.isInitialized) return false
        val salt = prefs.getString(KEY_SALT, "").orEmpty()
        val hash = prefs.getString(KEY_HASH, "").orEmpty()
        return PinHasher.matches(pin, salt, hash)
    }
}

private fun ByteArray.toHex(): String {
    val out = StringBuilder(size * 2)
    for (b in this) {
        val v = b.toInt() and 0xFF
        out.append(HEX[v ushr 4])
        out.append(HEX[v and 0x0F])
    }
    return out.toString()
}

private fun String.hexToBytes(): ByteArray {
    val cleaned = trim()
    val out = ByteArray(cleaned.length / 2)
    for (i in out.indices) {
        val hi = Character.digit(cleaned[i * 2], 16)
        val lo = Character.digit(cleaned[i * 2 + 1], 16)
        if (hi < 0 || lo < 0) throw IllegalArgumentException("Garam tidak valid")
        out[i] = ((hi shl 4) or lo).toByte()
    }
    return out
}

private const val HEX = "0123456789abcdef"
