package com.doczis.app.ui.signpdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class SignaturePadView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val drawPath = Path()
    private val drawPaint = Paint().apply {
        color = Color.BLACK
        strokeWidth = 4f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        isAntiAlias = true
    }
    private var lastX = 0f
    private var lastY = 0f
    private var signatureBitmap: Bitmap? = null

    override fun onSizeChanged(w: Int, h: Int, oldW: Int, oldH: Int) {
        super.onSizeChanged(w, h, oldW, oldH)
        if (w > 0 && h > 0) {
            signatureBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.WHITE)
        canvas.drawPath(drawPath, drawPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        return when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                drawPath.moveTo(x, y)
                lastX = x
                lastY = y
                true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = Math.abs(x - lastX)
                val dy = Math.abs(y - lastY)
                if (dx > 4 || dy > 4) {
                    drawPath.quadTo(lastX, lastY, (x + lastX) / 2, (y + lastY) / 2)
                    lastX = x
                    lastY = y
                }
                invalidate()
                true
            }
            MotionEvent.ACTION_UP -> {
                drawPath.lineTo(x, y)
                invalidate()
                true
            }
            else -> false
        }
    }

    fun getSignatureBitmap(): Bitmap? {
        val bmp = signatureBitmap ?: return null
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)
        canvas.drawPath(drawPath, drawPaint)
        return bmp
    }

    fun clear() {
        drawPath.rewind()
        invalidate()
    }
}
