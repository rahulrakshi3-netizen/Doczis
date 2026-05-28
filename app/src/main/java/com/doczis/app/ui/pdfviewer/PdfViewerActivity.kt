package com.doczis.app.ui.pdfviewer

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.doczis.app.R
import com.doczis.app.databinding.ActivityPdfViewerBinding
import com.doczis.app.util.Debounce
import com.doczis.app.util.ErrorHandler
import com.doczis.app.util.FileSaveManager
import com.doczis.app.util.MemoryMonitor
import com.doczis.app.util.NotificationHelper
import com.tom_roush.pdfbox.pdmodel.PDDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class PdfViewerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPdfViewerBinding
    private var renderer: PdfRenderer? = null
    private var fd: ParcelFileDescriptor? = null
    private var pageCount = 0
    private var pageHeights: IntArray? = null
    private var fileName = "PDF"
    private var currentFile: File? = null
    private var originalUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPdfViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val uri = intent.data ?: run {
            Toast.makeText(this, "No file specified", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        originalUri = uri

        binding.backButton.setOnClickListener { finish() }
        binding.zoomImageView.setOnClickListener { toggleUI() }

        loadPdfAsync(uri)
    }

    private fun loadPdfAsync(uri: Uri) {
        binding.progressOverlay.isVisible = true

        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    loadAndMeasurePdf(uri)
                }
                result.onSuccess { (tempFile, renderW, totalH, heights, scale) ->
                    currentFile = tempFile
                    pageHeights = heights
                    pageCount = heights.size
                    fileName = FileSaveManager.getFileName(this@PdfViewerActivity, uri, "PDF")

                    val safeTotalH = totalH.coerceAtMost(14000)

                    val bigBitmap = withContext(Dispatchers.IO) {
                        try {
                            renderAllPages(renderW, safeTotalH, heights)
                        } catch (e: OutOfMemoryError) {
                            renderAllPages((renderW / 2).coerceAtLeast(320), (safeTotalH / 2).coerceAtLeast(1000), heights.map { it / 2 }.toIntArray())
                        }
                    }

                    binding.zoomImageView.setImageBitmap(bigBitmap)
                    binding.zoomImageView.post {
                        try {
                            with(binding) {
                                zoomImageView.scrollPaddingTop = topBar.height.toFloat()
                                zoomImageView.scrollPaddingBottom = bottomBar.height.toFloat()
                                zoomImageView.fitToWidth()
                                zoomImageView.matrix.postTranslate(0f, zoomImageView.scrollPaddingTop)
                                zoomImageView.imageMatrix = zoomImageView.matrix
                            }
                        } catch (_: Exception) {}
                    }
                    binding.progressOverlay.isVisible = false

                    binding.pageIndicatorTop.text = fileName
                    updatePageIndicator(0)

                    binding.zoomImageView.setOnScrollCallback(object : ZoomableImageView.OnScrollCallback {
                        override fun onScroll(transY: Float) { updatePageFromScroll(transY) }
                    })
                    binding.zoomImageView.setOnZoomCallback(object : ZoomableImageView.OnZoomCallback {
                        override fun onZoom(transY: Float) { updatePageFromScroll(transY) }
                    })

                    setupBottomBar(tempFile)
                    goImmersive()
                }.onFailure { error ->
                    withContext(Dispatchers.Main) {
                        binding.progressOverlay.isVisible = false
                        if (error is SecurityException) {
                            showPasswordDialog(uri)
                        } else {
                            val msg = ErrorHandler.handleFileOpenError(this@PdfViewerActivity, error)
                            Toast.makeText(this@PdfViewerActivity, msg, Toast.LENGTH_LONG).show()
                            finish()
                        }
                    }
                }
            } catch (e: Exception) {
                binding.progressOverlay.isVisible = false
                ErrorHandler.logError("loadPdf", e)
                Toast.makeText(this@PdfViewerActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private fun showPasswordDialog(uri: Uri) {
        val input = EditText(this).apply {
            hint = "Enter PDF password"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        AlertDialog.Builder(this)
            .setTitle("Password Required")
            .setMessage("This PDF is password-protected")
            .setView(input)
            .setPositiveButton("Open") { _, _ ->
                val password = input.text.toString()
                if (password.isBlank()) {
                    showPasswordDialog(uri)
                    return@setPositiveButton
                }
                decryptAndOpen(uri, password)
            }
            .setNegativeButton("Cancel") { _, _ -> finish() }
            .setCancelable(false)
            .show()
    }

    private fun decryptAndOpen(uri: Uri, password: String) {
        binding.progressOverlay.isVisible = true
        lifecycleScope.launch {
            try {
                val decryptedFile = withContext(Dispatchers.IO) {
                    decryptPdf(uri, password)
                }
                val fileUri = FileProvider.getUriForFile(this@PdfViewerActivity, "${packageName}.fileprovider", decryptedFile)
                loadPdfAsync(fileUri)
            } catch (e: Exception) {
                binding.progressOverlay.isVisible = false
                ErrorHandler.logError("decryptPdf", e)
                val msg = when {
                    e.message?.contains("password", true) == true -> "Incorrect password"
                    else -> "Could not open: ${e.message}"
                }
                Toast.makeText(this@PdfViewerActivity, msg, Toast.LENGTH_LONG).show()
                showPasswordDialog(uri)
            }
        }
    }

    private fun decryptPdf(uri: Uri, password: String): File {
        val tempFile = File(cacheDir, "decrypt_${System.nanoTime()}.pdf")
        contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(tempFile).use { output -> input.copyTo(output) }
        } ?: throw Exception("Cannot read file")

        val doc = PDDocument.load(tempFile, password)
        doc.setAllSecurityToBeRemoved(true)
        doc.save(tempFile)
        doc.close()
        return tempFile
    }

    private data class PdfLoadResult(
        val tempFile: File,
        val renderW: Int,
        val totalH: Int,
        val heights: IntArray,
        val scale: Float
    )

    private fun loadAndMeasurePdf(uri: Uri): Result<PdfLoadResult> = runCatching {
        val tempFile = File(cacheDir, "view_${System.nanoTime()}.pdf")
        contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(tempFile).use { output -> input.copyTo(output) }
        } ?: throw IllegalStateException("Cannot read file")

        if (tempFile.length() > 200L * 1024 * 1024) {
            throw OutOfMemoryError("File too large (${FileSaveManager.formatSize(tempFile.length())})")
        }

        fd = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
        renderer = PdfRenderer(fd!!)
        pageCount = renderer!!.pageCount

        if (pageCount == 0) throw IllegalStateException("Empty PDF")

        fileName = FileSaveManager.getFileName(this, uri, "PDF")

        val dm = resources.displayMetrics
        val screenW = dm.widthPixels

        val maxPages = 100
        val pagesToRender = minOf(pageCount, maxPages)

        val first = renderer!!.openPage(0)
        val renderScale = screenW.toFloat() / first.width
        first.close()

        val heights = IntArray(pagesToRender)
        var totalH = 0

        for (i in 0 until pagesToRender) {
            val page = renderer!!.openPage(i)
            heights[i] = (page.height * renderScale).toInt()
            totalH += heights[i]
            page.close()
        }

        val maxCompositePixels = 2160 * 10000
        val adjustedScale: Float
        val renderW: Int

        if (screenW.toLong() * totalH > maxCompositePixels) {
            val reduceFactor = kotlin.math.sqrt(
                maxCompositePixels.toFloat() / (screenW.toFloat() * totalH)
            )
            renderW = (screenW * reduceFactor).toInt().coerceIn(480, screenW)
            adjustedScale = renderScale * reduceFactor
            totalH = 0
            for (i in 0 until pagesToRender) {
                val page = renderer!!.openPage(i)
                heights[i] = (page.height * adjustedScale).toInt()
                totalH += heights[i]
                page.close()
            }
        } else {
            renderW = screenW
            adjustedScale = renderScale
        }

        pdfInfo(fileName, pageCount, tempFile.length())
        PdfLoadResult(tempFile, renderW, totalH, heights, adjustedScale)
    }

    private fun renderAllPages(
        renderW: Int,
        safeTotalH: Int,
        heights: IntArray
    ): Bitmap {
        val renderer = renderer ?: throw IllegalStateException("Renderer not initialized")
        val pagesToRender = heights.size

        val bigBitmap: Bitmap = try {
            Bitmap.createBitmap(renderW, safeTotalH, Bitmap.Config.ARGB_8888)
        } catch (e: Exception) {
            ErrorHandler.logError("createBigBitmap", e)
            Bitmap.createBitmap(renderW.coerceAtMost(720), safeTotalH.coerceAtMost(4000), Bitmap.Config.ARGB_8888)
        }
        val canvas = Canvas(bigBitmap)
        val paint = Paint().apply { isFilterBitmap = true }
        var yOff = 0

        for (i in 0 until pagesToRender) {
            if (yOff >= bigBitmap.height) break
            val pageH = heights[i].coerceAtMost(bigBitmap.height - yOff)
            if (pageH <= 0) break

            var pageBitmap: Bitmap? = null
            try {
                val page = renderer.openPage(i) ?: continue
                pageBitmap = Bitmap.createBitmap(renderW, pageH, Bitmap.Config.ARGB_8888)
                page.render(pageBitmap, null, null, 1)
                page.close()

                canvas.drawBitmap(pageBitmap, 0f, yOff.toFloat(), paint)
            } catch (e: OutOfMemoryError) {
                ErrorHandler.logError("renderPage_$i OOM", e)
                break
            } catch (e: Exception) {
                ErrorHandler.logError("renderPage_$i", e)
            } finally {
                pageBitmap?.recycle()
            }

            if (i < pagesToRender - 1 && yOff + pageH < bigBitmap.height) {
                paint.color = 0xFFCFD8DC.toInt()
                canvas.drawRect(0f, (yOff + pageH - 1).toFloat(), renderW.toFloat(), (yOff + pageH + 1).toFloat(), paint)
                paint.isFilterBitmap = true
            }
            yOff += pageH
        }

        MemoryMonitor.logMemory(this, "renderComplete")
        return bigBitmap
    }

    private fun pdfInfo(name: String, pages: Int, size: Long) {
        ErrorHandler.log("PdfViewer", "Opened: $name, $pages pages, ${FileSaveManager.formatSize(size)}")
    }

    override fun onDestroy() {
        super.onDestroy()
        cleanup()
    }

    private fun cleanup() {
        try { renderer?.close() } catch (_: Exception) {}
        renderer = null
        try { fd?.close() } catch (_: Exception) {}
        fd = null
        currentFile = null
    }

    private fun setupBottomBar(cachedFile: File) {
        binding.shareButton.setOnClickListener {
            val shareUri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", cachedFile)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, shareUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Share PDF"))
        }

        binding.toolsButton.setOnClickListener { view ->
            AlertDialog.Builder(this)
                .setTitle("Tools")
                .setItems(arrayOf("Go to Page")) { _, which ->
                    when (which) {
                        0 -> goToPage()
                    }
                }
                .show()
        }

        binding.moreButton.setOnClickListener { view ->
            val items = arrayOf("Rename", "Save to Device", "Delete", "Details")
            AlertDialog.Builder(this)
                .setTitle("Options")
                .setItems(items) { _, which ->
                    when (which) {
                        0 -> showRenameDialog()
                        1 -> saveToDownloads()
                        2 -> showDeleteConfirm()
                        3 -> showDetails()
                    }
                }
                .show()
        }
    }

    private fun goToPage() {
        val heights = pageHeights ?: return
        val input = EditText(this).apply {
            hint = "Page (1-$pageCount)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        AlertDialog.Builder(this)
            .setTitle("Go to Page")
            .setView(input)
            .setPositiveButton("Go") { _, _ ->
                val pageNum = input.text.toString().toIntOrNull()
                if (pageNum == null || pageNum < 1 || pageNum > pageCount) {
                    Toast.makeText(this, "Enter a number between 1 and $pageCount", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                var bitmapY = 0
                for (i in 0 until pageNum - 1) {
                    bitmapY += heights[i]
                }
                binding.zoomImageView.scrollToBitmapY(bitmapY)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showRenameDialog() {
        val file = currentFile ?: return
        val input = EditText(this).apply {
            setText(fileName)
            val dot = fileName.lastIndexOf('.')
            if (dot > 0) setSelection(0, dot)
        }
        AlertDialog.Builder(this).apply {
            setTitle("Rename")
            setView(input)
            setPositiveButton("Rename") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotBlank()) {
                    val newFile = File(file.parentFile, newName)
                    if (file.renameTo(newFile)) {
                        fileName = newName
                        currentFile = newFile
                        binding.pageIndicatorTop.text = fileName
                        Toast.makeText(this@PdfViewerActivity, "Renamed", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@PdfViewerActivity, "Rename failed", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            setNegativeButton("Cancel", null)
        }.show()
    }

    private fun saveToDownloads() {
        if (Debounce.isDuplicate()) return
        val file = currentFile ?: return
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    FileSaveManager.saveToDownloads(this@PdfViewerActivity, file, fileName)
                }
                NotificationHelper.showFileSavedNotification(this@PdfViewerActivity, "Save", fileName, file.absolutePath)
                Toast.makeText(this@PdfViewerActivity, "Saved to Downloads/Doczis/$fileName", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(this@PdfViewerActivity, ErrorHandler.handleSaveError(this@PdfViewerActivity, e), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showDetails() {
        val file = currentFile ?: return
        val size = FileSaveManager.formatSize(file.length())
        val path = originalUri?.toString() ?: file.absolutePath
        val info = buildString {
            append("Name: $fileName\n")
            append("Type: PDF\n")
            append("Pages: $pageCount\n")
            append("Size: $size\n")
            append("Path: $path\n")
        }
        AlertDialog.Builder(this).apply {
            setTitle("Details")
            setMessage(info)
            setPositiveButton("OK", null)
        }.show()
    }

    private fun showDeleteConfirm() {
        val file = currentFile ?: return
        AlertDialog.Builder(this).apply {
            setTitle("Delete PDF")
            setMessage("Delete \"$fileName\"?")
            setPositiveButton("Delete") { _, _ ->
                file.delete()
                Toast.makeText(this@PdfViewerActivity, "Deleted", Toast.LENGTH_SHORT).show()
                finish()
            }
            setNegativeButton("Cancel", null)
        }.show()
    }

    private fun updatePageFromScroll(transY: Float) {
        val h = pageHeights ?: return
        if (h.isEmpty()) return
        val scale = binding.zoomImageView.getCurrentScale()
        val absY = (-transY) / scale
        var cum = 0
        for (i in h.indices) {
            cum += h[i]
            if (absY < cum) {
                updatePageIndicator(i)
                return
            }
        }
        updatePageIndicator(h.lastIndex)
    }

    private fun updatePageIndicator(currentIndex: Int) {
        val page = currentIndex + 1
        val totalShown = minOf(pageCount, pageHeights?.size ?: pageCount)
        binding.pageIndicatorRight.text = "$page / $totalShown"
    }

    private var uiVisible = true
    private fun toggleUI() {
        uiVisible = !uiVisible
        val vis = if (uiVisible) View.VISIBLE else View.GONE
        binding.topBar.visibility = vis
        binding.bottomBar.visibility = vis
        binding.pageIndicatorRight.visibility = vis
        if (uiVisible) {
            WindowInsetsControllerCompat(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
        } else {
            goImmersive()
        }
    }

    private fun goImmersive() {
        WindowInsetsControllerCompat(window, window.decorView).let { ctrl ->
            ctrl.hide(WindowInsetsCompat.Type.systemBars())
            ctrl.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}
