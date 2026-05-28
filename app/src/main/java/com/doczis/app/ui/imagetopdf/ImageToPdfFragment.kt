package com.doczis.app.ui.imagetopdf

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.doczis.app.DoczisApp
import com.doczis.app.data.db.FileEntity
import com.doczis.app.databinding.FragmentImageToPdfBinding
import com.doczis.app.util.Debounce
import com.doczis.app.util.ErrorHandler
import com.doczis.app.util.FileSaveManager
import com.doczis.app.util.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ImageToPdfFragment : Fragment() {

    private var _binding: FragmentImageToPdfBinding? = null
    private val binding get() = _binding!!
    private var selectedImages = mutableListOf<Uri>()

    private val imagePicker = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        selectedImages.clear()
        selectedImages.addAll(uris)
        binding.imageCount.text = "${selectedImages.size} image(s) selected"
        binding.createButton.isEnabled = selectedImages.isNotEmpty()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentImageToPdfBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.pickButton.setOnClickListener {
            if (Debounce.isDuplicate(it)) return@setOnClickListener
            imagePicker.launch("image/*")
        }
        binding.createButton.setOnClickListener {
            if (Debounce.isDuplicate(it)) return@setOnClickListener
            if (selectedImages.isNotEmpty()) createPdf()
        }
    }

    private fun createPdf() {
        val input = EditText(requireContext()).apply {
            setText("Doczis_Images.pdf")
            setSelection(0, 12)
        }
        AlertDialog.Builder(requireContext()).apply {
            setTitle("Save as")
            setView(input)
            setPositiveButton("Create") { _, _ ->
                var customName = input.text.toString().trim()
                if (customName.isBlank()) customName = "Doczis_Images.pdf"
                if (!customName.endsWith(".pdf", true)) customName += ".pdf"

                binding.createButton.isEnabled = false
                binding.progressBar.visibility = View.VISIBLE

                lifecycleScope.launch {
                    try {
                        val result = withContext(Dispatchers.IO) { generatePdf(selectedImages, customName) }
                        val app = requireActivity().application as DoczisApp
                        app.fileRepository.insert(
                            FileEntity(
                                fileName = result.first,
                                filePath = result.second,
                                fileSize = result.third,
                                toolType = "image_to_pdf"
                            )
                        )
                        NotificationHelper.showFileSavedNotification(requireContext(), "PDF Created", result.first, result.second)
                        Toast.makeText(requireContext(), "PDF saved: ${result.first}", Toast.LENGTH_LONG).show()
                        findNavController().navigateUp()
                    } catch (e: Exception) {
                        ErrorHandler.logError("imageToPdf", e)
                        Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_LONG).show()
                    } finally {
                        binding.createButton.isEnabled = true
                        binding.progressBar.visibility = View.GONE
                    }
                }
            }
            setNegativeButton("Cancel", null)
            show()
        }
    }

    private fun generatePdf(uris: List<Uri>, fileName: String): Triple<String, String, Long> {
        val document = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842

        try {
            for ((idx, uri) in uris.withIndex()) {
                val inputStream = requireContext().contentResolver.openInputStream(uri) ?: continue
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeStream(inputStream, null, options)
                inputStream.close()
                if (options.outWidth <= 0 || options.outHeight <= 0) continue

                var inSampleSize = 1
                var scaledW = options.outWidth
                var scaledH = options.outHeight
                val maxDim = 4096
                while (scaledW > maxDim || scaledH > maxDim) {
                    inSampleSize *= 2
                    scaledW = options.outWidth / inSampleSize
                    scaledH = options.outHeight / inSampleSize
                }

                val decodeStream = requireContext().contentResolver.openInputStream(uri)
                val bitmapOptions = BitmapFactory.Options().apply {
                    this.inSampleSize = inSampleSize
                    this.inPreferredConfig = Bitmap.Config.RGB_565
                }
                val bitmap = BitmapFactory.decodeStream(decodeStream, null, bitmapOptions)
                decodeStream?.close()
                if (bitmap == null) continue

                val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, idx + 1).create()
                val page = document.startPage(pageInfo)
                val canvas = page.canvas
                val scale = minOf(pageWidth.toFloat() / bitmap.width, pageHeight.toFloat() / bitmap.height)
                val drawW = (bitmap.width * scale).toInt()
                val drawH = (bitmap.height * scale).toInt()
                val left = (pageWidth - drawW) / 2
                val top = (pageHeight - drawH) / 2
                val scaledBitmap = Bitmap.createScaledBitmap(bitmap, drawW, drawH, true)
                canvas.drawBitmap(scaledBitmap, left.toFloat(), top.toFloat(), null)
                scaledBitmap.recycle()
                bitmap.recycle()
                document.finishPage(page)
            }

            if (document.pages.isEmpty()) throw Exception("No valid images could be processed")
            val uri = FileSaveManager.savePdfDocumentToDownloads(requireContext(), document, fileName)
            val fileUri = uri.getOrThrow()
            val fileSize = try {
                val cursor = requireContext().contentResolver.query(fileUri, null, null, null, null)
                cursor?.use {
                    val sizeIdx = it.getColumnIndex(android.provider.OpenableColumns.SIZE)
                    if (sizeIdx >= 0 && it.moveToFirst()) it.getLong(sizeIdx) else -1L
                } ?: -1L
            } catch (_: Exception) { -1L }
            return Triple(fileName, fileUri.toString(), fileSize)
        } finally {
            if (document.pages.isNotEmpty()) document.close()
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
