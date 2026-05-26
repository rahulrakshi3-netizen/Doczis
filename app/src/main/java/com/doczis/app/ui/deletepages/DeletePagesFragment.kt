package com.doczis.app.ui.deletepages

import android.graphics.Bitmap
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
import androidx.recyclerview.widget.LinearLayoutManager
import com.doczis.app.DoczisApp
import com.doczis.app.data.db.FileEntity
import com.doczis.app.databinding.FragmentDeletePagesBinding
import com.doczis.app.util.Debounce
import com.doczis.app.util.ErrorHandler
import com.doczis.app.util.FileSaveManager
import com.doczis.app.util.MemoryMonitor
import com.doczis.app.util.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class DeletePagesFragment : Fragment() {

    private var _binding: FragmentDeletePagesBinding? = null
    private val binding get() = _binding!!
    private var adapter: PageDeleteAdapter? = null
    private var sourceRenderer: PdfRenderer? = null
    private var sourceFd: android.os.ParcelFileDescriptor? = null
    private var sourceFile: File? = null
    private var pageCount = 0
    private var pageDataList = mutableListOf<DeletePageData>()
    private var suggestedFileName = "Doczis_Deleted.pdf"

    private val pdfPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { loadPdf(it) }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDeletePagesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                com.doczis.app.R.id.action_save -> {
                    if (!Debounce.isDuplicate()) savePdf()
                    true
                }
                else -> false
            }
        }

        binding.selectPdfButton.setOnClickListener {
            if (Debounce.isDuplicate()) return@setOnClickListener
            pdfPicker.launch(arrayOf("application/pdf"))
        }
        binding.saveButton.setOnClickListener {
            if (!Debounce.isDuplicate()) savePdf()
        }
    }

    private fun loadPdf(uri: Uri) {
        lifecycleScope.launch {
            try {
                suggestedFileName = extractFileName(uri)
                binding.selectPdfButton.isEnabled = false
                binding.selectPdfButton.text = "Loading..."

                val file = withContext(Dispatchers.IO) {
                    val tempFile = File(requireContext().cacheDir, "delete_${System.nanoTime()}.pdf")
                    requireContext().contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(tempFile).use { output -> input.copyTo(output) }
                    }
                    tempFile
                }
                sourceFile = file

                withContext(Dispatchers.IO) {
                    closeRenderer()
                    sourceFd = android.os.ParcelFileDescriptor.open(file, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
                    sourceRenderer = PdfRenderer(sourceFd!!)
                    pageCount = sourceRenderer!!.pageCount
                    if (pageCount == 0) throw IllegalStateException("Empty PDF")

                    pageDataList.clear()
                    val dm = requireContext().resources.displayMetrics
                    val thumbW = (dm.widthPixels / 4).coerceIn(150, 300)
                    val maxThumbnails = 200
                    val pagesToRender = minOf(pageCount, maxThumbnails)

                    for (i in 0 until pagesToRender) {
                        val page = sourceRenderer!!.openPage(i)
                        val scale = thumbW.toFloat() / page.width
                        val thumbH = (page.height * scale).toInt()
                        val bitmap = try {
                            Bitmap.createBitmap(thumbW, thumbH, Bitmap.Config.ARGB_8888)
                        } catch (e: OutOfMemoryError) {
                            Bitmap.createBitmap(thumbW / 2, thumbH / 2, Bitmap.Config.ARGB_8888)
                        }
                        page.render(bitmap, null, null, 1)
                        val origW = page.width
                        val origH = page.height
                        page.close()
                        pageDataList.add(DeletePageData(bitmap, i, origW, origH, checked = false))
                    }
                }

                MemoryMonitor.logMemory(requireContext(), "deletePagesLoad")
                adapter = PageDeleteAdapter(pageDataList)
                binding.pagesRecyclerView.apply {
                    layoutManager = LinearLayoutManager(requireContext())
                    adapter = this@DeletePagesFragment.adapter
                }

                binding.pageCount.text = "$pageCount page(s) — tap to mark for deletion"
                binding.pageCount.isVisible = true
                binding.pagesRecyclerView.isVisible = true
                binding.selectPdfButton.isVisible = false
                binding.saveButton.isVisible = true
            } catch (e: Exception) {
                ErrorHandler.logError("deletePagesLoad", e)
                Toast.makeText(requireContext(), ErrorHandler.handleFileOpenError(requireContext(), e), Toast.LENGTH_LONG).show()
                binding.selectPdfButton.isEnabled = true
                binding.selectPdfButton.text = "Select PDF"
            }
        }
    }

    private fun extractFileName(uri: Uri): String {
        var name = "Doczis_Deleted.pdf"
        if (uri.scheme == "content") {
            val cursor = requireContext().contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                val nameIdx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIdx >= 0 && it.moveToFirst()) {
                    val raw = it.getString(nameIdx)
                    val dot = raw.lastIndexOf('.')
                    name = if (dot > 0) "${raw.substring(0, dot)}_deleted.pdf" else "${raw}_deleted.pdf"
                }
            }
        } else if (uri.scheme == "file") {
            val raw = File(uri.path ?: "").name
            val dot = raw.lastIndexOf('.')
            name = if (dot > 0) "${raw.substring(0, dot)}_deleted.pdf" else "${raw}_deleted.pdf"
        }
        return name
    }

    private fun savePdf() {
        val remaining = adapter?.getRemainingPages() ?: return
        if (remaining.size == pageCount) {
            Toast.makeText(requireContext(), "No pages selected for deletion", Toast.LENGTH_SHORT).show()
            return
        }
        if (remaining.isEmpty()) {
            Toast.makeText(requireContext(), "Cannot delete all pages", Toast.LENGTH_SHORT).show()
            return
        }

        val input = EditText(requireContext()).apply {
            setText(suggestedFileName)
            val dot = suggestedFileName.lastIndexOf('.')
            if (dot > 0) setSelection(0, dot)
        }

        AlertDialog.Builder(requireContext()).apply {
            setTitle("Save as (${remaining.size} pages)")
            setView(input)
            setPositiveButton("Save") { _, _ ->
                var customName = input.text.toString().trim()
                if (customName.isBlank()) customName = suggestedFileName
                if (!customName.endsWith(".pdf", true)) customName += ".pdf"

                binding.progressOverlay.isVisible = true
                binding.toolbar.menu.findItem(com.doczis.app.R.id.action_save).isEnabled = false

                lifecycleScope.launch {
                    try {
                        val result = withContext(Dispatchers.IO) {
                            generatePdf(remaining, customName)
                        }
                        val app = requireActivity().application as DoczisApp
                        app.fileRepository.insert(
                            FileEntity(
                                fileName = result.first,
                                filePath = result.second,
                                fileSize = result.third,
                                toolType = "delete_pages"
                            )
                        )
                        NotificationHelper.showFileSavedNotification(requireContext(), "Pages Deleted", result.first, result.second)
                        Toast.makeText(requireContext(), "Saved: ${result.first}", Toast.LENGTH_LONG).show()
                        findNavController().navigateUp()
                    } catch (e: Exception) {
                        ErrorHandler.logError("deletePagesSave", e)
                        Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_LONG).show()
                    } finally {
                        binding.progressOverlay.isVisible = false
                        binding.toolbar.menu.findItem(com.doczis.app.R.id.action_save).isEnabled = true
                    }
                }
            }
            setNegativeButton("Cancel", null)
            show()
        }
    }

    private fun generatePdf(pages: List<DeletePageData>, fileName: String): Triple<String, String, Long> {
        val r = sourceRenderer ?: throw Exception("PDF not loaded")
        val document = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842

        try {
            for (pageData in pages) {
                val srcPage = r.openPage(pageData.originalIndex)
                val srcW = srcPage.width
                val srcH = srcPage.height
                val renderScale = pageWidth.toFloat() / srcW
                val renderW = (srcW * renderScale).toInt()
                val renderH = (srcH * renderScale).toInt()
                val fullBitmap = try {
                    Bitmap.createBitmap(renderW, renderH, Bitmap.Config.ARGB_8888)
                } catch (e: OutOfMemoryError) {
                    Bitmap.createBitmap(renderW / 2, renderH / 2, Bitmap.Config.ARGB_8888)
                }
                srcPage.render(fullBitmap, null, null, 1)
                srcPage.close()

                val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, document.pages.size + 1).create()
                val page = document.startPage(pageInfo)
                page.canvas.drawBitmap(fullBitmap, 0f, 0f, null)
                fullBitmap.recycle()
                document.finishPage(page)
            }

            if (document.pages.isEmpty()) throw Exception("No pages to save")
            val uri = FileSaveManager.savePdfDocumentToDownloads(requireContext(), document, fileName)
            return Triple(fileName, uri.getOrThrow().toString(), -1L)
        } finally {
        }
    }

    private fun closeRenderer() {
        try { sourceRenderer?.close() } catch (_: Exception) {}
        sourceRenderer = null
        try { sourceFd?.close() } catch (_: Exception) {}
        sourceFd = null
    }

    override fun onDestroyView() {
        closeRenderer()
        _binding = null
        super.onDestroyView()
    }
}
