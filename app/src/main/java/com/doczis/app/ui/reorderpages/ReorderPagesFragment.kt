package com.doczis.app.ui.reorderpages

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
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.doczis.app.DoczisApp
import com.doczis.app.data.db.FileEntity
import com.doczis.app.databinding.FragmentReorderPagesBinding
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

class ReorderPagesFragment : Fragment() {

    private var _binding: FragmentReorderPagesBinding? = null
    private val binding get() = _binding!!
    private var adapter: PageReorderAdapter? = null
    private var sourceRenderer: PdfRenderer? = null
    private var sourceFd: android.os.ParcelFileDescriptor? = null
    private var sourceFile: File? = null
    private var pageCount = 0
    private var pageDataList = mutableListOf<PageData>()
    private var suggestedFileName = "Doczis_Reordered.pdf"

    private val pdfPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { loadPdf(it) }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentReorderPagesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                com.doczis.app.R.id.action_save -> {
                    if (!Debounce.isDuplicate()) saveReorderedPdf()
                    true
                }
                else -> false
            }
        }

        binding.selectPdfButton.setOnClickListener {
            if (Debounce.isDuplicate(it)) return@setOnClickListener
            pdfPicker.launch(arrayOf("application/pdf"))
        }
        binding.saveButton.setOnClickListener {
            if (!Debounce.isDuplicate(it)) saveReorderedPdf()
        }
    }

    private fun loadPdf(uri: Uri) {
        lifecycleScope.launch {
            try {
                suggestedFileName = extractFileName(uri)
                binding.selectPdfButton.isEnabled = false
                binding.selectPdfButton.text = "Loading..."

                val file = withContext(Dispatchers.IO) {
                    val tempFile = File(requireContext().cacheDir, "reorder_${System.nanoTime()}.pdf")
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
                        pageDataList.add(PageData(bitmap, i, origW, origH))
                    }
                }

                MemoryMonitor.logMemory(requireContext(), "reorderPagesLoad")
                adapter = PageReorderAdapter(pageDataList) { holder ->
                    touchHelper.startDrag(holder)
                }
                binding.pagesRecyclerView.apply {
                    layoutManager = LinearLayoutManager(requireContext())
                    adapter = this@ReorderPagesFragment.adapter
                }
                touchHelper.attachToRecyclerView(binding.pagesRecyclerView)

                binding.pageCount.text = "$pageCount page(s) — drag to reorder"
                binding.pageCount.isVisible = true
                binding.pagesRecyclerView.isVisible = true
                binding.selectPdfButton.isVisible = false
                binding.saveButton.isVisible = true
            } catch (e: Exception) {
                ErrorHandler.logError("reorderPagesLoad", e)
                Toast.makeText(requireContext(), ErrorHandler.handleFileOpenError(requireContext(), e), Toast.LENGTH_LONG).show()
                binding.selectPdfButton.isEnabled = true
                binding.selectPdfButton.text = "Select PDF"
            }
        }
    }

    private val touchHelper by lazy {
        ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                adapter?.move(viewHolder.adapterPosition, target.adapterPosition)
                return true
            }
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}
            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(viewHolder, actionState)
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) viewHolder?.itemView?.alpha = 0.7f
            }
            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                viewHolder.itemView.alpha = 1.0f
            }
        })
    }

    private fun extractFileName(uri: Uri): String {
        var name = "Doczis_Reordered.pdf"
        if (uri.scheme == "content") {
            val cursor = requireContext().contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                val nameIdx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIdx >= 0 && it.moveToFirst()) {
                    val raw = it.getString(nameIdx)
                    val dot = raw.lastIndexOf('.')
                    name = if (dot > 0) "${raw.substring(0, dot)}_reordered.pdf" else "${raw}_reordered.pdf"
                }
            }
        } else if (uri.scheme == "file") {
            val raw = File(uri.path ?: "").name
            val dot = raw.lastIndexOf('.')
            name = if (dot > 0) "${raw.substring(0, dot)}_reordered.pdf" else "${raw}_reordered.pdf"
        }
        return name
    }

    private fun saveReorderedPdf() {
        if (pageCount == 0 || sourceFile == null) {
            Toast.makeText(requireContext(), "No PDF loaded", Toast.LENGTH_SHORT).show()
            return
        }

        val input = EditText(requireContext()).apply {
            setText(suggestedFileName)
            val dot = suggestedFileName.lastIndexOf('.')
            if (dot > 0) setSelection(0, dot)
        }

        AlertDialog.Builder(requireContext()).apply {
            setTitle("Save as")
            setView(input)
            setPositiveButton("Save") { _, _ ->
                var customName = input.text.toString().trim()
                if (customName.isBlank()) customName = suggestedFileName
                if (!customName.endsWith(".pdf", true)) customName += ".pdf"

                binding.progressOverlay.isVisible = true
                binding.toolbar.menu.findItem(com.doczis.app.R.id.action_save).isEnabled = false

                lifecycleScope.launch {
                    try {
                        val result = withContext(Dispatchers.IO) { generatePdf(customName) }
                        val app = requireActivity().application as DoczisApp
                        app.fileRepository.insert(
                            FileEntity(
                                fileName = result.first,
                                filePath = result.second,
                                fileSize = result.third,
                                toolType = "reorder_pages"
                            )
                        )
                        NotificationHelper.showFileSavedNotification(requireContext(), "PDF Reordered", result.first, result.second)
                        Toast.makeText(requireContext(), "Saved: ${result.first}", Toast.LENGTH_LONG).show()
                        findNavController().navigateUp()
                    } catch (e: Exception) {
                        ErrorHandler.logError("reorderPagesSave", e)
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

    private fun generatePdf(fileName: String): Triple<String, String, Long> {
        val r = sourceRenderer ?: throw Exception("PDF not loaded")
        val document = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842

        try {
            for (pageData in pageDataList) {
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
