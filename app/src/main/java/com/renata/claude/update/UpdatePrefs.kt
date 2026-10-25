package com.renata.claude.update

import android.content.Context

/**
 * Menyimpan waktu pemeriksaan pembaruan terakhir supaya aplikasi tidak
 * memeriksa otomatis setiap kali dibuka.
 */
object UpdatePrefs {

    private const val PREFS = "renata_claude_updates"
    private const val KEY_LAST_CHECK = "lastCheckAt"
    private const val ONE_DAY_MS = 24L * 60 * 60 * 1000

    fun shouldCheckAutomatically(context: Context, now: Long = System.currentTimeMillis()): Boolean {
        val last = prefs(context).getLong(KEY_LAST_CHECK, 0L)
        return now - last >= ONE_DAY_MS
    }

    fun markChecked(context: Context, now: Long = System.currentTimeMillis()) {
        prefs(context).edit().putLong(KEY_LAST_CHECK, now).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
