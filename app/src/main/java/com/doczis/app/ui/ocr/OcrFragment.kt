package com.doczis.app.ui.ocr

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.BitmapFactory
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
import com.doczis.app.databinding.FragmentOcrBinding
import com.doczis.app.util.Debounce
import com.doczis.app.util.ErrorHandler
import com.doczis.app.util.FileSaveManager
import com.doczis.app.util.NotificationHelper
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class OcrFragment : Fragment() {

    private var _binding: FragmentOcrBinding? = null
    private val binding get() = _binding!!
    private var sourceFile: File? = null
    private var imgName = "image"
    private var recognizedText = ""

    private val imagePicker = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { loadImage(it) }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOcrBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.selectImageButton.setOnClickListener {
            if (Debounce.isDuplicate()) return@setOnClickListener
            imagePicker.launch("image/*")
        }
        binding.copyButton.setOnClickListener {
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("OCR", recognizedText))
            Toast.makeText(requireContext(), "Copied", Toast.LENGTH_SHORT).show()
        }
        binding.saveButton.setOnClickListener {
            if (Debounce.isDuplicate()) return@setOnClickListener
            saveText()
        }
    }

    private fun loadImage(uri: Uri) {
        lifecycleScope.launch {
            try {
                imgName = FileSaveManager.getFileName(requireContext(), uri, "image")
                binding.selectImageButton.isEnabled = false
                binding.selectImageButton.text = "Loading..."

                val bitmap = withContext(Dispatchers.IO) {
                    val temp = File(requireContext().cacheDir, "ocr_${System.nanoTime()}.tmp")
                    requireContext().contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(temp).use { output -> input.copyTo(output) }
                    }
                    sourceFile = temp
                    BitmapFactory.decodeFile(temp.absolutePath)
                } ?: throw Exception("Cannot decode image")

                binding.fileInfo.text = "Image — ${FileSaveManager.formatSize(sourceFile?.length() ?: 0)}"
                binding.fileInfo.isVisible = true
                binding.progressOverlay.isVisible = true

                val inputImage = InputImage.fromBitmap(bitmap, 0)
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

                recognizer.process(inputImage)
                    .addOnSuccessListener { result ->
                        recognizedText = result.text.trim()
                        if (recognizedText.isBlank()) recognizedText = "(no text found)"
                        binding.recognizedText.text = recognizedText
                        binding.resultScroll.isVisible = true
                        binding.actionButtons.isVisible = true
                        binding.progressOverlay.isVisible = false
                    }
                    .addOnFailureListener { e ->
                        ErrorHandler.logError("ocrProcess", e)
                        Toast.makeText(requireContext(), "OCR failed: ${e.message}", Toast.LENGTH_LONG).show()
                        binding.progressOverlay.isVisible = false
                    }
            } catch (e: Exception) {
                ErrorHandler.logError("ocrLoad", e)
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_LONG).show()
                resetUi()
            }
        }
    }

    private fun saveText() {
        if (recognizedText.isBlank() || recognizedText == "(no text found)") {
            Toast.makeText(requireContext(), "No text to save", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                val txtName = "${imgName.substringBeforeLast('.')}_ocr.txt"
                val bytes = recognizedText.toByteArray()

                val uri = withContext(Dispatchers.IO) {
                    FileSaveManager.saveBytesToDownloads(requireContext(), bytes, txtName, "text/plain", "Doczis/OCR").getOrThrow()
                }

                val app = requireActivity().application as DoczisApp
                app.fileRepository.insert(
                    FileEntity(
                        fileName = txtName,
                        filePath = uri.toString(),
                        fileSize = bytes.size.toLong(),
                        toolType = "ocr"
                    )
                )
                NotificationHelper.showFileSavedNotification(requireContext(), "OCR Text Saved", txtName, uri.toString())
                Toast.makeText(requireContext(), "Saved to Downloads/Doczis/OCR/", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                ErrorHandler.logError("ocrSave", e)
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun resetUi() {
        binding.selectImageButton.isEnabled = true
        binding.selectImageButton.text = "Select Image"
        binding.fileInfo.isVisible = false
        binding.resultScroll.isVisible = false
        binding.actionButtons.isVisible = false
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
