package com.chitalka.app.data.progress

import android.content.Context
import androidx.core.content.edit
import com.chitalka.app.data.model.Bookmark
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID

class BookmarkStore(context: Context) {

    private val prefs = context.getSharedPreferences("bookmarks", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val type = object : TypeToken<List<Bookmark>>() {}.type

    fun forManga(mangaId: String): List<Bookmark> =
        all().filter { it.mangaId == mangaId }.sortedByDescending { it.createdAt }

    fun add(mangaId: String, chapterIndex: Int, pageIndex: Int, label: String): Bookmark {
        val bookmark = Bookmark(
            id = UUID.randomUUID().toString(),
            mangaId = mangaId,
            chapterIndex = chapterIndex,
            pageIndex = pageIndex,
            label = label,
            createdAt = System.currentTimeMillis()
        )
        save(all() + bookmark)
        return bookmark
    }

    fun remove(id: String) {
        save(all().filterNot { it.id == id })
    }

    fun removeForManga(mangaId: String) {
        save(all().filterNot { it.mangaId == mangaId })
    }

    fun exportJson(): String = gson.toJson(all())

    fun importJson(json: String) {
        val items: List<Bookmark> = gson.fromJson(json, type) ?: emptyList()
        save((all() + items).distinctBy { it.id })
    }

    private fun all(): List<Bookmark> {
        val json = prefs.getString(KEY, "[]") ?: "[]"
        return gson.fromJson(json, type) ?: emptyList()
    }

    private fun save(items: List<Bookmark>) {
        prefs.edit { putString(KEY, gson.toJson(items)) }
    }

    companion object {
        private const val KEY = "items"
    }
}
