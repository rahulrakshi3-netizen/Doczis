package com.doczis.app.ui.split

import android.graphics.Bitmap
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
import androidx.recyclerview.widget.GridLayoutManager
import com.doczis.app.databinding.FragmentSplitBinding
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

class SplitFragment : Fragment() {

    private var _binding: FragmentSplitBinding? = null
    private val binding get() = _binding!!
    private var renderer: PdfRenderer? = null
    private var fd: android.os.ParcelFileDescriptor? = null
    private var sourceFile: File? = null
    private var pageCount = 0
    private var adapter: PageCheckAdapter? = null
    private var pdfName = "document.pdf"

    private val filePicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { loadPdf(it) }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSplitBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.selectPdfButton.setOnClickListener {
            if (Debounce.isDuplicate(it)) return@setOnClickListener
            filePicker.launch(arrayOf("application/pdf"))
        }
        binding.selectAllButton.setOnClickListener {
            adapter?.pages?.forEach { it.isSelected = true }
            adapter?.notifyItemRangeChanged(0, adapter?.itemCount ?: 0)
        }
        binding.deselectAllButton.setOnClickListener {
            adapter?.pages?.forEach { it.isSelected = false }
            adapter?.notifyItemRangeChanged(0, adapter?.itemCount ?: 0)
        }
        binding.splitButton.setOnClickListener {
            if (Debounce.isDuplicate(it)) return@setOnClickListener
            startSplit()
        }
    }

    private fun loadPdf(uri: Uri) {
        lifecycleScope.launch {
            try {
                pdfName = FileSaveManager.getFileName(requireContext(), uri, "document.pdf")
                binding.selectPdfButton.isEnabled = false
                binding.selectPdfButton.text = "Loading..."

                val file = withContext(Dispatchers.IO) {
                    val temp = File(requireContext().cacheDir, "split_${System.nanoTime()}.pdf")
                    requireContext().contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(temp).use { output -> input.copyTo(output) }
                    }
                    temp
                }
                sourceFile = file

                val (count, pages) = withContext(Dispatchers.IO) {
                    closeRenderer()
                    fd = android.os.ParcelFileDescriptor.open(file, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
                    renderer = PdfRenderer(fd!!)
                    val c = renderer!!.pageCount
                    if (c == 0) throw IllegalStateException("Empty PDF")

                    val maxThumbnails = 100
                    val pagesToRender = minOf(c, maxThumbnails)
                    val list = mutableListOf<CheckablePage>()
                    for (i in 0 until pagesToRender) {
                        val page = renderer!!.openPage(i)
                        val w = 200
                        val h = (page.height * w.toFloat() / page.width).toInt()
                        val bitmap = try {
                            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        } catch (e: OutOfMemoryError) {
                            Bitmap.createBitmap(w / 2, h / 2, Bitmap.Config.ARGB_8888)
                        }
                        page.render(bitmap, null, null, 1)
                        page.close()
                        list.add(CheckablePage(i, bitmap))
                    }
                    Pair(c, list)
                }
                pageCount = count

                MemoryMonitor.logMemory(requireContext(), "splitLoad")
                binding.fileInfo.text = "$pageCount pages — ${FileSaveManager.formatSize(file.length())}"
                binding.fileInfo.isVisible = true
                binding.splitControls.isVisible = true
                binding.splitButton.isVisible = true
                binding.selectPdfButton.isVisible = false

                adapter = PageCheckAdapter(pages.toMutableList())
                binding.pagesRecyclerView.apply {
                    layoutManager = GridLayoutManager(requireContext(), 2)
                    adapter = this@SplitFragment.adapter
                }
            } catch (e: Exception) {
                ErrorHandler.logError("splitLoad", e)
                Toast.makeText(requireContext(), ErrorHandler.handleFileOpenError(requireContext(), e), Toast.LENGTH_LONG).show()
                resetUi()
            }
        }
    }

    private fun startSplit() {
        val pages = adapter?.pages?.filter { it.isSelected } ?: run {
            Toast.makeText(requireContext(), "No pages selected", Toast.LENGTH_SHORT).show()
            return
        }
        if (pages.isEmpty()) {
            Toast.makeText(requireContext(), "No pages selected", Toast.LENGTH_SHORT).show()
            return
        }

        binding.progressOverlay.isVisible = true
        binding.splitButton.isEnabled = false

        lifecycleScope.launch {
            try {
                var savedCount = 0
                val r = renderer ?: throw Exception("PDF not loaded")
                val pdfBase = pdfName.substringBeforeLast('.')

                withContext(Dispatchers.IO) {
                    for (page in pages) {
                        val outName = "${pdfBase}_page_${page.index + 1}.pdf"
                        val srcPage = r.openPage(page.index)
                        val w = srcPage.width
                        val h = srcPage.height
                        val bitmap = try {
                            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        } catch (e: OutOfMemoryError) {
                            Bitmap.createBitmap(w / 2, h / 2, Bitmap.Config.ARGB_8888)
                        }
                        srcPage.render(bitmap, null, null, 1)
                        srcPage.close()

                        val doc = PdfDocument()
                        val pageInfo = PdfDocument.PageInfo.Builder(w, h, 1).create()
                        val docPage = doc.startPage(pageInfo)
                        docPage.canvas.drawBitmap(bitmap, 0f, 0f, null)
                        bitmap.recycle()
                        doc.finishPage(docPage)

                        val tempFile = File(requireContext().cacheDir, "split_p_${page.index}_${System.nanoTime()}.pdf")
                        FileOutputStream(tempFile).use { doc.writeTo(it) }
                        doc.close()

                        FileSaveManager.saveToDownloads(requireContext(), tempFile, outName, "application/pdf", "Doczis/Split")
                        tempFile.delete()
                        savedCount++

                        withContext(Dispatchers.Main) {
                            binding.progressText.text = "Saved $savedCount of ${pages.size}"
                        }
                    }
                }

                NotificationHelper.showFileSavedNotification(requireContext(), "Split", "${pdfBase}: $savedCount PDFs", null)
                Toast.makeText(requireContext(), "Saved $savedCount PDFs to Downloads/Doczis/Split/", Toast.LENGTH_LONG).show()
                findNavController().navigateUp()
            } catch (e: OutOfMemoryError) {
                ErrorHandler.logError("splitSave", e)
                Toast.makeText(requireContext(), "Out of memory. Try splitting fewer pages.", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                ErrorHandler.logError("splitSave", e)
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                binding.progressOverlay.isVisible = false
                binding.splitButton.isEnabled = true
            }
        }
    }

    private fun resetUi() {
        binding.selectPdfButton.isEnabled = true
        binding.selectPdfButton.text = "Select PDF"
        binding.fileInfo.isVisible = false
        binding.splitControls.isVisible = false
        binding.splitButton.isVisible = false
        binding.selectPdfButton.isVisible = true
    }

    private fun closeRenderer() {
        try { renderer?.close() } catch (_: Exception) {}
        renderer = null
        try { fd?.close() } catch (_: Exception) {}
        fd = null
    }

    override fun onDestroyView() {
        closeRenderer()
        _binding = null
        super.onDestroyView()
    }
}
