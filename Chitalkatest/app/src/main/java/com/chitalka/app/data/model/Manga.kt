package com.chitalka.app.data.model

/**
 * Gson часто кладёт null в поля с default-значениями Kotlin.
 * Поэтому списки и строки читаем через безопасные геттеры.
 */
data class MangaMeta(
    val id: String? = null,
    val title: String? = null,
    val author: String? = null,
    val publishedAt: String? = null,
    val description: String? = null,
    val language: String? = null,
    val cover: String? = null,
    val categories: List<String>? = null,
    val tags: List<String>? = null,
    val chapters: List<ChapterMeta>? = null
) {
    fun safeId(): String = id?.takeIf { it.isNotBlank() } ?: "unknown"
    fun safeTitle(): String = title?.takeIf { it.isNotBlank() } ?: "Без названия"
    fun safeAuthor(): String = author.orEmpty()
    fun safePublished(): String = publishedAt.orEmpty()
    fun safeDescription(): String = description.orEmpty()
    fun safeCover(): String = cover?.takeIf { it.isNotBlank() } ?: "cover.jpg"
    fun safeChapters(): List<ChapterMeta> = chapters.orEmpty()

    fun allLabels(): List<String> {
        return (categories.orEmpty() + tags.orEmpty())
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
    }
}

data class ChapterMeta(
    val id: String? = null,
    val index: Int = 0,
    val title: String? = null,
    val path: String? = null
) {
    fun safeTitle(fallbackIndex: Int): String =
        title?.takeIf { it.isNotBlank() } ?: "Глава ${fallbackIndex + 1}"

    fun safePath(): String = path.orEmpty()
}

data class MangaFile(
    val uri: android.net.Uri,
    val displayName: String,
    val meta: MangaMeta
)

data class ReadingProgress(
    val mangaId: String,
    val chapterIndex: Int,
    val pageIndex: Int
)

data class Bookmark(
    val id: String,
    val mangaId: String,
    val chapterIndex: Int,
    val pageIndex: Int,
    val label: String,
    val createdAt: Long
)
