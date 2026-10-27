package com.renata.claude.update

import android.content.Context

/**
 * Menyimpan waktu pemeriksaan pembaruan terakhir supaya aplikasi tidak
 * memeriksa otomatis setiap kali dibuka.
 */
object UpdatePrefs {

    private const val PREFS = "renata_claude_updates"
    private const val KEY_LAST_CHECK = "lastCheckAt"
    // Jeda minimum antar pemeriksaan agar aplikasi yang sering dibuka ke recents
    // tidak membanjiri server manifest.
    private const val DEFAULT_INTERVAL_MS = 5 * 60 * 1000L

    fun shouldCheckAutomatically(context: Context, now: Long = System.currentTimeMillis()): Boolean {
        val last = prefs(context).getLong(KEY_LAST_CHECK, 0L)
        return last <= 0L || now - last >= DEFAULT_INTERVAL_MS
    }

    fun markChecked(context: Context, now: Long = System.currentTimeMillis()) {
        prefs(context).edit().putLong(KEY_LAST_CHECK, now).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
