package com.chitalka.app.data.format

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.LruCache
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipFile

class ArchiveStore(private val context: Context) {

    private val open = ConcurrentHashMap<String, ChitalkaArchive>()
    private val covers = object : LruCache<String, Bitmap>(12) {}

    fun open(uri: Uri): ChitalkaArchive {
        val key = uri.toString()
        open[key]?.let { return it }
        synchronized(this) {
            open[key]?.let { return it }
            val file = materialize(uri)
            val name = DocumentFile.fromSingleUri(context, uri)?.name ?: uri.lastPathSegment ?: "manga"
            val archive = ChitalkaArchive(ZipFile(file), name)
            open[key] = archive
            return archive
        }
    }

    fun cover(uri: Uri): Bitmap? {
        val key = uri.toString()
        covers.get(key)?.let { return it }
        val bitmap = runCatching { open(uri).decodeCover() }.getOrNull() ?: return null
        covers.put(key, bitmap)
        return bitmap
    }

    private fun materialize(uri: Uri): File {
        val dir = File(context.cacheDir, "archives").apply { mkdirs() }
        val size = DocumentFile.fromSingleUri(context, uri)?.length() ?: -1L
        val dest = File(dir, "${uri.toString().hashCode()}_$size.chitalka")
        if (dest.exists() && dest.length() > 0L) return dest

        dir.listFiles()?.forEach { cached ->
            if (cached.name.startsWith("${uri.toString().hashCode()}_")) cached.delete()
        }
        context.contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { input.copyTo(it) }
        } ?: error("Не удалось открыть файл")
        return dest
    }
}
