package com.chitalka.app.data.progress

import android.content.Context
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class MangaExtras(
    val shelf: String = SHELF_READING, // reading / planned / done / archive
    val rating: Int = 0, // 0..5
    val note: String = "",
    val favorite: Boolean = false,
    val hidden: Boolean = false,
    val titleOverride: String = "",
    val authorOverride: String = "",
    val colorLabel: String = "" // hex or empty
) {
    companion object {
        const val SHELF_READING = "reading"
        const val SHELF_PLANNED = "planned"
        const val SHELF_DONE = "done"
        const val SHELF_ARCHIVE = "archive"
    }
}

class MetaStore(context: Context) {
    private val prefs = context.getSharedPreferences("manga_extras", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val type = object : TypeToken<MangaExtras>() {}.type

    fun get(mangaId: String): MangaExtras {
        val raw = prefs.getString(mangaId, null) ?: return MangaExtras()
        return runCatching { gson.fromJson<MangaExtras>(raw, type) }.getOrDefault(MangaExtras())
    }

    fun set(mangaId: String, extras: MangaExtras) {
        prefs.edit { putString(mangaId, gson.toJson(extras)) }
    }

    fun update(mangaId: String, block: (MangaExtras) -> MangaExtras) {
        set(mangaId, block(get(mangaId)))
    }

    fun clear(mangaId: String) {
        prefs.edit { remove(mangaId) }
    }

    fun exportAll(): Map<String, String> =
        prefs.all.mapNotNull { (k, v) -> (v as? String)?.let { k to it } }.toMap()

    fun importAll(data: Map<String, String>) {
        prefs.edit { data.forEach { (k, v) -> putString(k, v) } }
    }
}
