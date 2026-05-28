package com.doczis.app.ui.watermark

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.doczis.app.DoczisApp
import com.doczis.app.data.db.FileEntity
import com.doczis.app.databinding.FragmentWatermarkBinding
import com.doczis.app.util.Debounce
import com.doczis.app.util.ErrorHandler
import com.doczis.app.util.FileSaveManager
import com.doczis.app.util.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class WatermarkFragment : Fragment() {

    private var _binding: FragmentWatermarkBinding? = null
    private val binding get() = _binding!!
    private var renderer: PdfRenderer? = null
    private var rendererFd: android.os.ParcelFileDescriptor? = null
    private var sourceFile: File? = null
    private var originalSize = 0L
    private var pageCount = 0
    private var suggestedFileName = "Doczis_Watermarked.pdf"

    private val filePicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { loadFile(it) }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWatermarkBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.selectPdfButton.setOnClickListener {
            if (Debounce.isDuplicate()) return@setOnClickListener
            filePicker.launch(arrayOf("application/pdf"))
        }
        binding.opacitySlider.addOnChangeListener { _, value, _ ->
            binding.opacityLabel.text = "${(value * 100).toInt()}% opacity"
        }
        binding.watermarkButton.setOnClickListener {
            if (Debounce.isDuplicate()) return@setOnClickListener
            startWatermark()
        }
    }

    private fun loadFile(uri: Uri) {
        lifecycleScope.launch {
            try {
                suggestedFileName = FileSaveManager.getFileName(requireContext(), uri, "Doczis_Watermarked.pdf")
                val dot = suggestedFileName.lastIndexOf('.')
                val base = if (dot > 0) suggestedFileName.substring(0, dot) else suggestedFileName
                suggestedFileName = "${base}_watermarked.pdf"

                binding.selectPdfButton.isEnabled = false
                binding.selectPdfButton.text = "Loading..."

                val file = withContext(Dispatchers.IO) {
                    val tempFile = File(requireContext().cacheDir, "watermark_${System.nanoTime()}.pdf")
                    requireContext().contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(tempFile).use { output -> input.copyTo(output) }
                    }
                    tempFile
                }
                sourceFile = file
                originalSize = file.length()

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

                binding.fileInfo.text = "PDF — ${pageCount} pages, ${FileSaveManager.formatSize(originalSize)}"
                binding.fileInfo.isVisible = true
                binding.watermarkControls.isVisible = true
                binding.selectPdfButton.isVisible = false
            } catch (e: Exception) {
                ErrorHandler.logError("watermarkLoad", e)
                Toast.makeText(requireContext(), ErrorHandler.handleFileOpenError(requireContext(), e), Toast.LENGTH_LONG).show()
                resetUi()
            }
        }
    }

    private fun startWatermark() {
        val text = binding.watermarkTextInput.text?.toString()?.trim() ?: ""
        if (text.isBlank()) {
            Toast.makeText(requireContext(), "Enter watermark text", Toast.LENGTH_SHORT).show()
            return
        }

        val opacity = binding.opacitySlider.value
        val position = getSelectedPosition()

        binding.progressOverlay.isVisible = true
        binding.watermarkButton.isEnabled = false

        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    addWatermark(suggestedFileName, text, opacity, position)
                }
                val app = requireActivity().application as DoczisApp
                app.fileRepository.insert(
                    FileEntity(
                        fileName = result.first,
                        filePath = result.second,
                        fileSize = result.third,
                        toolType = "watermark"
                    )
                )
                NotificationHelper.showFileSavedNotification(requireContext(), "Watermarked", result.first, result.second)
                Toast.makeText(requireContext(), "Saved: ${result.first}", Toast.LENGTH_LONG).show()
                findNavController().navigateUp()
            } catch (e: Exception) {
                ErrorHandler.logError("watermarkSave", e)
                Toast.makeText(requireContext(), ErrorHandler.handleSaveError(requireContext(), e), Toast.LENGTH_LONG).show()
            } finally {
                binding.progressOverlay.isVisible = false
                binding.watermarkButton.isEnabled = true
            }
        }
    }

    private fun getSelectedPosition(): String {
        return when (binding.positionChipGroup.checkedChipId) {
            com.doczis.app.R.id.topLeftChip -> "top_left"
            com.doczis.app.R.id.topRightChip -> "top_right"
            com.doczis.app.R.id.bottomLeftChip -> "bottom_left"
            com.doczis.app.R.id.bottomRightChip -> "bottom_right"
            else -> "center"
        }
    }

    private fun addWatermark(
        fileName: String, text: String, opacity: Float, position: String
    ): Triple<String, String, Long> {
        val r = renderer ?: throw Exception("PDF not loaded")
        val document = PdfDocument()

        try {
            for (i in 0 until pageCount) {
                val srcPage = r.openPage(i)
                val w = srcPage.width
                val h = srcPage.height

                val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                srcPage.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                srcPage.close()

                val canvas = Canvas(bitmap)
                val paint = Paint().apply {
                    color = Color.argb((opacity * 255).toInt(), 100, 100, 100)
                    textSize = w * 0.06f
                    isAntiAlias = true
                    style = Paint.Style.FILL
                }

                val textWidth = paint.measureText(text)
                val textHeight = paint.textSize
                val margin = w * 0.06f

                val x = when (position) {
                    "top_left" -> margin
                    "top_right" -> w - textWidth - margin
                    "bottom_left" -> margin
                    "bottom_right" -> w - textWidth - margin
                    else -> (w - textWidth) / 2f
                }
                val y = when (position) {
                    "top_left", "top_right" -> margin + textHeight
                    "bottom_left", "bottom_right" -> h - margin
                    else -> (h + textHeight) / 2f
                }

                canvas.save()
                canvas.rotate(-25f, w / 2f, h / 2f)
                canvas.drawText(text, x, y, paint)
                canvas.restore()

                val pageInfo = PdfDocument.PageInfo.Builder(w, h, document.pages.size + 1).create()
                val page = document.startPage(pageInfo)
                page.canvas.drawBitmap(bitmap, 0f, 0f, null)
                bitmap.recycle()
                document.finishPage(page)
            }

            val tempFile = File(requireContext().cacheDir, "watermark_out_${System.nanoTime()}.pdf")
            FileOutputStream(tempFile).use { document.writeTo(it) }
            val uriResult = FileSaveManager.saveToDownloads(requireContext(), tempFile, fileName)
            tempFile.delete()
            val uri = uriResult.getOrThrow()
            return Triple(fileName, uri.toString(), -1L)
        } finally {
            document.close()
        }
    }

    private fun resetUi() {
        closeRenderer()
        binding.selectPdfButton.isEnabled = true
        binding.selectPdfButton.text = "Select PDF"
        binding.fileInfo.isVisible = false
        binding.watermarkControls.isVisible = false
        binding.selectPdfButton.isVisible = true
    }

    private fun closeRenderer() {
        try { renderer?.close() } catch (_: Exception) {}
        renderer = null
        try { rendererFd?.close() } catch (_: Exception) {}
        rendererFd = null
    }

    override fun onDestroyView() {
        closeRenderer()
        super.onDestroyView()
        _binding = null
    }
}
