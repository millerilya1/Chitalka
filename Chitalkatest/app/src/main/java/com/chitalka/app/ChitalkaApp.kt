package com.chitalka.app

import android.app.Application
import com.chitalka.app.data.format.ArchiveStore
import com.chitalka.app.data.library.LibraryRepository
import com.chitalka.app.data.progress.BookmarkStore
import com.chitalka.app.data.progress.MetaStore
import com.chitalka.app.data.progress.ReadingProgressStore
import com.chitalka.app.data.progress.SettingsStore
import com.chitalka.app.data.progress.StatsStore
import com.chitalka.app.data.progress.TagStore

class ChitalkaApp : Application() {
    lateinit var archives: ArchiveStore; private set
    lateinit var library: LibraryRepository; private set
    lateinit var progress: ReadingProgressStore; private set
    lateinit var bookmarks: BookmarkStore; private set
    lateinit var tags: TagStore; private set
    lateinit var settings: SettingsStore; private set
    lateinit var extras: MetaStore; private set
    lateinit var stats: StatsStore; private set

    override fun onCreate() {
        super.onCreate()
        archives = ArchiveStore(this)
        library = LibraryRepository(this, archives)
        progress = ReadingProgressStore(this)
        bookmarks = BookmarkStore(this)
        tags = TagStore(this)
        settings = SettingsStore(this)
        extras = MetaStore(this)
        stats = StatsStore(this)
    }
}
