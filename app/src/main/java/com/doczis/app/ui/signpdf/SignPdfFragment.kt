package com.doczis.app.ui.signpdf

import android.graphics.Bitmap
import android.graphics.Canvas
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
import com.doczis.app.databinding.FragmentSignPdfBinding
import com.doczis.app.util.Debounce
import com.doczis.app.util.ErrorHandler
import com.doczis.app.util.FileSaveManager
import com.doczis.app.util.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class SignPdfFragment : Fragment() {

    private var _binding: FragmentSignPdfBinding? = null
    private val binding get() = _binding!!
    private var renderer: PdfRenderer? = null
    private var rendererFd: android.os.ParcelFileDescriptor? = null
    private var sourceFile: File? = null
    private var originalSize = 0L
    private var pageCount = 0
    private var suggestedFileName = "Doczis_Signed.pdf"

    private val filePicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { loadFile(it) }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSignPdfBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.selectPdfButton.setOnClickListener {
            if (Debounce.isDuplicate(it)) return@setOnClickListener
            filePicker.launch(arrayOf("application/pdf"))
        }
        binding.clearButton.setOnClickListener {
            binding.signaturePad.clear()
        }
        binding.signButton.setOnClickListener {
            if (Debounce.isDuplicate(it)) return@setOnClickListener
            if (binding.signaturePad.getSignatureBitmap() == null) {
                Toast.makeText(requireContext(), "Draw a signature first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            startSign()
        }
    }

    private fun loadFile(uri: Uri) {
        lifecycleScope.launch {
            try {
                suggestedFileName = FileSaveManager.getFileName(requireContext(), uri, "Doczis_Signed.pdf")
                val dot = suggestedFileName.lastIndexOf('.')
                val base = if (dot > 0) suggestedFileName.substring(0, dot) else suggestedFileName
                suggestedFileName = "${base}_signed.pdf"

                binding.selectPdfButton.isEnabled = false
                binding.selectPdfButton.text = "Loading..."

                val file = withContext(Dispatchers.IO) {
                    val tempFile = File(requireContext().cacheDir, "sign_${System.nanoTime()}.pdf")
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
                binding.signControls.isVisible = true
                binding.selectPdfButton.isVisible = false
            } catch (e: Exception) {
                ErrorHandler.logError("signLoad", e)
                Toast.makeText(requireContext(), ErrorHandler.handleFileOpenError(requireContext(), e), Toast.LENGTH_LONG).show()
                resetUi()
            }
        }
    }

    private fun startSign() {
        binding.progressOverlay.isVisible = true
        binding.signButton.isEnabled = false

        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    signPdf(suggestedFileName)
                }
                val app = requireActivity().application as DoczisApp
                app.fileRepository.insert(
                    FileEntity(
                        fileName = result.first,
                        filePath = result.second,
                        fileSize = result.third,
                        toolType = "sign"
                    )
                )
                NotificationHelper.showFileSavedNotification(requireContext(), "Signed", result.first, result.second)
                Toast.makeText(requireContext(), "Saved: ${result.first}", Toast.LENGTH_LONG).show()
                findNavController().navigateUp()
            } catch (e: Exception) {
                ErrorHandler.logError("signSave", e)
                Toast.makeText(requireContext(), ErrorHandler.handleSaveError(requireContext(), e), Toast.LENGTH_LONG).show()
            } finally {
                binding.progressOverlay.isVisible = false
                binding.signButton.isEnabled = true
            }
        }
    }

    private fun signPdf(fileName: String): Triple<String, String, Long> {
        val r = renderer ?: throw Exception("PDF not loaded")
        val sig = binding.signaturePad.getSignatureBitmap() ?: throw Exception("No signature")
        val document = PdfDocument()

        try {
            for (i in 0 until pageCount) {
                val srcPage = r.openPage(i)
                val w = srcPage.width
                val h = srcPage.height

                val pageBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                srcPage.render(pageBitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                srcPage.close()

                val canvas = Canvas(pageBitmap)
                val sigWidth = (w * 0.3f).toInt().coerceAtMost(sig.width)
                val sigHeight = (sigWidth.toFloat() * sig.height / sig.width).toInt()
                val scaledSig = Bitmap.createScaledBitmap(sig, sigWidth, sigHeight, true)

                val margin = (w * 0.05f).toInt()
                val sx = w - sigWidth - margin
                val sy = h - sigHeight - margin
                canvas.drawBitmap(scaledSig, sx.toFloat(), sy.toFloat(), null)
                scaledSig.recycle()

                val pageInfo = PdfDocument.PageInfo.Builder(w, h, document.pages.size + 1).create()
                val page = document.startPage(pageInfo)
                page.canvas.drawBitmap(pageBitmap, 0f, 0f, null)
                pageBitmap.recycle()
                document.finishPage(page)
            }

            val tempFile = File(requireContext().cacheDir, "sign_out_${System.nanoTime()}.pdf")
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
        binding.signControls.isVisible = false
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
