package com.chitalka.app.ui.reader

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Matrix
import android.graphics.PointF
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.appcompat.widget.AppCompatImageView
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Картинка с pinch-zoom, pan, кликом и горизонтальным свайпом.
 * Тап: левая треть — prev, правая — next, центр — toggle UI.
 * Свайп влево — next, вправо — prev (когда не увеличено).
 */
class ZoomableImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : AppCompatImageView(context, attrs) {

    var onTapLeft: (() -> Unit)? = null
    var onTapRight: (() -> Unit)? = null
    var onTapCenter: (() -> Unit)? = null
    var onLongPress: (() -> Unit)? = null

    private val matrixValues = FloatArray(9)
    private val last = PointF()
    private var scale = 1f
    private var minScale = 1f
    private var mode = NONE
    private var downX = 0f
    private var downY = 0f
    private var lastPageTurnMs = 0L

    private val scaleDetector = ScaleGestureDetector(context, ScaleListener())
    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (scale > minScale + 0.05f) resetZoom() else zoomTo(2.4f, e.x, e.y)
            return true
        }

        override fun onLongPress(e: MotionEvent) {
            onLongPress?.invoke()
        }

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            if (isZoomed()) return false
            val w = width.toFloat().coerceAtLeast(1f)
            when {
                e.x < w * 0.33f -> onTapLeft?.invoke()
                e.x > w * 0.66f -> onTapRight?.invoke()
                else -> onTapCenter?.invoke()
            }
            return true
        }

        override fun onFling(
            e1: MotionEvent?,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float
        ): Boolean {
            if (isZoomed()) return false
            if (abs(velocityX) < 600 || abs(velocityX) < abs(velocityY)) return false
            turnPage(velocityX < 0)
            return true
        }
    })

    init {
        scaleType = ScaleType.MATRIX
        isClickable = true
    }

    fun isZoomed(): Boolean = scale > minScale + 0.02f

    fun resetZoom() {
        if (width == 0 || height == 0) {
            post { if (width > 0 && height > 0) resetZoom() }
            return
        }
        imageMatrix = fitMatrix()
        invalidate()
    }

    override fun setImageBitmap(bm: android.graphics.Bitmap?) {
        super.setImageBitmap(bm)
        post { resetZoom() }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) resetZoom()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        val drawable = drawable ?: return true
        if (drawable.intrinsicWidth <= 0) return true

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                last.set(event.x, event.y)
                downX = event.x
                downY = event.y
                mode = DRAG
                parent?.requestDisallowInterceptTouchEvent(isZoomed())
            }
            MotionEvent.ACTION_MOVE -> if (mode == DRAG && isZoomed()) {
                imageMatrix = Matrix(imageMatrix).apply {
                    postTranslate(event.x - last.x, event.y - last.y)
                }
                last.set(event.x, event.y)
                clamp()
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                // Свайп без fling (медленный жест)
                if (!isZoomed() && mode == DRAG) {
                    val dx = event.x - downX
                    val dy = event.y - downY
                    if (abs(dx) > width * 0.18f && abs(dx) > abs(dy) * 1.5f) {
                        turnPage(dx < 0)
                    }
                }
                mode = NONE
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return true
    }

    private fun turnPage(toNext: Boolean) {
        val now = System.currentTimeMillis()
        if (now - lastPageTurnMs < 280) return
        lastPageTurnMs = now
        if (toNext) onTapRight?.invoke() else onTapLeft?.invoke()
    }

    private fun zoomTo(target: Float, focusX: Float, focusY: Float) {
        val next = min(MAX, max(minScale, target))
        val factor = next / scale
        if (factor == 0f || !factor.isFinite()) return
        imageMatrix = Matrix(imageMatrix).apply { postScale(factor, factor, focusX, focusY) }
        scale = next
        clamp()
    }

    private fun fitMatrix(): Matrix {
        val d = drawable ?: return Matrix()
        val vw = width.toFloat().coerceAtLeast(1f)
        val vh = height.toFloat().coerceAtLeast(1f)
        val dw = d.intrinsicWidth.toFloat().coerceAtLeast(1f)
        val dh = d.intrinsicHeight.toFloat().coerceAtLeast(1f)
        minScale = min(vw / dw, vh / dh)
        if (!minScale.isFinite() || minScale <= 0f) minScale = 1f
        scale = minScale
        val scaledW = dw * minScale
        val scaledH = dh * minScale
        val tx = (vw - scaledW) / 2f
        val ty = (vh - scaledH) / 2f
        return Matrix().apply {
            setScale(minScale, minScale)
            postTranslate(tx, ty)
        }
    }

    private fun clamp() {
        imageMatrix.getValues(matrixValues)
        val d = drawable ?: return
        val scaledW = d.intrinsicWidth * matrixValues[Matrix.MSCALE_X]
        val scaledH = d.intrinsicHeight * matrixValues[Matrix.MSCALE_Y]
        var tx = matrixValues[Matrix.MTRANS_X]
        var ty = matrixValues[Matrix.MTRANS_Y]
        if (scaledW <= width) tx = (width - scaledW) / 2f
        else {
            if (tx > 0) tx = 0f
            if (tx + scaledW < width) tx = width - scaledW
        }
        if (scaledH <= height) ty = (height - scaledH) / 2f
        else {
            if (ty > 0) ty = 0f
            if (ty + scaledH < height) ty = height - scaledH
        }
        matrixValues[Matrix.MTRANS_X] = tx
        matrixValues[Matrix.MTRANS_Y] = ty
        imageMatrix = Matrix().apply { setValues(matrixValues) }
        invalidate()
    }

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            zoomTo(scale * detector.scaleFactor, detector.focusX, detector.focusY)
            parent?.requestDisallowInterceptTouchEvent(true)
            return true
        }
    }

    companion object {
        private const val NONE = 0
        private const val DRAG = 1
        private const val MAX = 5f
    }
}
