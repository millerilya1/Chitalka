package com.chitalka.app.ui

import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding

/** Поднимает view над системной навигационной панелью / status bar. */
fun View.applySystemBarInsets(
    left: Boolean = false,
    top: Boolean = false,
    right: Boolean = false,
    bottom: Boolean = true,
    extraBottom: Int = 0,
    asMargin: Boolean = false
) {
    val initialLeft = paddingLeft
    val initialTop = paddingTop
    val initialRight = paddingRight
    val initialBottom = paddingBottom
    val lp = layoutParams as? ViewGroup.MarginLayoutParams
    val marginLeft = lp?.leftMargin ?: 0
    val marginTop = lp?.topMargin ?: 0
    val marginRight = lp?.rightMargin ?: 0
    val marginBottom = lp?.bottomMargin ?: 0

    ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        if (asMargin && lp != null) {
            v.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                leftMargin = marginLeft + if (left) bars.left else 0
                topMargin = marginTop + if (top) bars.top else 0
                rightMargin = marginRight + if (right) bars.right else 0
                bottomMargin = marginBottom + (if (bottom) bars.bottom else 0) + extraBottom
            }
        } else {
            v.updatePadding(
                left = initialLeft + if (left) bars.left else 0,
                top = initialTop + if (top) bars.top else 0,
                right = initialRight + if (right) bars.right else 0,
                bottom = initialBottom + (if (bottom) bars.bottom else 0) + extraBottom
            )
        }
        insets
    }
    requestApplyInsets()
}
