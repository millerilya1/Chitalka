package com.chitalka.app.data.progress

import android.content.Context
import androidx.core.content.edit
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class StatsStore(context: Context) {
    private val prefs = context.getSharedPreferences("stats", Context.MODE_PRIVATE)
    private val dayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    fun recordPageTurn() {
        val day = dayFmt.format(Date())
        val key = "day:$day"
        prefs.edit { putInt(key, prefs.getInt(key, 0) + 1) }
        prefs.edit { putInt("total", prefs.getInt("total", 0) + 1) }
    }

    fun todayPages(): Int = prefs.getInt("day:${dayFmt.format(Date())}", 0)
    fun totalPages(): Int = prefs.getInt("total", 0)
}
