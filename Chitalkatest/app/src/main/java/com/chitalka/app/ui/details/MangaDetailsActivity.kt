package com.chitalka.app.ui.details

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.chitalka.app.ChitalkaApp
import com.chitalka.app.R
import com.chitalka.app.data.progress.MangaExtras
import com.chitalka.app.databinding.ActivityMangaDetailsBinding
import com.chitalka.app.databinding.ItemBookmarkBinding
import com.chitalka.app.databinding.ItemChapterBinding
import com.chitalka.app.ui.applySystemBarInsets
import com.chitalka.app.ui.reader.ReaderActivity
import com.google.android.material.chip.Chip

class MangaDetailsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMangaDetailsBinding
    private lateinit var uri: Uri
    private lateinit var mangaId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMangaDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarInsets(bottom = true)

        val app = application as ChitalkaApp
        @Suppress("DEPRECATION")
        uri = intent.getParcelableExtra(EXTRA_URI) ?: run { finish(); return }

        val archive = runCatching { app.archives.open(uri) }.getOrNull() ?: run { finish(); return }
        val meta = runCatching { archive.readMeta() }.getOrNull() ?: run { finish(); return }
        mangaId = meta.safeId()
        val chapters = meta.safeChapters()
        val progress = app.progress.load(mangaId)
        val extras = app.extras.get(mangaId)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        val title = extras.titleOverride.ifBlank { meta.safeTitle() }
        binding.toolbar.title = title

        runCatching { binding.cover.setImageBitmap(app.archives.cover(uri)) }
        binding.title.text = title
        binding.author.text = extras.authorOverride.ifBlank { meta.safeAuthor() }
        binding.published.text = meta.safePublished()
        binding.chapters.text = "Глав: ${chapters.size}"
        binding.description.text = meta.safeDescription().ifBlank { "Нет описания" }

        val fileLabels = meta.allLabels()
        if (fileLabels.isNotEmpty()) {
            binding.categories.visibility = View.VISIBLE
            binding.categories.text = fileLabels.joinToString(" · ")
        } else binding.categories.visibility = View.GONE

        val totalChapters = chapters.size.coerceAtLeast(1)
        binding.continueLabel.text =
            "Прогресс: глава ${progress.chapterIndex + 1}/$totalChapters, стр. ${progress.pageIndex + 1}"

        binding.readButton.setOnClickListener {
            startActivity(ReaderActivity.intent(this, uri, mangaId))
        }
        binding.deleteButton.setOnClickListener { confirmDelete() }
        binding.addTagButton.setOnClickListener { promptAddTag() }

        // Meta actions via long menu on title
        binding.title.setOnClickListener { showMetaMenu() }
        binding.author.setOnClickListener { showMetaMenu() }
        binding.continueLabel.setOnClickListener { showMetaMenu() }

        renderTags()
        renderChapters(chapters, progress.chapterIndex, progress.pageIndex)
        applyExtrasUi(extras)
    }

    private fun applyExtrasUi(extras: MangaExtras) {
        val shelfName = when (extras.shelf) {
            MangaExtras.SHELF_PLANNED -> "В планах"
            MangaExtras.SHELF_DONE -> "Готово"
            MangaExtras.SHELF_ARCHIVE -> "Архив"
            else -> "Читаю"
        }
        val stars = if (extras.rating > 0) " ★".repeat(extras.rating) else ""
        val fav = if (extras.favorite) " ★избр." else ""
        val hid = if (extras.hidden) " [скрыт]" else ""
        binding.published.text = listOfNotNull(
            binding.published.text?.toString()?.takeIf { it.isNotBlank() },
            "Полка: $shelfName$stars$fav$hid"
        ).joinToString(" · ")
        if (extras.note.isNotBlank()) {
            binding.description.text =
                (binding.description.text?.toString().orEmpty()) + "\n\n📝 " + extras.note
        }
        if (extras.colorLabel.isNotBlank()) runCatching {
            binding.title.setTextColor(Color.parseColor(extras.colorLabel))
        }
    }

    private fun showMetaMenu() {
        val app = application as ChitalkaApp
        val items = arrayOf(
            "Полка", "Оценка", "Заметка", "Избранное", "Скрыть/показать",
            "Название/автор", "Цвет метки", "Поделиться тайтлом"
        )
        AlertDialog.Builder(this)
            .setTitle("Метаданные")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> pickShelf()
                    1 -> pickRating()
                    2 -> editNote()
                    3 -> {
                        app.extras.update(mangaId) { it.copy(favorite = !it.favorite) }
                        recreate()
                    }
                    4 -> {
                        app.extras.update(mangaId) { it.copy(hidden = !it.hidden) }
                        recreate()
                    }
                    5 -> editTitleAuthor()
                    6 -> pickColor()
                    7 -> shareTitle()
                }
            }
            .show()
    }

    private fun pickShelf() {
        val shelves = arrayOf("Читаю", "В планах", "Готово", "Архив")
        val ids = arrayOf(
            MangaExtras.SHELF_READING, MangaExtras.SHELF_PLANNED,
            MangaExtras.SHELF_DONE, MangaExtras.SHELF_ARCHIVE
        )
        AlertDialog.Builder(this).setTitle(R.string.shelf).setItems(shelves) { _, i ->
            (application as ChitalkaApp).extras.update(mangaId) { it.copy(shelf = ids[i]) }
            recreate()
        }.show()
    }

    private fun pickRating() {
        val ratings = arrayOf("Без оценки", "★", "★★", "★★★", "★★★★", "★★★★★")
        AlertDialog.Builder(this).setTitle(R.string.rating).setItems(ratings) { _, i ->
            (application as ChitalkaApp).extras.update(mangaId) { it.copy(rating = i) }
            recreate()
        }.show()
    }

    private fun editNote() {
        val app = application as ChitalkaApp
        val input = EditText(this).apply {
            setText(app.extras.get(mangaId).note)
            setTextColor(ContextCompat.getColor(this@MangaDetailsActivity, R.color.paper))
            setPadding(48, 32, 48, 32)
        }
        AlertDialog.Builder(this).setTitle(R.string.note).setView(input)
            .setPositiveButton("OK") { _, _ ->
                app.extras.update(mangaId) { it.copy(note = input.text.toString()) }
                recreate()
            }.setNegativeButton(R.string.close, null).show()
    }

    private fun editTitleAuthor() {
        val app = application as ChitalkaApp
        val e = app.extras.get(mangaId)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 8)
        }
        val titleIn = EditText(this).apply {
            hint = "Название"
            setText(e.titleOverride)
            setTextColor(ContextCompat.getColor(this@MangaDetailsActivity, R.color.paper))
        }
        val authorIn = EditText(this).apply {
            hint = "Автор"
            setText(e.authorOverride)
            setTextColor(ContextCompat.getColor(this@MangaDetailsActivity, R.color.paper))
        }
        box.addView(titleIn); box.addView(authorIn)
        AlertDialog.Builder(this).setTitle(R.string.edit_meta).setView(box)
            .setPositiveButton("OK") { _, _ ->
                app.extras.update(mangaId) {
                    it.copy(
                        titleOverride = titleIn.text.toString().trim(),
                        authorOverride = authorIn.text.toString().trim()
                    )
                }
                recreate()
            }.setNegativeButton(R.string.close, null).show()
    }

    private fun pickColor() {
        val colors = listOf("" to "Нет", "#E74C3C" to "Красный", "#3498DB" to "Синий",
            "#2ECC71" to "Зелёный", "#F1C40F" to "Жёлтый", "#9B59B6" to "Фиолетовый")
        AlertDialog.Builder(this).setTitle(R.string.color_label)
            .setItems(colors.map { it.second }.toTypedArray()) { _, i ->
                (application as ChitalkaApp).extras.update(mangaId) { it.copy(colorLabel = colors[i].first) }
                recreate()
            }.show()
    }

    private fun shareTitle() {
        val title = binding.title.text?.toString().orEmpty()
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "Читаю: $title")
        }
        startActivity(Intent.createChooser(send, getString(R.string.share_page)))
    }

    private fun renderChapters(chapters: List<com.chitalka.app.data.model.ChapterMeta>, cur: Int, page: Int) {
        binding.chaptersList.removeAllViews()
        val inflater = LayoutInflater.from(this)
        val accent = ContextCompat.getColor(this, R.color.accent_soft)
        val paper = ContextCompat.getColor(this, R.color.paper)
        chapters.forEachIndexed { index, chapter ->
            val row = ItemChapterBinding.inflate(inflater, binding.chaptersList, false)
            row.chapterIndex.text = "${index + 1}."
            row.chapterTitle.text = chapter.safeTitle(index)
            if (index == cur) {
                row.chapterProgress.text = "стр. ${page + 1}"
                row.chapterTitle.setTextColor(accent)
            }
            row.root.setOnClickListener {
                val p = if (index == cur) page else 0
                startActivity(ReaderActivity.intent(this, uri, mangaId, index, p))
            }
            binding.chaptersList.addView(row.root)
        }
    }

    private fun renderTags() {
        val app = application as ChitalkaApp
        val userTags = app.tags.get(mangaId)
        val fileTags = runCatching { app.archives.open(uri).readMeta().allLabels() }.getOrDefault(emptyList())
        binding.tagsList.removeAllViews()
        val paper = ContextCompat.getColor(this, R.color.paper)
        val surface = ContextCompat.getColor(this, R.color.surface_high)
        val accent = ContextCompat.getColor(this, R.color.accent_soft)
        fileTags.forEach { tag ->
            binding.tagsList.addView(Chip(this).apply {
                text = tag; isClickable = false; setTextColor(paper)
                chipBackgroundColor = android.content.res.ColorStateList.valueOf(surface)
            })
        }
        userTags.forEach { tag ->
            binding.tagsList.addView(Chip(this).apply {
                text = tag; isCloseIconVisible = true; setTextColor(accent)
                chipBackgroundColor = android.content.res.ColorStateList.valueOf(surface)
                setOnCloseIconClickListener { app.tags.remove(mangaId, tag); renderTags() }
            })
        }
        binding.noTags.visibility =
            if (fileTags.isEmpty() && userTags.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun promptAddTag() {
        val input = EditText(this).apply {
            hint = getString(R.string.tag_hint)
            setTextColor(ContextCompat.getColor(this@MangaDetailsActivity, R.color.paper))
            setPadding(48, 32, 48, 32)
        }
        AlertDialog.Builder(this).setTitle(R.string.add_tag).setView(input)
            .setPositiveButton(R.string.add_tag) { _, _ ->
                val t = input.text?.toString().orEmpty()
                if (t.isNotBlank()) {
                    (application as ChitalkaApp).tags.add(mangaId, t)
                    renderTags()
                }
            }.setNegativeButton(R.string.close, null).show()
    }

    private fun confirmDelete() {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_manga)
            .setMessage(getString(R.string.delete_manga_confirm, binding.title.text))
            .setPositiveButton(R.string.delete_manga) { _, _ ->
                val app = application as ChitalkaApp
                app.library.remove(uri)
                app.progress.clear(mangaId)
                app.bookmarks.removeForManga(mangaId)
                app.tags.clear(mangaId)
                app.extras.clear(mangaId)
                finish()
            }.setNegativeButton(R.string.close, null).show()
    }

    override fun onResume() {
        super.onResume()
        if (!::mangaId.isInitialized) return
        renderBookmarks()
    }

    private fun renderBookmarks() {
        val app = application as ChitalkaApp
        val items = app.bookmarks.forManga(mangaId)
        binding.bookmarksList.removeAllViews()
        binding.noBookmarks.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        val inflater = LayoutInflater.from(this)
        items.forEach { bookmark ->
            val row = ItemBookmarkBinding.inflate(inflater, binding.bookmarksList, false)
            row.bookmarkLabel.text = bookmark.label
            row.root.setOnClickListener {
                startActivity(ReaderActivity.intent(this, uri, mangaId, bookmark.chapterIndex, bookmark.pageIndex))
            }
            row.bookmarkDelete.setOnClickListener {
                app.bookmarks.remove(bookmark.id)
                renderBookmarks()
            }
            binding.bookmarksList.addView(row.root)
        }
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    companion object {
        private const val EXTRA_URI = "uri"
        private const val EXTRA_ID = "id"
        fun intent(context: Context, mangaId: String, uri: Uri): Intent =
            Intent(context, MangaDetailsActivity::class.java)
                .putExtra(EXTRA_ID, mangaId).putExtra(EXTRA_URI, uri)
    }
}
