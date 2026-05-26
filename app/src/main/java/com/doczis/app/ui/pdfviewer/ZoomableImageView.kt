package com.doczis.app.ui.pdfviewer

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Matrix
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.ViewConfiguration
import android.widget.OverScroller
import androidx.appcompat.widget.AppCompatImageView
import kotlin.math.max

class ZoomableImageView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    interface OnScrollCallback {
        fun onScroll(transY: Float)
    }

    interface OnZoomCallback {
        fun onZoom(transY: Float)
    }

    private var scrollCallback: OnScrollCallback? = null
    private var zoomCallback: OnZoomCallback? = null

    fun setOnScrollCallback(cb: OnScrollCallback) { scrollCallback = cb }
    fun setOnZoomCallback(cb: OnZoomCallback) { zoomCallback = cb }

    fun getCurrentScale(): Float {
        val values = FloatArray(9)
        matrix.getValues(values)
        return values[Matrix.MSCALE_X]
    }

    private val matrix = Matrix()
    private val savedMatrix = Matrix()
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    private var activePointerId = -1
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var isDragging = false
    private var isFlinging = false

    private var minScale = 1f
    private var maxScale = 6f
    private var currentScale = 1f

    var scrollPaddingTop: Float = 0f
    var scrollPaddingBottom: Float = 0f

    private val scroller = OverScroller(context)
    private var zoomAnimator: ValueAnimator? = null

    private var imgW = 0f
    private var imgH = 0f
    private var vw = 0f
    private var vh = 0f

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(d: ScaleGestureDetector): Boolean = true
        override fun onScale(d: ScaleGestureDetector): Boolean {
            val newScale = getCurrentScale() * d.scaleFactor
            val clamped = newScale.coerceIn(minScale, maxScale * 1.2f)
            if (clamped != getCurrentScale()) {
                matrix.postScale(clamped / getCurrentScale(), clamped / getCurrentScale(), d.focusX, d.focusY)
                clampBounds()
                imageMatrix = matrix
            }
            return true
        }

        override fun onScaleEnd(d: ScaleGestureDetector) {
            super.onScaleEnd(d)
            val values = FloatArray(9)
            matrix.getValues(values)
            zoomCallback?.onZoom(values[Matrix.MTRANS_Y])
        }
    })

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            performClick()
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            val curScale = getCurrentScale()
            val targetScale = if (curScale < 2f) 3f else minScale
            animateZoom(targetScale, e.x, e.y)
            return true
        }

        override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
            val values = FloatArray(9)
            matrix.getValues(values)
            val curX = values[Matrix.MTRANS_X]
            val curY = values[Matrix.MTRANS_Y]
            val d = drawable ?: return true
            val s = getCurrentScale()
            val maxX = 0f
            val minX = vw - d.intrinsicWidth * s
            val maxY = 0f
            val minY = vh - d.intrinsicHeight * s
            val velMul = maxOf(s / minScale, 1f)
            scroller.fling(
                curX.toInt(), curY.toInt(),
                (velocityX * velMul).toInt(), (velocityY * velMul).toInt(),
                minX.toInt(), maxX.toInt(),
                minY.toInt(), maxY.toInt(),
                0, 0
            )
            isFlinging = true
            postOnAnimation(flingRunnable)
            return true
        }
    })

    private val flingRunnable = object : Runnable {
        override fun run() {
            if (scroller.computeScrollOffset()) {
                val values = FloatArray(9)
                matrix.getValues(values)
                values[Matrix.MTRANS_X] = scroller.currX.toFloat()
                values[Matrix.MTRANS_Y] = scroller.currY.toFloat()
                matrix.setValues(values)
                clampBounds()
                imageMatrix = matrix
                scrollCallback?.onScroll(getTransY())
                postOnAnimation(this)
            } else {
                isFlinging = false
            }
        }
    }

    private fun getTransY(): Float {
        val values = FloatArray(9)
        matrix.getValues(values)
        return values[Matrix.MTRANS_Y]
    }

    init {
        scaleType = ScaleType.MATRIX
        isClickable = true
    }

    private fun animateZoom(targetScale: Float, focusX: Float, focusY: Float) {
        zoomAnimator?.cancel()
        val startScale = getCurrentScale()
        val savedMatrix = Matrix(matrix)

        zoomAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 250
            addUpdateListener { anim ->
                val t = anim.animatedFraction
                val curScale = startScale + (targetScale - startScale) * t
                val ratio = curScale / startScale
                matrix.set(savedMatrix)
                matrix.postScale(ratio, ratio, focusX, focusY)
                clampBounds()
                imageMatrix = matrix
                scrollCallback?.onScroll(getTransY())
            }
            start()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)

        val action = event.actionMasked
        when (action) {
            MotionEvent.ACTION_DOWN -> {
                activePointerId = event.getPointerId(0)
                lastTouchX = event.x
                lastTouchY = event.y
                isDragging = false
                isFlinging = false
                scroller.forceFinished(true)
                savedMatrix.set(matrix)
            }
            MotionEvent.ACTION_MOVE -> {
                val pointerIndex = event.findPointerIndex(activePointerId)
                if (pointerIndex == -1) return true
                val x = event.getX(pointerIndex)
                val y = event.getY(pointerIndex)
                val dx = x - lastTouchX
                val dy = y - lastTouchY

                if (!isDragging && (dx * dx + dy * dy) > touchSlop * touchSlop) {
                    isDragging = true
                }

                if (isDragging && !scaleDetector.isInProgress) {
                    matrix.postTranslate(dx, dy)
                    clampBounds()
                    imageMatrix = matrix
                    val values = FloatArray(9)
                    matrix.getValues(values)
                    scrollCallback?.onScroll(values[Matrix.MTRANS_Y])
                }

                lastTouchX = x
                lastTouchY = y
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                activePointerId = -1
                isDragging = false
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val pointerIndex = event.actionIndex
                val pointerId = event.getPointerId(pointerIndex)
                if (pointerId == activePointerId) {
                    val newIndex = if (pointerIndex == 0) 1 else 0
                    activePointerId = event.getPointerId(newIndex)
                    lastTouchX = event.getX(newIndex)
                    lastTouchY = event.getY(newIndex)
                }
            }
        }
        return true
    }

    private fun clampBounds() {
        val drawable = drawable ?: return
        vw = width.toFloat()
        vh = height.toFloat()
        val dw = drawable.intrinsicWidth.toFloat()
        val dh = drawable.intrinsicHeight.toFloat()

        val values = FloatArray(9)
        matrix.getValues(values)
        val scale = values[Matrix.MSCALE_X]
        var tx = values[Matrix.MTRANS_X]
        var ty = values[Matrix.MTRANS_Y]

        imgW = dw * scale
        imgH = dh * scale

        if (imgW > vw) {
            tx = max(vw - imgW, Math.min(0f, tx))
        } else {
            tx = (vw - imgW) / 2f
        }

        if (imgH > vh) {
            val maxY = scrollPaddingTop
            val minY = vh - imgH - scrollPaddingBottom
            ty = max(minY, Math.min(maxY, ty))
        } else {
            ty = scrollPaddingTop
        }

        values[Matrix.MTRANS_X] = tx
        values[Matrix.MTRANS_Y] = ty
        matrix.setValues(values)
        currentScale = scale
    }

    fun fitToWidth() {
        val drawable = drawable ?: return
        vw = width.toFloat()
        if (vw <= 0f) {
            post { fitToWidth() }
            return
        }
        val dw = drawable.intrinsicWidth.toFloat()
        val dh = drawable.intrinsicHeight.toFloat()
        if (dw <= 0 || dh <= 0) return

        matrix.reset()
        val scale = vw / dw
        minScale = scale
        matrix.postScale(scale, scale, 0f, 0f)
        imageMatrix = matrix
        currentScale = scale
    }

    fun resetView() {
        zoomAnimator?.cancel()
        scroller.forceFinished(true)
        isFlinging = false
        matrix.reset()
        fitToWidth()
        imageMatrix = matrix
    }

    fun scrollToBitmapY(bitmapY: Int) {
        val scale = getCurrentScale()
        val targetTy = -(bitmapY * scale)
        val values = FloatArray(9)
        matrix.getValues(values)
        values[Matrix.MTRANS_Y] = targetTy
        matrix.setValues(values)
        clampBounds()
        imageMatrix = matrix
        scrollCallback?.onScroll(getTransY())
    }
}
