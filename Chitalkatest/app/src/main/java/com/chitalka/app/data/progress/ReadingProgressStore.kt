package com.chitalka.app.data.progress

import android.content.Context
import com.chitalka.app.data.model.ReadingProgress

class ReadingProgressStore(context: Context) {

    private val prefs = context.getSharedPreferences("reading_progress", Context.MODE_PRIVATE)

    fun load(mangaId: String): ReadingProgress {
        return ReadingProgress(
            mangaId = mangaId,
            chapterIndex = prefs.getInt(key(mangaId, "chapter"), 0),
            pageIndex = prefs.getInt(key(mangaId, "page"), 0)
        )
    }

    fun lastReadAt(mangaId: String): Long = prefs.getLong(key(mangaId, "last"), 0L)

    fun save(progress: ReadingProgress) {
        prefs.edit()
            .putInt(key(progress.mangaId, "chapter"), progress.chapterIndex)
            .putInt(key(progress.mangaId, "page"), progress.pageIndex)
            .putLong(key(progress.mangaId, "last"), System.currentTimeMillis())
            .apply()
    }

    fun clear(mangaId: String) {
        prefs.edit()
            .remove(key(mangaId, "chapter"))
            .remove(key(mangaId, "page"))
            .remove(key(mangaId, "last"))
            .apply()
    }

    /** Все ключи прогресса для экспорта */
    fun exportAll(): Map<String, Any> {
        val out = mutableMapOf<String, Any>()
        prefs.all.forEach { (k, v) ->
            if (v != null) out[k] = v
        }
        return out
    }

    fun importAll(data: Map<String, Any?>) {
        val ed = prefs.edit()
        data.forEach { (k, v) ->
            when (v) {
                is Number -> {
                    if (k.endsWith(":last")) ed.putLong(k, v.toLong())
                    else ed.putInt(k, v.toInt())
                }
                is String -> ed.putString(k, v)
            }
        }
        ed.apply()
    }

    private fun key(mangaId: String, field: String) = "$mangaId:$field"
}
