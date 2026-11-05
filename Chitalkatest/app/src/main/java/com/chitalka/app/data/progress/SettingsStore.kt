package com.chitalka.app.data.progress

import android.content.Context
import androidx.core.content.edit

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var nightMode: Boolean
        get() = prefs.getBoolean("night", true)
        set(v) = prefs.edit { putBoolean("night", v) }

    var gridMode: Boolean
        get() = prefs.getBoolean("grid", false)
        set(v) = prefs.edit { putBoolean("grid", v) }

    /** 0 title, 1 recent, 2 author */
    var sortMode: Int
        get() = prefs.getInt("sort", 0)
        set(v) = prefs.edit { putInt("sort", v) }

    var autoTurnSeconds: Int
        get() = prefs.getInt("auto_turn", 0)
        set(v) = prefs.edit { putInt("auto_turn", v) }

    var dualPage: Boolean
        get() = prefs.getBoolean("dual_page", false)
        set(v) = prefs.edit { putBoolean("dual_page", v) }

    /** true = manga RTL (tap left = next) */
    var rtlMode: Boolean
        get() = prefs.getBoolean("rtl", true)
        set(v) = prefs.edit { putBoolean("rtl", v) }

    var keepScreenOn: Boolean
        get() = prefs.getBoolean("keep_screen", true)
        set(v) = prefs.edit { putBoolean("keep_screen", v) }

    var haptic: Boolean
        get() = prefs.getBoolean("haptic", true)
        set(v) = prefs.edit { putBoolean("haptic", v) }

    /** 0 auto, 1 portrait, 2 landscape */
    var orientationLock: Int
        get() = prefs.getInt("orient", 0)
        set(v) = prefs.edit { putInt("orient", v) }

    var pocketMode: Boolean
        get() = prefs.getBoolean("pocket", false)
        set(v) = prefs.edit { putBoolean("pocket", v) }

    var showHidden: Boolean
        get() = prefs.getBoolean("show_hidden", false)
        set(v) = prefs.edit { putBoolean("show_hidden", v) }

    var onboardingDone: Boolean
        get() = prefs.getBoolean("onboard", false)
        set(v) = prefs.edit { putBoolean("onboard", v) }

    /** filter shelf: "" = all */
    var shelfFilter: String
        get() = prefs.getString("shelf_filter", "") ?: ""
        set(v) = prefs.edit { putString("shelf_filter", v) }
}
