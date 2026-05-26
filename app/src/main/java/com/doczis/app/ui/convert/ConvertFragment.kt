package com.doczis.app.ui.convert

import android.graphics.Bitmap
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
import com.doczis.app.databinding.FragmentConvertBinding
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

class ConvertFragment : Fragment() {

    private var _binding: FragmentConvertBinding? = null
    private val binding get() = _binding!!
    private var renderer: PdfRenderer? = null
    private var fd: android.os.ParcelFileDescriptor? = null
    private var sourceFile: File? = null
    private var pageCount = 0
    private var adapter: ConvertPageAdapter? = null
    private var pdfName = "PDF"

    private val filePicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { loadPdf(it) }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentConvertBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.selectPdfButton.setOnClickListener {
            if (Debounce.isDuplicate()) return@setOnClickListener
            filePicker.launch(arrayOf("application/pdf"))
        }
        binding.selectAllButton.setOnClickListener {
            adapter?.currentList?.forEach { it.isSelected = true }
            adapter?.notifyItemRangeChanged(0, adapter?.itemCount ?: 0)
        }
        binding.deselectAllButton.setOnClickListener {
            adapter?.currentList?.forEach { it.isSelected = false }
            adapter?.notifyItemRangeChanged(0, adapter?.itemCount ?: 0)
        }
        binding.convertButton.setOnClickListener {
            if (Debounce.isDuplicate()) return@setOnClickListener
            startConvert()
        }
    }

    private fun loadPdf(uri: Uri) {
        lifecycleScope.launch {
            try {
                pdfName = FileSaveManager.getFileName(requireContext(), uri, "PDF")
                binding.selectPdfButton.isEnabled = false
                binding.selectPdfButton.text = "Loading..."

                val file = withContext(Dispatchers.IO) {
                    val temp = File(requireContext().cacheDir, "convert_${System.nanoTime()}.pdf")
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
                    val list = mutableListOf<ConvertPage>()
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
                        list.add(ConvertPage(i, bitmap))
                    }
                    Pair(c, list)
                }
                pageCount = count

                MemoryMonitor.logMemory(requireContext(), "convertLoad")
                binding.fileInfo.text = "$pageCount pages — ${FileSaveManager.formatSize(file.length())}"
                binding.fileInfo.isVisible = true
                binding.convertControls.isVisible = true
                binding.convertButton.isVisible = true
                binding.selectPdfButton.isVisible = false

                adapter = ConvertPageAdapter()
                adapter?.submitList(pages)
                binding.pagesRecyclerView.apply {
                    layoutManager = GridLayoutManager(requireContext(), 2)
                    adapter = this@ConvertFragment.adapter
                }
            } catch (e: Exception) {
                ErrorHandler.logError("convertLoad", e)
                Toast.makeText(requireContext(), ErrorHandler.handleFileOpenError(requireContext(), e), Toast.LENGTH_LONG).show()
                resetUi()
            }
        }
    }

    private fun startConvert() {
        val pages = adapter?.currentList?.filter { it.isSelected } ?: run {
            Toast.makeText(requireContext(), "No pages selected", Toast.LENGTH_SHORT).show()
            return
        }
        if (pages.isEmpty()) {
            Toast.makeText(requireContext(), "No pages selected", Toast.LENGTH_SHORT).show()
            return
        }

        val isPng = binding.chipPng.isChecked
        val ext = if (isPng) "png" else "jpg"
        val mime = if (isPng) "image/png" else "image/jpeg"

        binding.progressOverlay.isVisible = true
        binding.convertButton.isEnabled = false

        lifecycleScope.launch {
            try {
                var savedCount = 0
                val r = renderer ?: throw Exception("PDF not loaded")
                val pdfBase = pdfName.substringBeforeLast('.')

                withContext(Dispatchers.IO) {
                    for (page in pages) {
                        val imgName = "${pdfBase}_page_${page.index + 1}.$ext"
                        val srcPage = r.openPage(page.index)
                        val w = 1080
                        val h = (srcPage.height * w.toFloat() / srcPage.width).toInt()
                        val bitmap = try {
                            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        } catch (e: OutOfMemoryError) {
                            Bitmap.createBitmap(w / 2, h / 2, Bitmap.Config.ARGB_8888)
                        }
                        srcPage.render(bitmap, null, null, 1)
                        srcPage.close()

                        val s = ByteArrayOutputStream()
                        if (isPng) bitmap.compress(Bitmap.CompressFormat.PNG, 100, s)
                        else bitmap.compress(Bitmap.CompressFormat.JPEG, 90, s)
                        bitmap.recycle()
                        val bytes = s.toByteArray()

                        FileSaveManager.saveBytesToDownloads(requireContext(), bytes, imgName, mime, "Doczis/Convert")
                        savedCount++

                        withContext(Dispatchers.Main) {
                            binding.progressText.text = "Saved $savedCount of ${pages.size}"
                        }
                    }
                }

                NotificationHelper.showFileSavedNotification(requireContext(), "Convert", "${pdfBase}: $savedCount images", null, mime)
                Toast.makeText(requireContext(), "Saved $savedCount images to Downloads/Doczis/Convert/", Toast.LENGTH_LONG).show()
                findNavController().navigateUp()
            } catch (e: OutOfMemoryError) {
                ErrorHandler.logError("convertSave", e)
                Toast.makeText(requireContext(), "Out of memory. Try converting fewer pages.", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                ErrorHandler.logError("convertSave", e)
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                binding.progressOverlay.isVisible = false
                binding.convertButton.isEnabled = true
            }
        }
    }

    private fun resetUi() {
        binding.selectPdfButton.isEnabled = true
        binding.selectPdfButton.text = "Select PDF"
        binding.fileInfo.isVisible = false
        binding.convertControls.isVisible = false
        binding.convertButton.isVisible = false
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
