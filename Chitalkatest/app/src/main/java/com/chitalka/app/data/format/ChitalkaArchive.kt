package com.chitalka.app.data.format

import android.graphics.BitmapFactory
import com.chitalka.app.data.model.ChapterMeta
import com.chitalka.app.data.model.MangaMeta
import com.google.gson.Gson
import java.io.Closeable
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * .chitalka / .cbz / zip: meta.json если есть, иначе синтез из структуры папок.
 */
class ChitalkaArchive(
    private val zip: ZipFile,
    private val displayName: String = "manga"
) : Closeable {

    private val gson = Gson()
    private val entries: Map<String, ZipEntry> = zip.entries().asSequence().associateBy {
        it.name.replace('\\', '/')
    }

    private val metaCache: MangaMeta by lazy { loadMeta() }

    private fun loadMeta(): MangaMeta {
        val json = readEntry("meta.json")?.decodeToString()
        if (json != null) {
            return gson.fromJson(json, MangaMeta::class.java)
                ?: error("Не удалось разобрать meta.json")
        }
        return synthesizeMeta()
    }

    /** CBZ / голый ZIP: каждая папка верхнего уровня = глава, либо все картинки = 1 глава */
    private fun synthesizeMeta(): MangaMeta {
        val images = entries.keys.filter { !it.endsWith("/") && isImage(it) }.sorted()
        if (images.isEmpty()) error("В архиве нет картинок")

        val byDir = images.groupBy { path ->
            val parts = path.split('/')
            if (parts.size >= 2) parts.dropLast(1).joinToString("/") else ""
        }

        val chapters = if (byDir.size == 1 && byDir.keys.first().isEmpty()) {
            listOf(ChapterMeta(id = "ch-001", index = 1, title = "Глава 1", path = ""))
        } else {
            byDir.keys.filter { it.isNotEmpty() }.sorted().mapIndexed { i, dir ->
                ChapterMeta(
                    id = "ch-${i + 1}",
                    index = i + 1,
                    title = dir.substringAfterLast('/').ifBlank { "Глава ${i + 1}" },
                    path = dir
                )
            }.ifEmpty {
                listOf(ChapterMeta(id = "ch-001", index = 1, title = "Глава 1", path = ""))
            }
        }

        val cover = images.firstOrNull { it.contains("cover", true) } ?: images.first()
        val title = displayName
            .substringAfterLast('/')
            .substringBeforeLast('.')
            .ifBlank { "Без названия" }

        return MangaMeta(
            id = "zip-${title.hashCode()}",
            title = title,
            author = "",
            publishedAt = "",
            description = "Импортировано из архива (без meta.json)",
            cover = cover,
            chapters = chapters
        )
    }

    fun readMeta(): MangaMeta = metaCache

    fun readCoverBytes(): ByteArray? {
        val name = metaCache.safeCover()
        return readEntry(name) ?: imagesFirst()
    }

    private fun imagesFirst(): ByteArray? {
        val first = entries.keys.filter { isImage(it) }.sorted().firstOrNull() ?: return null
        return readEntry(first)
    }

    fun decodeCover() = readCoverBytes()?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }

    fun listPages(chapterPath: String): List<String> {
        val images = entries.keys.filter { !it.endsWith("/") && isImage(it) }
        if (chapterPath.isBlank()) {
            // все картинки в корне или все подряд
            val rooted = images.filter { !it.contains('/') }
            return (rooted.ifEmpty { images }).sorted()
        }
        val prefix = chapterPath.trimEnd('/') + "/"
        return images.filter { it.startsWith(prefix) }.sorted()
    }

    fun readPage(entryName: String): ByteArray =
        readEntry(entryName) ?: error("Страница не найдена: $entryName")

    fun readEntry(wanted: String): ByteArray? {
        val entry = entries[wanted] ?: entries[wanted.replace('\\', '/')] ?: return null
        return zip.getInputStream(entry).use { it.readBytes() }
    }

    override fun close() = zip.close()

    companion object {
        private val IMAGE_EXT = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp")

        fun isImage(name: String): Boolean {
            val ext = name.substringAfterLast('.', "").lowercase()
            return ext in IMAGE_EXT
        }
    }
}
