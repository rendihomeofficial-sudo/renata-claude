package com.renata.claude.update

import android.content.Context

/**
 * Menyimpan waktu pemeriksaan pembaruan terakhir supaya aplikasi tidak
 * memeriksa otomatis setiap kali dibuka.
 */
object UpdatePrefs {

    private const val PREFS = "renata_claude_updates"
    private const val KEY_LAST_CHECK = "lastCheckAt"
    // Ambang batas 0ms agar selalu memeriksa pembaruan otomatis setiap kali aplikasi dibuka
    private const val DEFAULT_INTERVAL_MS = 0L

    fun shouldCheckAutomatically(context: Context, now: Long = System.currentTimeMillis()): Boolean {
        // Selalu true supaya aplikasi memeriksa manifest rilis setiap kali dibuka
        return true
    }

    fun markChecked(context: Context, now: Long = System.currentTimeMillis()) {
        prefs(context).edit().putLong(KEY_LAST_CHECK, now).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
