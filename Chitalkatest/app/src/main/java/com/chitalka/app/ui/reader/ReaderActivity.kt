package com.chitalka.app.ui.reader

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.MediaStore
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.chitalka.app.ChitalkaApp
import com.chitalka.app.R
import com.chitalka.app.data.format.ChitalkaArchive
import com.chitalka.app.data.model.MangaMeta
import com.chitalka.app.data.model.ReadingProgress
import com.chitalka.app.databinding.ActivityReaderBinding
import com.chitalka.app.ui.applySystemBarInsets

class ReaderActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReaderBinding
    private lateinit var archive: ChitalkaArchive
    private lateinit var meta: MangaMeta

    private val pages = mutableListOf<ReaderPage>()
    private var pagePos = 0
    private var chromeVisible = true
    private val history = ArrayDeque<Int>()
    private var brightnessOverlay: View? = null

    private val autoHandler = Handler(Looper.getMainLooper())
    private val autoRunnable = object : Runnable {
        override fun run() {
            val step = pageStep()
            if (pagePos + step < pages.size) {
                goPage(step)
                scheduleAuto()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReaderBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val app = application as ChitalkaApp
        binding.chrome.applySystemBarInsets(bottom = true, left = true, right = true)
        binding.topChrome.applySystemBarInsets(top = true, left = true, right = true, bottom = false, asMargin = true)
        applyTheme()
        applyDualVisibility()
        applyOrientation()
        applyKeepScreen()
        applyPocket()

        @Suppress("DEPRECATION")
        val uri = intent.getParcelableExtra<Uri>(EXTRA_URI) ?: run { finish(); return }
        archive = runCatching { app.archives.open(uri) }.getOrNull() ?: run { finish(); return }
        meta = runCatching { archive.readMeta() }.getOrNull() ?: run { finish(); return }

        meta.safeChapters().forEachIndexed { chapterIdx, chapter ->
            archive.listPages(chapter.safePath()).forEachIndexed { pageIdx, entry ->
                pages += ReaderPage(chapterIdx, chapter.safeTitle(chapterIdx), pageIdx, entry)
            }
        }
        if (pages.isEmpty()) {
            Toast.makeText(this, "Нет страниц", Toast.LENGTH_LONG).show()
            finish(); return
        }

        binding.nextChapter.setOnClickListener { shiftChapter(1) }
        binding.prevChapter.setOnClickListener { shiftChapter(-1) }
        binding.bookmark.setOnClickListener { addBookmark() }
        binding.btnTheme.setOnClickListener { toggleTheme() }
        binding.btnAuto.setOnClickListener { cycleAuto() }
        binding.btnClose.setOnClickListener { finish() }
        binding.btnMenu.setOnClickListener { showReaderMenu() }
        binding.chapterTitle.setOnClickListener { showReaderMenu() }
        binding.btnModeSingle.setOnClickListener { setDualMode(false) }
        binding.btnModeDual.setOnClickListener { setDualMode(true) }
        updateModeButtons()

        wireTaps()

        val saved = app.progress.load(meta.safeId())
        val jumpChapter = intent.getIntExtra(EXTRA_CHAPTER, -1)
        val jumpPage = intent.getIntExtra(EXTRA_PAGE, -1)
        val targetChapter = if (jumpChapter >= 0) jumpChapter else saved.chapterIndex
        val targetPage = if (jumpPage >= 0) jumpPage else saved.pageIndex
        pagePos = pages.indexOfFirst {
            it.chapterListIndex == targetChapter && it.pageIndex == targetPage
        }.takeIf { it >= 0 }
            ?: pages.indexOfFirst { it.chapterListIndex == targetChapter }.takeIf { it >= 0 }
            ?: 0

        showCurrentPage()
        scheduleAuto()
    }

    private fun wireTaps() {
        val rtl = (application as ChitalkaApp).settings.rtlMode
        listOf(binding.pageImage, binding.pageImageRight).forEach { img ->
            img.onTapLeft = { goPage(if (rtl) pageStep() else -pageStep()) }
            img.onTapRight = { goPage(if (rtl) -pageStep() else pageStep()) }
            img.onTapCenter = { toggleChrome() }
            img.onLongPress = { addBookmark() }
        }
    }

    private fun pageStep() = if (dual()) 2 else 1

    private fun dual() = (application as ChitalkaApp).settings.dualPage

    private fun applyDualVisibility() {
        val on = dual()
        binding.pageImageRight.visibility = if (on) View.VISIBLE else View.GONE
        binding.pageGutter.visibility = if (on) View.VISIBLE else View.GONE
        updateModeButtons()
    }

    private fun setDualMode(enabled: Boolean) {
        val s = (application as ChitalkaApp).settings
        if (s.dualPage == enabled) return
        s.dualPage = enabled
        applyDualVisibility()
        Toast.makeText(
            this,
            if (enabled) R.string.mode_dual else R.string.mode_single,
            Toast.LENGTH_SHORT
        ).show()
        showCurrentPage()
        scheduleAuto()
    }

    private fun updateModeButtons() {
        val on = dual()
        binding.btnModeSingle.setBackgroundResource(
            if (!on) R.drawable.bg_mode_selected else R.drawable.bg_mode_normal
        )
        binding.btnModeDual.setBackgroundResource(
            if (on) R.drawable.bg_mode_selected else R.drawable.bg_mode_normal
        )
        binding.btnModeSingle.setTextColor(
            if (!on) 0xFF0E0C0A.toInt() else 0xFFF4EDE1.toInt()
        )
        binding.btnModeDual.setTextColor(
            if (on) 0xFF0E0C0A.toInt() else 0xFFF4EDE1.toInt()
        )
    }

    private fun applyOrientation() {
        requestedOrientation = when ((application as ChitalkaApp).settings.orientationLock) {
            1 -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            2 -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    private fun applyKeepScreen() {
        if ((application as ChitalkaApp).settings.keepScreenOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun applyPocket() {
        if ((application as ChitalkaApp).settings.pocketMode) {
            chromeVisible = false
            binding.chrome.visibility = View.GONE
            binding.topChrome.visibility = View.GONE
        }
    }

    private fun applyTheme() {
        val night = (application as ChitalkaApp).settings.nightMode
        val bg = if (night) Color.parseColor("#0E0C0A") else Color.parseColor("#F4EDE1")
        binding.readerRoot.setBackgroundColor(bg)
        binding.pagesRow.setBackgroundColor(bg)
    }

    private fun haptic() {
        if (!(application as ChitalkaApp).settings.haptic) return
        runCatching {
            val v = if (android.os.Build.VERSION.SDK_INT >= 31) {
                (getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(VIBRATOR_SERVICE) as Vibrator
            }
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                v.vibrate(VibrationEffect.createOneShot(15, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(15)
            }
        }
    }

    private fun showReaderMenu() {
        val app = application as ChitalkaApp
        val items = arrayOf(
            getString(R.string.jump_page),
            getString(R.string.toc),
            if (app.settings.rtlMode) getString(R.string.ltr) else getString(R.string.rtl),
            getString(R.string.brightness),
            getString(R.string.orient),
            getString(R.string.keep_screen),
            getString(R.string.haptic),
            getString(R.string.pocket),
            getString(R.string.share_page),
            "Назад по истории"
        )
        AlertDialog.Builder(this).setTitle("Ридер").setItems(items) { _, which ->
            when (which) {
                0 -> jumpToPage()
                1 -> showToc()
                2 -> {
                    app.settings.rtlMode = !app.settings.rtlMode
                    wireTaps()
                    Toast.makeText(this, if (app.settings.rtlMode) R.string.rtl else R.string.ltr, Toast.LENGTH_SHORT).show()
                }
                3 -> showBrightness()
                4 -> cycleOrientation()
                5 -> {
                    app.settings.keepScreenOn = !app.settings.keepScreenOn
                    applyKeepScreen()
                }
                6 -> {
                    app.settings.haptic = !app.settings.haptic
                    Toast.makeText(this, if (app.settings.haptic) "Вибрация вкл" else "Вибрация выкл", Toast.LENGTH_SHORT).show()
                }
                7 -> {
                    app.settings.pocketMode = !app.settings.pocketMode
                    applyPocket()
                }
                8 -> shareCurrentPage()
                9 -> historyBack()
            }
        }.show()
    }

    private fun jumpToPage() {
        val input = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            hint = "1…${pages.size}"
            setPadding(48, 32, 48, 32)
        }
        AlertDialog.Builder(this).setTitle(R.string.jump_page).setView(input)
            .setPositiveButton("OK") { _, _ ->
                val n = input.text.toString().toIntOrNull() ?: return@setPositiveButton
                val idx = (n - 1).coerceIn(0, pages.lastIndex)
                pushHistory()
                pagePos = idx
                showCurrentPage()
            }.setNegativeButton(R.string.close, null).show()
    }

    private fun showToc() {
        val titles = meta.safeChapters().mapIndexed { i, ch -> "${i + 1}. ${ch.safeTitle(i)}" }.toTypedArray()
        AlertDialog.Builder(this).setTitle(R.string.toc).setItems(titles) { _, i ->
            val idx = pages.indexOfFirst { it.chapterListIndex == i }
            if (idx >= 0) {
                pushHistory()
                pagePos = idx
                showCurrentPage()
            }
        }.show()
    }

    private fun showBrightness() {
        val bar = SeekBar(this).apply {
            max = 100
            progress = 80
            setPadding(48, 32, 48, 32)
        }
        // overlay dim
        if (brightnessOverlay == null) {
            brightnessOverlay = View(this).apply {
                setBackgroundColor(Color.BLACK)
                alpha = 0f
                layoutParams = android.widget.FrameLayout.LayoutParams(
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT
                )
                isClickable = false
            }
            binding.readerRoot.addView(brightnessOverlay, 1)
        }
        bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                brightnessOverlay?.alpha = 1f - progress / 100f
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })
        AlertDialog.Builder(this).setTitle(R.string.brightness).setView(bar)
            .setPositiveButton("OK", null).show()
    }

    private fun cycleOrientation() {
        val s = (application as ChitalkaApp).settings
        s.orientationLock = (s.orientationLock + 1) % 3
        applyOrientation()
        val msg = when (s.orientationLock) {
            1 -> "Книжная"
            2 -> "Альбом"
            else -> "Авто"
        }
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private fun shareCurrentPage() {
        val page = pages.getOrNull(pagePos) ?: return
        runCatching {
            val bytes = archive.readPage(page.entryName)
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@runCatching
            val path = MediaStore.Images.Media.insertImage(
                contentResolver, bmp,
                "chitalka_${page.pageIndex}",
                "${page.chapterTitle} · стр. ${page.pageIndex + 1}"
            ) ?: return@runCatching
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, Uri.parse(path))
                putExtra(Intent.EXTRA_TEXT, "${page.chapterTitle} · стр. ${page.pageIndex + 1}")
            }
            startActivity(Intent.createChooser(send, getString(R.string.share_page)))
        }.onFailure {
            Toast.makeText(this, "Не удалось поделиться", Toast.LENGTH_SHORT).show()
        }
    }

    private fun pushHistory() {
        if (history.isEmpty() || history.last() != pagePos) {
            history.addLast(pagePos)
            if (history.size > 50) history.removeFirst()
        }
    }

    private fun historyBack() {
        if (history.isEmpty()) {
            Toast.makeText(this, "История пуста", Toast.LENGTH_SHORT).show()
            return
        }
        pagePos = history.removeLast()
        showCurrentPage(animate = true)
    }

    override fun onPause() {
        autoHandler.removeCallbacks(autoRunnable)
        persistProgress()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        scheduleAuto()
    }

    override fun onDestroy() {
        autoHandler.removeCallbacks(autoRunnable)
        super.onDestroy()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val step = pageStep()
        val rtl = (application as ChitalkaApp).settings.rtlMode
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_N, KeyEvent.KEYCODE_VOLUME_DOWN -> {
                goPage(if (rtl) -step else step); true
            }
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_P, KeyEvent.KEYCODE_VOLUME_UP -> {
                goPage(if (rtl) step else -step); true
            }
            KeyEvent.KEYCODE_B -> { addBookmark(); true }
            KeyEvent.KEYCODE_BACK -> {
                if (history.isNotEmpty()) { historyBack(); true }
                else super.onKeyDown(keyCode, event)
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    private fun toggleDual() {
        setDualMode(!dual())
    }

    private fun toggleTheme() {
        val s = (application as ChitalkaApp).settings
        s.nightMode = !s.nightMode
        applyTheme()
        Toast.makeText(this, if (s.nightMode) R.string.night_on else R.string.day_on, Toast.LENGTH_SHORT).show()
    }

    private fun cycleAuto() {
        val s = (application as ChitalkaApp).settings
        s.autoTurnSeconds = when (s.autoTurnSeconds) { 0 -> 5; 5 -> 10; 10 -> 15; else -> 0 }
        scheduleAuto()
        val msg = if (s.autoTurnSeconds == 0) getString(R.string.auto_off)
        else getString(R.string.auto_on, s.autoTurnSeconds)
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private fun scheduleAuto() {
        autoHandler.removeCallbacks(autoRunnable)
        val sec = (application as ChitalkaApp).settings.autoTurnSeconds
        if (sec > 0) autoHandler.postDelayed(autoRunnable, sec * 1000L)
    }

    private fun toggleChrome() {
        chromeVisible = !chromeVisible
        val v = if (chromeVisible) View.VISIBLE else View.GONE
        binding.chrome.visibility = v
        binding.topChrome.visibility = v
    }

    private fun goPage(delta: Int) {
        val next = pagePos + delta
        if (next !in pages.indices) return
        pushHistory()
        pagePos = next
        showCurrentPage(animate = true)
        haptic()
        (application as ChitalkaApp).stats.recordPageTurn()
        scheduleAuto()
    }

    private fun shiftChapter(delta: Int) {
        if (pages.isEmpty()) return
        val current = pages[pagePos]
        val targetChapter = current.chapterListIndex + delta
        val idx = pages.indexOfFirst { it.chapterListIndex == targetChapter }
        if (idx < 0) return
        pushHistory()
        pagePos = idx
        showCurrentPage(animate = true)
        scheduleAuto()
    }

    private fun loadBitmap(entry: String): Bitmap? = runCatching {
        val bytes = archive.readPage(entry)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }.getOrNull()

    private fun showCurrentPage(animate: Boolean = false) {
        val page = pages.getOrNull(pagePos) ?: return
        val apply = {
            binding.pageImage.setImageBitmap(loadBitmap(page.entryName))
            binding.pageImage.resetZoom()
            if (dual()) {
                val rightPage = pages.getOrNull(pagePos + 1)
                if (rightPage != null) {
                    binding.pageImageRight.visibility = View.VISIBLE
                    binding.pageImageRight.setImageBitmap(loadBitmap(rightPage.entryName))
                    binding.pageImageRight.resetZoom()
                } else {
                    binding.pageImageRight.setImageBitmap(null)
                    binding.pageImageRight.visibility = View.INVISIBLE
                }
            }
            val totalInChapter = pages.count { it.chapterListIndex == page.chapterListIndex }
            val extra = if (dual() && pages.getOrNull(pagePos + 1) != null) "–${page.pageIndex + 2}" else ""
            binding.chapterTitle.text = page.chapterTitle
            binding.pageIndicator.text =
                "стр. ${page.pageIndex + 1}$extra / $totalInChapter  ·  ${pagePos + 1}/${pages.size}"
            persistProgress()
        }
        if (animate) {
            binding.pagesRow.animate().alpha(0.3f).setDuration(80).withEndAction {
                apply()
                binding.pagesRow.animate().alpha(1f).setDuration(80).start()
            }.start()
        } else apply()
    }

    private fun addBookmark() {
        val page = pages.getOrNull(pagePos) ?: return
        (application as ChitalkaApp).bookmarks.add(
            mangaId = meta.safeId(),
            chapterIndex = page.chapterListIndex,
            pageIndex = page.pageIndex,
            label = "${page.chapterTitle} · стр. ${page.pageIndex + 1}"
        )
        Toast.makeText(this, R.string.bookmark_added, Toast.LENGTH_SHORT).show()
        haptic()
    }

    private fun persistProgress() {
        val page = pages.getOrNull(pagePos) ?: return
        (application as ChitalkaApp).progress.save(
            ReadingProgress(meta.safeId(), page.chapterListIndex, page.pageIndex)
        )
    }

    companion object {
        private const val EXTRA_URI = "uri"
        private const val EXTRA_ID = "id"
        private const val EXTRA_CHAPTER = "chapter"
        private const val EXTRA_PAGE = "page"

        fun intent(
            context: Context, uri: Uri, mangaId: String,
            chapterIndex: Int = -1, pageIndex: Int = -1
        ): Intent = Intent(context, ReaderActivity::class.java)
            .putExtra(EXTRA_URI, uri).putExtra(EXTRA_ID, mangaId)
            .putExtra(EXTRA_CHAPTER, chapterIndex).putExtra(EXTRA_PAGE, pageIndex)
    }
}

data class ReaderPage(
    val chapterListIndex: Int,
    val chapterTitle: String,
    val pageIndex: Int,
    val entryName: String
)
