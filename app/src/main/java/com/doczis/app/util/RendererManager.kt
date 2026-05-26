package com.doczis.app.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.Closeable
import java.io.File

class RendererManager : Closeable {

    private var renderer: PdfRenderer? = null
    private var fd: ParcelFileDescriptor? = null
    private var _pageCount: Int = 0

    val pageCount: Int get() = _pageCount

    fun open(file: File): Result<Unit> = runCatching {
        close()
        fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        renderer = PdfRenderer(fd!!)
        _pageCount = renderer!!.pageCount
    }

    fun openWithDescriptor(pfd: ParcelFileDescriptor): Result<Unit> = runCatching {
        close()
        fd = pfd
        renderer = PdfRenderer(pfd)
        _pageCount = renderer!!.pageCount
    }

    fun renderPage(
        index: Int,
        width: Int,
        height: Int? = null,
        config: Bitmap.Config = Bitmap.Config.ARGB_8888,
        scale: Int = 1
    ): Bitmap? {
        val r = renderer ?: return null
        if (index < 0 || index >= _pageCount) return null
        return try {
            val page = r.openPage(index) ?: return null
            val pageH = height ?: (page.height * width.toFloat() / page.width).toInt()
            val bitmap = try {
                Bitmap.createBitmap(width, pageH, config)
            } catch (e: OutOfMemoryError) {
                Bitmap.createBitmap(width.coerceAtMost(512), pageH.coerceAtMost(512), Bitmap.Config.ARGB_8888)
            }
            page.render(bitmap, null, null, scale)
            page.close()
            bitmap
        } catch (e: Exception) {
            null
        }
    }

    fun renderThumbnail(index: Int, thumbWidth: Int = 200): Bitmap? {
        return renderPage(index, thumbWidth, config = Bitmap.Config.ARGB_8888)
    }

    fun openPage(index: Int): PdfRenderer.Page? {
        return try {
            renderer?.openPage(index)
        } catch (e: Exception) {
            null
        }
    }

    override fun close() {
        try { renderer?.close() } catch (_: Exception) {}
        renderer = null
        try { fd?.close() } catch (_: Exception) {}
        fd = null
        _pageCount = 0
    }

    protected fun finalize() {
        close()
    }
}
