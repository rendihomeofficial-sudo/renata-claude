package com.renata.claude.data

import android.content.Context
import org.json.JSONObject
import java.io.File

/** Stores conversations as one JSON file per chat under the app's private files dir. */
object ChatStore {

    private const val DIR = "chats"
    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private fun dir(): File = File(appContext.filesDir, DIR).apply { mkdirs() }

    fun list(): List<Chat> {
        val files = dir().listFiles { f -> f.extension == "json" } ?: return emptyList()
        return files.mapNotNull { read(it) }
            .sortedWith(compareByDescending<Chat> { it.pinned }.thenByDescending { it.updatedAt })
    }

    fun read(file: File): Chat? = try {
        val text = file.readText()
        if (text.isBlank()) null else Chat.fromJson(JSONObject(text))
    } catch (e: Exception) {
        null
    }

    fun save(chat: Chat) {
        try {
            File(dir(), "${chat.id}.json").writeText(chat.toJson().toString())
        } catch (e: Exception) {
            // ignore write errors, in-memory state stays authoritative
        }
    }

    fun delete(chatId: String) {
        try {
            File(dir(), "$chatId.json").delete()
        } catch (e: Exception) {
        }
    }

    fun deleteAll() {
        try {
            dir().listFiles()?.forEach { it.delete() }
        } catch (e: Exception) {
        }
    }
}
