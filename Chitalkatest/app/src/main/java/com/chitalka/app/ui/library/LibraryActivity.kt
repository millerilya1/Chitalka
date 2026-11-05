package com.chitalka.app.ui.library

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.chitalka.app.ChitalkaApp
import com.chitalka.app.R
import com.chitalka.app.data.model.MangaFile
import com.chitalka.app.data.progress.MangaExtras
import com.chitalka.app.databinding.ActivityLibraryBinding
import com.chitalka.app.ui.applySystemBarInsets
import com.chitalka.app.ui.details.MangaDetailsActivity
import com.chitalka.app.ui.reader.ReaderActivity
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import org.json.JSONArray
import org.json.JSONObject

class LibraryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLibraryBinding
    private var allItems: List<MangaFile> = emptyList()
    private var selectedCategory: String? = null
    private var query: String = ""

    private val adapter = LibraryAdapter(
        onClick = { manga ->
            startActivity(MangaDetailsActivity.intent(this, manga.meta.safeId(), manga.uri))
        },
        onLongClick = { manga -> confirmDelete(manga) }
    )

    private val openDocument = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        runCatching {
            val app = application as ChitalkaApp
            app.library.add(uri)
            // автотеги из имени файла
            val name = uri.lastPathSegment?.substringAfterLast(':')?.substringBeforeLast('.') ?: ""
            val id = app.library.all().lastOrNull { it.uri == uri }?.meta?.safeId()
            if (id != null && name.isNotBlank()) {
                name.split('_', '-', ' ', '.').map { it.trim() }.filter { it.length > 2 }.take(5)
                    .forEach { app.tags.add(id, it) }
            }
            refresh()
            Snackbar.make(binding.root, "Файл добавлен", Snackbar.LENGTH_SHORT).show()
        }.onFailure {
            Snackbar.make(binding.root, "Не удалось добавить", Snackbar.LENGTH_LONG).show()
        }
    }

    private val exportDoc = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@registerForActivityResult
        runCatching { writeExport(uri) }
            .onSuccess { Snackbar.make(binding.root, R.string.export_done, Snackbar.LENGTH_SHORT).show() }
            .onFailure { Snackbar.make(binding.root, it.message ?: "Ошибка", Snackbar.LENGTH_LONG).show() }
    }

    private val importDoc = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        runCatching { readImport(uri); refresh() }
            .onSuccess { Snackbar.make(binding.root, R.string.import_done, Snackbar.LENGTH_SHORT).show() }
            .onFailure { Snackbar.make(binding.root, it.message ?: "Ошибка", Snackbar.LENGTH_LONG).show() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLibraryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        val app = application as ChitalkaApp
        binding.addButton.applySystemBarInsets(bottom = true, right = true, asMargin = true)
        binding.mangaList.applySystemBarInsets(bottom = true, extraBottom = 24)

        adapter.gridMode = app.settings.gridMode
        applyLayoutManager()
        binding.mangaList.adapter = adapter
        binding.addButton.setOnClickListener {
            openDocument.launch(arrayOf(
                "application/zip", "application/octet-stream",
                "application/x-cbz", "application/vnd.comicbook+zip", "*/*"
            ))
        }

        binding.searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                query = s?.toString().orEmpty().trim()
                applyFilter()
            }
        })

        if (!app.settings.onboardingDone) {
            AlertDialog.Builder(this)
                .setTitle("Добро пожаловать в Читалку")
                .setMessage(
                    "1) Добавьте .chitalka или .cbz кнопкой +\n" +
                    "2) Листайте тапом или свайпом\n" +
                    "3) Полки, теги и закладки — на экране тайтла\n" +
                    "4) В ридере: день/ночь, автолистание, две страницы"
                )
                .setPositiveButton("Понятно") { _, _ -> app.settings.onboardingDone = true }
                .show()
        }

        // еженедельный бэкап-намёк
        val last = getSharedPreferences("backup", 0).getLong("last", 0L)
        if (last > 0 && System.currentTimeMillis() - last > 7L * 24 * 3600 * 1000) {
            Snackbar.make(binding.root, "Пора сделать экспорт библиотеки", Snackbar.LENGTH_LONG)
                .setAction("Экспорт") { exportDoc.launch("chitalka-library.json") }
                .show()
        }

        refresh()
    }

    private fun applyLayoutManager() {
        binding.mangaList.layoutManager = if (adapter.gridMode) GridLayoutManager(this, 3)
        else LinearLayoutManager(this)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_library, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val app = application as ChitalkaApp
        return when (item.itemId) {
            R.id.action_about -> { showAbout(); true }
            R.id.action_grid -> {
                app.settings.gridMode = !app.settings.gridMode
                adapter.gridMode = app.settings.gridMode
                applyLayoutManager()
                true
            }
            R.id.action_sort -> {
                app.settings.sortMode = (app.settings.sortMode + 1) % 3
                val msg = when (app.settings.sortMode) {
                    1 -> R.string.sort_recent
                    2 -> R.string.sort_author
                    else -> R.string.sort_title
                }
                Snackbar.make(binding.root, msg, Snackbar.LENGTH_SHORT).show()
                applyFilter()
                true
            }
            R.id.action_random -> {
                val list = adapter.let {
                    // filtered list not stored - use allItems filtered same way
                    currentFiltered().randomOrNull()
                }
                if (list != null) {
                    startActivity(MangaDetailsActivity.intent(this, list.meta.safeId(), list.uri))
                } else Snackbar.make(binding.root, "Библиотека пуста", Snackbar.LENGTH_SHORT).show()
                true
            }
            R.id.action_duplicates -> {
                showDuplicates()
                true
            }
            R.id.action_export -> {
                exportDoc.launch("chitalka-library.json")
                getSharedPreferences("backup", 0).edit().putLong("last", System.currentTimeMillis()).apply()
                true
            }
            R.id.action_import -> {
                importDoc.launch(arrayOf("application/json", "text/*", "*/*"))
                true
            }
            R.id.action_show_hidden -> {
                app.settings.showHidden = !app.settings.showHidden
                applyFilter()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun currentFiltered(): List<MangaFile> {
        val app = application as ChitalkaApp
        val q = query.lowercase()
        val shelf = app.settings.shelfFilter
        return allItems.filter { item ->
            val id = item.meta.safeId()
            val extras = app.extras.get(id)
            if (extras.hidden && !app.settings.showHidden) return@filter false
            if (shelf == "fav" && !extras.favorite) return@filter false
            if (shelf.isNotEmpty() && shelf != "fav" && extras.shelf != shelf) return@filter false
            val labels = item.meta.allLabels() + app.tags.get(id)
            val title = extras.titleOverride.ifBlank { item.meta.safeTitle() }
            val author = extras.authorOverride.ifBlank { item.meta.safeAuthor() }
            val matchesQuery = q.isEmpty() ||
                title.lowercase().contains(q) || author.lowercase().contains(q) ||
                labels.any { it.lowercase().contains(q) } || item.displayName.lowercase().contains(q)
            val matchesCategory = selectedCategory == null ||
                labels.any { it.equals(selectedCategory, ignoreCase = true) }
            matchesQuery && matchesCategory
        }.let { list ->
            when (app.settings.sortMode) {
                1 -> list.sortedByDescending { app.progress.lastReadAt(it.meta.safeId()) }
                2 -> list.sortedBy {
                    app.extras.get(it.meta.safeId()).authorOverride.ifBlank { it.meta.safeAuthor() }.lowercase()
                }
                else -> list.sortedBy {
                    app.extras.get(it.meta.safeId()).titleOverride.ifBlank { it.meta.safeTitle() }.lowercase()
                }
            }
        }
    }

    private fun showDuplicates() {
        val groups = allItems.groupBy { it.meta.safeTitle().lowercase().trim() }
            .filter { it.value.size > 1 }
        if (groups.isEmpty()) {
            Snackbar.make(binding.root, "Дубликатов не найдено", Snackbar.LENGTH_SHORT).show()
            return
        }
        val msg = groups.entries.take(15).joinToString("\n") { (title, list) ->
            "«$title» × ${list.size}"
        }
        AlertDialog.Builder(this)
            .setTitle("Возможные дубликаты")
            .setMessage(msg)
            .setPositiveButton(R.string.close, null)
            .show()
    }

    private fun writeExport(uri: Uri) {
        val app = application as ChitalkaApp
        val json = JSONObject().apply {
            put("version", 2)
            put("uris", JSONArray(app.library.exportUris()))
            put("progress", JSONObject(app.progress.exportAll() as Map<*, *>))
            put("tags", JSONObject(app.tags.exportAll() as Map<*, *>))
            put("extras", JSONObject(app.extras.exportAll() as Map<*, *>))
            put("bookmarks", app.bookmarks.exportJson())
        }
        contentResolver.openOutputStream(uri)?.use { it.write(json.toString(2).toByteArray(Charsets.UTF_8)) }
            ?: error("Не удалось записать")
    }

    private fun readImport(uri: Uri) {
        val app = application as ChitalkaApp
        val text = contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: error("Не удалось прочитать")
        val root = JSONObject(text)
        val uris = mutableListOf<String>()
        root.optJSONArray("uris")?.let { arr -> for (i in 0 until arr.length()) uris += arr.getString(i) }
        app.library.importUris(uris)
        root.optJSONObject("progress")?.let { p ->
            val map = mutableMapOf<String, Any?>()
            p.keys().forEach { k -> map[k] = p.get(k) }
            app.progress.importAll(map)
        }
        root.optJSONObject("tags")?.let { t ->
            val map = mutableMapOf<String, String>()
            t.keys().forEach { k -> map[k] = t.getString(k) }
            app.tags.importAll(map)
        }
        root.optJSONObject("extras")?.let { t ->
            val map = mutableMapOf<String, String>()
            t.keys().forEach { k -> map[k] = t.getString(k) }
            app.extras.importAll(map)
        }
        root.optString("bookmarks", "").takeIf { it.isNotBlank() && it != "null" }?.let {
            app.bookmarks.importJson(it)
        }
    }

    private fun showAbout() {
        val app = application as ChitalkaApp
        val url = getString(R.string.telegram_url)
        val stats = "Сегодня страниц: ${app.stats.todayPages()}\nВсего: ${app.stats.totalPages()}\nТайтлов: ${allItems.size}"
        AlertDialog.Builder(this)
            .setTitle(R.string.about_title)
            .setMessage(getString(R.string.about_body, url) + "\n\n" + stats)
            .setPositiveButton(R.string.open_telegram) { _, _ ->
                runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            }
            .setNegativeButton(R.string.close, null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        allItems = runCatching { (application as ChitalkaApp).library.all() }.getOrDefault(emptyList())
        rebuildShelfChips()
        rebuildCategoryChips()
        updateContinueBar()
        applyFilter()
    }

    private fun updateContinueBar() {
        val app = application as ChitalkaApp
        val recent = allItems
            .filter { app.progress.lastReadAt(it.meta.safeId()) > 0 }
            .maxByOrNull { app.progress.lastReadAt(it.meta.safeId()) }
        if (recent == null) {
            binding.continueBar.visibility = View.GONE
            return
        }
        binding.continueBar.visibility = View.VISIBLE
        val title = app.extras.get(recent.meta.safeId()).titleOverride.ifBlank { recent.meta.safeTitle() }
        val p = app.progress.load(recent.meta.safeId())
        binding.continueText.text = "Продолжить: $title (гл. ${p.chapterIndex + 1})"
        binding.continueButton.setOnClickListener {
            startActivity(ReaderActivity.intent(this, recent.uri, recent.meta.safeId()))
        }
    }

    private fun rebuildShelfChips() {
        val chips = binding.shelfChips
        chips.removeAllViews()
        val app = application as ChitalkaApp
        val paper = ContextCompat.getColor(this, R.color.paper)
        val surface = ContextCompat.getColor(this, R.color.surface_high)
        data class Shelf(val id: String, val label: String)
        val shelves = listOf(
            Shelf("", "Все"),
            Shelf(MangaExtras.SHELF_READING, "Читаю"),
            Shelf(MangaExtras.SHELF_PLANNED, "В планах"),
            Shelf(MangaExtras.SHELF_DONE, "Готово"),
            Shelf(MangaExtras.SHELF_ARCHIVE, "Архив"),
            Shelf("fav", "★ Избранное")
        )
        shelves.forEach { s ->
            chips.addView(Chip(this).apply {
                text = s.label
                isCheckable = true
                isChecked = app.settings.shelfFilter == s.id
                setTextColor(paper)
                chipBackgroundColor = android.content.res.ColorStateList.valueOf(surface)
                setOnClickListener {
                    app.settings.shelfFilter = if (isChecked) s.id else ""
                    for (i in 0 until chips.childCount) {
                        val other = chips.getChildAt(i) as? Chip ?: continue
                        if (other !== this) other.isChecked = false
                    }
                    applyFilter()
                }
            })
        }
    }

    private fun rebuildCategoryChips() {
        val chips = binding.categoryChips
        chips.removeAllViews()
        val app = application as ChitalkaApp
        val categories = allItems
            .flatMap { it.meta.allLabels() + app.tags.get(it.meta.safeId()) }
            .distinct().sortedBy { it.lowercase() }
        if (categories.isEmpty()) {
            binding.categoryScroll.visibility = View.GONE
            selectedCategory = null
            return
        }
        binding.categoryScroll.visibility = View.VISIBLE
        val paper = ContextCompat.getColor(this, R.color.paper)
        val surface = ContextCompat.getColor(this, R.color.surface_high)
        fun addChip(label: String, value: String?) {
            chips.addView(Chip(this).apply {
                text = label
                isCheckable = true
                isChecked = selectedCategory == value
                setTextColor(paper)
                chipBackgroundColor = android.content.res.ColorStateList.valueOf(surface)
                setOnClickListener {
                    selectedCategory = if (isChecked) value else null
                    for (i in 0 until chips.childCount) {
                        val other = chips.getChildAt(i) as? Chip ?: continue
                        if (other !== this) other.isChecked = false
                    }
                    applyFilter()
                }
            })
        }
        addChip("Теги: все", null)
        categories.forEach { addChip(it, it) }
    }

    private fun applyFilter() {
        val filtered = currentFiltered()
        adapter.submit(filtered)
        binding.emptyHint.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        binding.emptyHint.text = when {
            allItems.isEmpty() -> getString(R.string.empty_library)
            else -> getString(R.string.empty_filter)
        }
    }

    private fun confirmDelete(manga: MangaFile) {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_manga)
            .setMessage(getString(R.string.delete_manga_confirm, manga.meta.safeTitle()))
            .setPositiveButton(R.string.delete_manga) { _, _ -> deleteManga(manga) }
            .setNegativeButton(R.string.close, null)
            .show()
    }

    private fun deleteManga(manga: MangaFile) {
        val app = application as ChitalkaApp
        val id = manga.meta.safeId()
        app.library.remove(manga.uri)
        app.progress.clear(id)
        app.bookmarks.removeForManga(id)
        app.tags.clear(id)
        app.extras.clear(id)
        refresh()
        Snackbar.make(binding.root, R.string.deleted, Snackbar.LENGTH_SHORT).show()
    }
}
