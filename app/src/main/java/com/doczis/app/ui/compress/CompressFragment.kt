package com.doczis.app.ui.compress

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.doczis.app.DoczisApp
import com.doczis.app.data.db.FileEntity
import com.doczis.app.databinding.FragmentCompressBinding
import com.doczis.app.util.Debounce
import com.doczis.app.util.ErrorHandler
import com.doczis.app.util.FileSaveManager
import com.doczis.app.util.MemoryMonitor
import com.doczis.app.util.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

class CompressFragment : Fragment() {

    private var _binding: FragmentCompressBinding? = null
    private val binding get() = _binding!!
    private var renderer: PdfRenderer? = null
    private var rendererFd: android.os.ParcelFileDescriptor? = null
    private var sourceFile: File? = null
    private var isPdf = false
    private var pageCount = 0
    private var originalSize = 0L
    private var suggestedFileName = "Doczis_Compressed.pdf"

    private val filePicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { loadFile(it) }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCompressBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.selectFileButton.setOnClickListener {
            if (Debounce.isDuplicate(it)) return@setOnClickListener
            filePicker.launch(arrayOf("application/pdf", "image/*"))
        }
        binding.compressButton.setOnClickListener {
            if (Debounce.isDuplicate(it)) return@setOnClickListener
            startCompress()
        }
    }

    private fun loadFile(uri: Uri) {
        lifecycleScope.launch {
            try {
                val mime = requireContext().contentResolver.getType(uri) ?: ""
                isPdf = mime == "application/pdf"
                suggestedFileName = extractFileName(uri)

                binding.selectFileButton.isEnabled = false
                binding.selectFileButton.text = "Loading..."

                val file = withContext(Dispatchers.IO) {
                    val tempFile = File(requireContext().cacheDir, "compress_${System.nanoTime()}.tmp")
                    requireContext().contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(tempFile).use { output -> input.copyTo(output) }
                    }
                    tempFile
                }
                sourceFile = file
                originalSize = file.length()

                val maxFileSize = 200L * 1024 * 1024
                if (originalSize > maxFileSize) {
                    Toast.makeText(requireContext(), "File too large (${FileSaveManager.formatSize(originalSize)})", Toast.LENGTH_LONG).show()
                    resetUi()
                    return@launch
                }

                if (isPdf) {
                    withContext(Dispatchers.IO) {
                        try {
                            closeRenderer()
                            rendererFd = android.os.ParcelFileDescriptor.open(file, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
                            renderer = PdfRenderer(rendererFd!!)
                            pageCount = renderer!!.pageCount
                            if (pageCount == 0) throw IllegalStateException("Empty PDF")
                        } catch (e: Exception) {
                            throw e
                        }
                    }
                }

                MemoryMonitor.logMemory(requireContext(), "compressLoad")
                val type = if (isPdf) "PDF" else "Image"
                binding.fileInfo.text = "$type — ${FileSaveManager.formatSize(originalSize)}"
                binding.fileInfo.isVisible = true
                binding.compressControls.isVisible = true
                binding.selectFileButton.isVisible = false
            } catch (e: Exception) {
                ErrorHandler.logError("compressLoad", e)
                Toast.makeText(requireContext(), ErrorHandler.handleFileOpenError(requireContext(), e), Toast.LENGTH_LONG).show()
                resetUi()
            }
        }
    }

    private fun startCompress() {
        val targetStr = binding.targetSizeInput.text?.toString()?.trim() ?: ""
        if (targetStr.isBlank()) {
            Toast.makeText(requireContext(), "Enter target size", Toast.LENGTH_SHORT).show()
            return
        }
        val isKb = binding.unitKb.isChecked
        val multiplier = if (isKb) 1024L else 1024L * 1024L
        val targetBytes = ((targetStr.toDoubleOrNull() ?: 0.0) * multiplier).toLong()
        if (targetBytes <= 0 || targetBytes >= originalSize) {
            Toast.makeText(requireContext(), "Target must be smaller than current size", Toast.LENGTH_SHORT).show()
            return
        }

        val input = EditText(requireContext()).apply {
            setText(suggestedFileName)
            val dot = suggestedFileName.lastIndexOf('.')
            if (dot > 0) setSelection(0, dot)
        }
        AlertDialog.Builder(requireContext()).apply {
            setTitle("Save compressed file as")
            setView(input)
            setPositiveButton("Save") { _, _ ->
                var customName = input.text.toString().trim()
                if (customName.isBlank()) customName = suggestedFileName
                val ext = if (isPdf) ".pdf" else ".jpg"
                if (!customName.endsWith(ext, true)) customName += ext
                doCompress(customName, targetBytes.toLong())
            }
            setNegativeButton("Cancel", null)
            show()
        }
    }

    private fun doCompress(fileName: String, targetBytes: Long) {
        binding.progressOverlay.isVisible = true
        binding.compressButton.isEnabled = false

        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    if (isPdf) compressPdf(fileName, targetBytes)
                    else compressImage(fileName, targetBytes)
                }
                val app = requireActivity().application as DoczisApp
                app.fileRepository.insert(
                    FileEntity(
                        fileName = result.first,
                        filePath = result.second,
                        fileSize = result.third,
                        toolType = "compress"
                    )
                )
                NotificationHelper.showFileSavedNotification(requireContext(), "Compressed", result.first, result.second)
                Toast.makeText(requireContext(), "Saved: ${result.first} (${FileSaveManager.formatSize(result.third)})", Toast.LENGTH_LONG).show()
                findNavController().navigateUp()
            } catch (e: Exception) {
                ErrorHandler.logError("compressSave", e)
                Toast.makeText(requireContext(), ErrorHandler.handleSaveError(requireContext(), e), Toast.LENGTH_LONG).show()
            } finally {
                binding.progressOverlay.isVisible = false
                binding.compressButton.isEnabled = true
            }
        }
    }

    private fun compressPdf(fileName: String, targetBytes: Long): Triple<String, String, Long> {
        val r = renderer ?: throw Exception("PDF not loaded")
        val ratio = targetBytes.toFloat() / originalSize.toFloat()
        val renderScale = maxOf(0.5f, kotlin.math.sqrt(ratio))
        val pageWidthPx = (842 * renderScale).toInt().coerceIn(350, 1200)
        val pageHeightPx = (1191 * renderScale).toInt().coerceIn(500, 1600)

        val document = PdfDocument()
        try {
            for (i in 0 until pageCount) {
                val srcPage = r.openPage(i)
                val scale = pageWidthPx.toFloat() / srcPage.width
                val w = (srcPage.width * scale).toInt()
                val h = (srcPage.height * scale).toInt()
                val bitmap = try {
                    Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                } catch (e: OutOfMemoryError) {
                    Bitmap.createBitmap(w / 2, h / 2, Bitmap.Config.ARGB_8888)
                }
                srcPage.render(bitmap, null, null, 1)
                srcPage.close()

                val pageInfo = PdfDocument.PageInfo.Builder(w, h, document.pages.size + 1).create()
                val page = document.startPage(pageInfo)
                page.canvas.drawBitmap(bitmap, 0f, 0f, null)
                bitmap.recycle()
                document.finishPage(page)
            }

            val tempFile = File(requireContext().cacheDir, "compress_out_${System.nanoTime()}.pdf")
            FileOutputStream(tempFile).use { document.writeTo(it) }
            val uriResult = FileSaveManager.saveToDownloads(requireContext(), tempFile, fileName)
            tempFile.delete()
            val uri = uriResult.getOrThrow()
            return Triple(fileName, uri.toString(), -1L)
        } finally {
            document.close()
        }
    }

    private fun compressImage(fileName: String, targetBytes: Long): Triple<String, String, Long> {
        val file = sourceFile ?: throw Exception("No file loaded")

        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)

        var inSampleSize = 1
        var maxDim = maxOf(options.outWidth, options.outHeight)
        while (maxDim > 2048) { inSampleSize *= 2; maxDim /= 2 }

        val decodeOptions = BitmapFactory.Options().apply {
            this.inSampleSize = inSampleSize
            this.inPreferredConfig = Bitmap.Config.RGB_565
        }
        val originalBitmap = BitmapFactory.decodeFile(file.absolutePath, decodeOptions)
            ?: throw Exception("Cannot decode image")

        var quality = 90
        var resultBytes: ByteArray
        while (true) {
            val stream = ByteArrayOutputStream()
            originalBitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
            resultBytes = stream.toByteArray()
            if (resultBytes.size.toLong() <= targetBytes || quality <= 5) break
            quality -= 5
        }
        originalBitmap.recycle()

        val uri = FileSaveManager.saveBytesToDownloads(requireContext(), resultBytes, fileName, "image/jpeg")
        return Triple(fileName, uri.getOrThrow().toString(), resultBytes.size.toLong())
    }

    private fun resetUi() {
        binding.selectFileButton.isEnabled = true
        binding.selectFileButton.text = "Select File"
        binding.fileInfo.isVisible = false
        binding.compressControls.isVisible = false
        binding.selectFileButton.isVisible = true
    }

    private fun extractFileName(uri: Uri): String {
        val raw = FileSaveManager.getFileName(requireContext(), uri, "file")
        val dot = raw.lastIndexOf('.')
        val base = if (dot > 0) raw.substring(0, dot) else raw
        val ext = if (dot > 0) raw.substring(dot) else if (isPdf) ".pdf" else ".jpg"
        return "${base}_compressed$ext"
    }

    private fun closeRenderer() {
        try { renderer?.close() } catch (_: Exception) {}
        renderer = null
        try { rendererFd?.close() } catch (_: Exception) {}
        rendererFd = null
    }

    override fun onDestroyView() {
        closeRenderer()
        _binding = null
        super.onDestroyView()
    }
}
