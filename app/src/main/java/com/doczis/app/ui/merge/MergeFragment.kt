package com.doczis.app.ui.merge

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
import com.doczis.app.databinding.FragmentMergeBinding
import com.doczis.app.util.Debounce
import com.doczis.app.util.ErrorHandler
import com.doczis.app.util.FileSaveManager
import com.doczis.app.util.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class MergeFragment : Fragment() {

    private var _binding: FragmentMergeBinding? = null
    private val binding get() = _binding!!
    private val sourceFiles = mutableListOf<MergeFile>()
    private var adapter: MergeFileAdapter? = null

    private val filePicker = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        for (uri in uris) loadPdf(uri)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMergeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.addFab.setOnClickListener {
            if (Debounce.isDuplicate(it)) return@setOnClickListener
            filePicker.launch(arrayOf("application/pdf"))
        }
        binding.mergeButton.setOnClickListener {
            if (Debounce.isDuplicate(it)) return@setOnClickListener
            startMerge()
        }

        adapter = MergeFileAdapter(sourceFiles) { index ->
            sourceFiles.removeAt(index)
            adapter?.notifyDataSetChanged()
            updateUi()
        }
        binding.filesRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = adapter
        }
        updateUi()
    }

    private fun loadPdf(uri: Uri) {
        lifecycleScope.launch {
            try {
                val (file, pageCount) = withContext(Dispatchers.IO) {
                    val temp = File(requireContext().cacheDir, "merge_${System.nanoTime()}_${sourceFiles.size}.pdf")
                    requireContext().contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(temp).use { output -> input.copyTo(output) }
                    }
                    val pfd = android.os.ParcelFileDescriptor.open(temp, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
                    val renderer = PdfRenderer(pfd)
                    val count = renderer.pageCount
                    renderer.close()
                    pfd.close()
                    Pair(temp, count)
                }
                val name = FileSaveManager.getFileName(requireContext(), uri, "document.pdf")
                sourceFiles.add(MergeFile(file, name, file.length(), pageCount))
                adapter?.notifyItemInserted(sourceFiles.size - 1)
                updateUi()
            } catch (e: Exception) {
                ErrorHandler.logError("mergeLoad", e)
                Toast.makeText(requireContext(), "Error loading file: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun updateUi() {
        val hasFiles = sourceFiles.isNotEmpty()
        binding.mergeButton.isVisible = hasFiles
        binding.emptyHint.isVisible = !hasFiles
        binding.filesRecyclerView.isVisible = hasFiles
    }

    private fun startMerge() {
        if (sourceFiles.size < 2) {
            Toast.makeText(requireContext(), "Add at least 2 PDFs to merge", Toast.LENGTH_SHORT).show()
            return
        }

        val input = EditText(requireContext()).apply { setText("merged_document.pdf") }
        AlertDialog.Builder(requireContext()).apply {
            setTitle("Save merged PDF as")
            setView(input)
            setPositiveButton("Save") { _, _ ->
                var customName = input.text.toString().trim()
                if (customName.isBlank()) customName = "merged_document.pdf"
                if (!customName.endsWith(".pdf", true)) customName += ".pdf"
                doMerge(customName)
            }
            setNegativeButton("Cancel", null)
            show()
        }
    }

    private fun doMerge(fileName: String) {
        binding.progressOverlay.isVisible = true
        binding.mergeButton.isEnabled = false

        lifecycleScope.launch {
            try {
                val outputDoc = PdfDocument()
                var totalPages = 0

                withContext(Dispatchers.IO) {
                    for ((idx, mergeFile) in sourceFiles.withIndex()) {
                        val pfd = android.os.ParcelFileDescriptor.open(mergeFile.file, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
                        val renderer = PdfRenderer(pfd)
                        try {
                            for (p in 0 until renderer.pageCount) {
                                val srcPage = renderer.openPage(p)
                                val w = srcPage.width
                                val h = srcPage.height
                                val bitmap = try {
                                    Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                                } catch (e: OutOfMemoryError) {
                                    Bitmap.createBitmap(w / 2, h / 2, Bitmap.Config.ARGB_8888)
                                }
                                srcPage.render(bitmap, null, null, 1)
                                srcPage.close()

                                val pageInfo = PdfDocument.PageInfo.Builder(w, h, outputDoc.pages.size + 1).create()
                                val page = outputDoc.startPage(pageInfo)
                                page.canvas.drawBitmap(bitmap, 0f, 0f, null)
                                bitmap.recycle()
                                outputDoc.finishPage(page)
                                totalPages++
                            }
                        } finally {
                            renderer.close()
                            pfd.close()
                        }

                        withContext(Dispatchers.Main) {
                            binding.progressText.text = "Processing file ${idx + 1} of ${sourceFiles.size}"
                        }
                    }
                }

                if (outputDoc.pages.isEmpty()) throw Exception("No pages to merge")
                val uri = withContext(Dispatchers.IO) {
                    val tempFile = File(requireContext().cacheDir, "merge_out_${System.nanoTime()}.pdf")
                    FileOutputStream(tempFile).use { outputDoc.writeTo(it) }
                    outputDoc.close()
                    val u = FileSaveManager.saveToDownloads(requireContext(), tempFile, fileName, "application/pdf", "Doczis/Merge")
                    tempFile.delete()
                    u.getOrThrow()
                }

                val app = requireActivity().application as DoczisApp
                app.fileRepository.insert(
                    FileEntity(
                        fileName = fileName,
                        filePath = uri.toString(),
                        fileSize = -1L,
                        toolType = "merge"
                    )
                )
                NotificationHelper.showFileSavedNotification(requireContext(), "Merged", fileName, uri.toString())
                Toast.makeText(requireContext(), "Saved to Downloads/Doczis/Merge/", Toast.LENGTH_LONG).show()
                findNavController().navigateUp()
            } catch (e: OutOfMemoryError) {
                ErrorHandler.logError("mergeSave", e)
                Toast.makeText(requireContext(), "Out of memory. Try merging fewer or smaller files.", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                ErrorHandler.logError("mergeSave", e)
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                binding.progressOverlay.isVisible = false
                binding.mergeButton.isEnabled = true
            }
        }
    }

    override fun onDestroyView() {
        for (mf in sourceFiles) try { mf.file.delete() } catch (_: Exception) {}
        _binding = null
        super.onDestroyView()
    }
}
