package com.chitalka.app.ui.library

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.chitalka.app.ChitalkaApp
import com.chitalka.app.data.model.MangaFile
import com.chitalka.app.data.progress.MangaExtras
import com.chitalka.app.databinding.ItemMangaBinding
import com.chitalka.app.databinding.ItemMangaGridBinding

class LibraryAdapter(
    private val onClick: (MangaFile) -> Unit,
    private val onLongClick: (MangaFile) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items = mutableListOf<MangaFile>()
    var gridMode: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                notifyDataSetChanged()
            }
        }

    fun submit(next: List<MangaFile>) {
        items.clear()
        items.addAll(next)
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int) = if (gridMode) 1 else 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inf = LayoutInflater.from(parent.context)
        return if (viewType == 1) GridHolder(ItemMangaGridBinding.inflate(inf, parent, false))
        else ListHolder(ItemMangaBinding.inflate(inf, parent, false))
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = items[position]
        val app = holder.itemView.context.applicationContext as ChitalkaApp
        when (holder) {
            is ListHolder -> bindList(holder, item, app)
            is GridHolder -> bindGrid(holder, item, app)
        }
    }

    private fun displayTitle(item: MangaFile, app: ChitalkaApp): String {
        val o = app.extras.get(item.meta.safeId()).titleOverride
        return o.ifBlank { item.meta.safeTitle() }
    }

    private fun displayAuthor(item: MangaFile, app: ChitalkaApp): String {
        val o = app.extras.get(item.meta.safeId()).authorOverride
        return o.ifBlank { item.meta.safeAuthor() }.ifBlank { "—" }
    }

    private fun progressPercent(item: MangaFile, app: ChitalkaApp): Int {
        val chapters = item.meta.safeChapters()
        if (chapters.isEmpty()) return 0
        val p = app.progress.load(item.meta.safeId())
        return ((p.chapterIndex + 1) * 100 / chapters.size).coerceIn(0, 100)
    }

    private fun unreadChapters(item: MangaFile, app: ChitalkaApp): Int {
        val total = item.meta.safeChapters().size
        val p = app.progress.load(item.meta.safeId())
        return (total - p.chapterIndex - 1).coerceAtLeast(0)
    }

    private fun bindList(holder: ListHolder, item: MangaFile, app: ChitalkaApp) {
        val meta = item.meta
        val id = meta.safeId()
        val extras = app.extras.get(id)
        val chapters = meta.safeChapters()
        holder.b.title.text = displayTitle(item, app) + if (extras.favorite) " ★" else ""
        holder.b.author.text = displayAuthor(item, app)
        val unread = unreadChapters(item, app)
        holder.b.chapters.text = "${chapters.size} гл." + if (unread > 0) " · ещё $unread" else ""
        val pct = progressPercent(item, app)
        holder.b.progressBar.progress = pct
        holder.b.progressBar.visibility = if (pct > 0) View.VISIBLE else View.GONE
        val progress = app.progress.load(id)
        if (progress.chapterIndex > 0 || progress.pageIndex > 0) {
            holder.b.progress.visibility = View.VISIBLE
            holder.b.progress.text =
                "гл. ${progress.chapterIndex + 1}/${chapters.size.coerceAtLeast(1)} · стр. ${progress.pageIndex + 1} · $pct%"
        } else holder.b.progress.visibility = View.GONE
        val labels = (meta.allLabels() + app.tags.get(id)).distinct().toMutableList()
        if (extras.rating > 0) labels.add(0, "★".repeat(extras.rating))
        if (extras.colorLabel.isNotBlank()) labels.add("●")
        if (labels.isNotEmpty()) {
            holder.b.categories.visibility = View.VISIBLE
            holder.b.categories.text = labels.joinToString(" · ")
            if (extras.colorLabel.isNotBlank()) runCatching {
                holder.b.categories.setTextColor(Color.parseColor(extras.colorLabel))
            }
        } else holder.b.categories.visibility = View.GONE
        runCatching { holder.b.cover.setImageBitmap(app.archives.cover(item.uri)) }
        holder.itemView.setOnClickListener { onClick(item) }
        holder.itemView.setOnLongClickListener { onLongClick(item); true }
    }

    private fun bindGrid(holder: GridHolder, item: MangaFile, app: ChitalkaApp) {
        val id = item.meta.safeId()
        val extras = app.extras.get(id)
        holder.b.title.text = displayTitle(item, app) + if (extras.favorite) " ★" else ""
        val pct = progressPercent(item, app)
        val progress = app.progress.load(id)
        if (pct > 0) {
            holder.b.progress.visibility = View.VISIBLE
            holder.b.progress.text = "$pct% · гл. ${progress.chapterIndex + 1}"
        } else holder.b.progress.visibility = View.GONE
        runCatching { holder.b.cover.setImageBitmap(app.archives.cover(item.uri)) }
        holder.itemView.setOnClickListener { onClick(item) }
        holder.itemView.setOnLongClickListener { onLongClick(item); true }
    }

    class ListHolder(val b: ItemMangaBinding) : RecyclerView.ViewHolder(b.root)
    class GridHolder(val b: ItemMangaGridBinding) : RecyclerView.ViewHolder(b.root)
}
