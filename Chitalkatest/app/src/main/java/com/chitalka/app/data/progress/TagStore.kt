package com.chitalka.app.data.progress

import android.content.Context
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/** Пользовательские теги, привязанные к mangaId (не в файле .chitalka). */
class TagStore(context: Context) {

    private val prefs = context.getSharedPreferences("user_tags", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val type = object : TypeToken<List<String>>() {}.type

    fun get(mangaId: String): List<String> {
        val raw = prefs.getString(mangaId, null) ?: return emptyList()
        return runCatching {
            (gson.fromJson<List<String>>(raw, type) ?: emptyList())
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
        }.getOrDefault(emptyList())
    }

    fun set(mangaId: String, tags: List<String>) {
        val cleaned = tags.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        prefs.edit {
            if (cleaned.isEmpty()) remove(mangaId)
            else putString(mangaId, gson.toJson(cleaned))
        }
    }

    fun add(mangaId: String, tag: String) {
        val t = tag.trim()
        if (t.isEmpty()) return
        set(mangaId, get(mangaId) + t)
    }

    fun remove(mangaId: String, tag: String) {
        set(mangaId, get(mangaId).filterNot { it.equals(tag, ignoreCase = true) })
    }

    fun clear(mangaId: String) {
        prefs.edit { remove(mangaId) }
    }

    fun exportAll(): Map<String, String> =
        prefs.all.mapNotNull { (k, v) -> (v as? String)?.let { k to it } }.toMap()

    fun importAll(data: Map<String, String>) {
        prefs.edit {
            data.forEach { (k, v) -> putString(k, v) }
        }
    }
}


