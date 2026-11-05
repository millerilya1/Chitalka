package com.chitalka.app.data.library

import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import com.chitalka.app.data.format.ArchiveStore
import com.chitalka.app.data.model.MangaFile

class LibraryRepository(
    private val context: Context,
    private val archives: ArchiveStore
) {

    private val prefs = context.getSharedPreferences("library", Context.MODE_PRIVATE)

    fun all(): List<MangaFile> {
        return uris().mapNotNull { uri ->
            runCatching {
                val meta = archives.open(uri).readMeta()
                MangaFile(
                    uri = uri,
                    displayName = uri.lastPathSegment ?: "manga",
                    meta = meta
                )
            }.getOrNull()
        }
    }

    fun add(uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        val next = (uris() + uri).distinct()
        prefs.edit { putStringSet(KEY, next.map(Uri::toString).toSet()) }
    }

    /** Убрать тайтл из библиотеки (файл на диске не трогаем). */
    fun remove(uri: Uri) {
        val next = uris().filterNot { it.toString() == uri.toString() }
        prefs.edit { putStringSet(KEY, next.map(Uri::toString).toSet()) }
        runCatching {
            context.contentResolver.releasePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
    }

    fun removeById(mangaId: String) {
        all().filter { it.meta.safeId() == mangaId }.forEach { remove(it.uri) }
    }

    fun find(mangaId: String): MangaFile? =
        all().firstOrNull { it.meta.safeId() == mangaId }

    fun exportUris(): List<String> = uris().map(Uri::toString)

    fun importUris(raw: List<String>) {
        val next = (uris() + raw.map(Uri::parse)).distinct()
        prefs.edit { putStringSet(KEY, next.map(Uri::toString).toSet()) }
    }

    private fun uris(): List<Uri> =
        prefs.getStringSet(KEY, emptySet()).orEmpty().map(Uri::parse)

    companion object {
        private const val KEY = "uris"
    }
}
