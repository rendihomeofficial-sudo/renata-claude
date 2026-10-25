package com.renata.claude.update

import android.content.Context

/**
 * Menyimpan waktu pemeriksaan pembaruan terakhir supaya aplikasi tidak
 * memeriksa otomatis setiap kali dibuka.
 */
object UpdatePrefs {

    private const val PREFS = "renata_claude_updates"
    private const val KEY_LAST_CHECK = "lastCheckAt"
    // ambang batas 1 jam (atau periksa setiap startup bila ada versi baru)
    private const val DEFAULT_INTERVAL_MS = 60 * 60 * 1000L

    fun shouldCheckAutomatically(context: Context, now: Long = System.currentTimeMillis()): Boolean {
        val last = prefs(context).getLong(KEY_LAST_CHECK, 0L)
        return now - last >= DEFAULT_INTERVAL_MS
    }

    fun markChecked(context: Context, now: Long = System.currentTimeMillis()) {
        prefs(context).edit().putLong(KEY_LAST_CHECK, now).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
