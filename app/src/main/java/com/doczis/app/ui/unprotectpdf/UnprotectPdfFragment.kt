package com.doczis.app.ui.unprotectpdf

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
import com.doczis.app.databinding.FragmentUnprotectPdfBinding
import com.doczis.app.util.Debounce
import com.doczis.app.util.ErrorHandler
import com.doczis.app.util.FileSaveManager
import com.doczis.app.util.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.tom_roush.pdfbox.pdmodel.PDDocument
import java.io.File
import java.io.FileOutputStream

class UnprotectPdfFragment : Fragment() {

    private var _binding: FragmentUnprotectPdfBinding? = null
    private val binding get() = _binding!!
    private var sourceFile: File? = null
    private var originalSize = 0L
    private var suggestedFileName = "Doczis_Unprotected.pdf"

    private val filePicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { loadFile(it) }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentUnprotectPdfBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.selectPdfButton.setOnClickListener {
            if (Debounce.isDuplicate()) return@setOnClickListener
            filePicker.launch(arrayOf("application/pdf"))
        }
        binding.unprotectButton.setOnClickListener {
            if (Debounce.isDuplicate()) return@setOnClickListener
            startUnprotect()
        }
    }

    private fun loadFile(uri: Uri) {
        lifecycleScope.launch {
            try {
                suggestedFileName = FileSaveManager.getFileName(requireContext(), uri, "Doczis_Unprotected.pdf")
                val dot = suggestedFileName.lastIndexOf('.')
                val base = if (dot > 0) suggestedFileName.substring(0, dot) else suggestedFileName
                suggestedFileName = "${base}_unprotected.pdf"

                binding.selectPdfButton.isEnabled = false
                binding.selectPdfButton.text = "Loading..."

                val file = withContext(Dispatchers.IO) {
                    val tempFile = File(requireContext().cacheDir, "unprotect_${System.nanoTime()}.tmp")
                    requireContext().contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(tempFile).use { output -> input.copyTo(output) }
                    }
                    tempFile
                }
                sourceFile = file
                originalSize = file.length()

                binding.fileInfo.text = "${FileSaveManager.formatSize(originalSize)}"
                binding.fileInfo.isVisible = true
                binding.unprotectControls.isVisible = true
                binding.selectPdfButton.isVisible = false
            } catch (e: Exception) {
                ErrorHandler.logError("unprotectLoad", e)
                Toast.makeText(requireContext(), ErrorHandler.handleFileOpenError(requireContext(), e), Toast.LENGTH_LONG).show()
                resetUi()
            }
        }
    }

    private fun startUnprotect() {
        val password = binding.passwordInput.text?.toString()?.trim() ?: ""

        if (password.isBlank()) {
            Toast.makeText(requireContext(), "Enter the password", Toast.LENGTH_SHORT).show()
            return
        }

        binding.progressOverlay.isVisible = true
        binding.unprotectButton.isEnabled = false

        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    unprotectPdf(suggestedFileName, password)
                }
                val app = requireActivity().application as DoczisApp
                app.fileRepository.insert(
                    FileEntity(
                        fileName = result.first,
                        filePath = result.second,
                        fileSize = result.third,
                        toolType = "unprotect"
                    )
                )
                NotificationHelper.showFileSavedNotification(requireContext(), "Unprotected", result.first, result.second)
                Toast.makeText(requireContext(), "Saved: ${result.first} — password removed", Toast.LENGTH_LONG).show()
                findNavController().navigateUp()
            } catch (e: Exception) {
                ErrorHandler.logError("unprotectSave", e)
                val msg = when {
                    e.message?.contains("Invalid password", true) == true || e.message?.contains("password", true) == true -> "Incorrect password"
                    e.message?.contains("Encryption", true) == true -> "This PDF is not password-protected"
                    else -> ErrorHandler.handleSaveError(requireContext(), e)
                }
                Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
            } finally {
                binding.progressOverlay.isVisible = false
                binding.unprotectButton.isEnabled = true
            }
        }
    }

    private fun unprotectPdf(fileName: String, password: String): Triple<String, String, Long> {
        val file = sourceFile ?: throw Exception("No file loaded")

        val doc = PDDocument.load(file, password)
        doc.setAllSecurityToBeRemoved(true)

        val tempFile = File(requireContext().cacheDir, "unprotect_out_${System.nanoTime()}.pdf")
        doc.save(tempFile)
        doc.close()

        val fileLen = tempFile.length()
        val uriResult = FileSaveManager.saveToDownloads(requireContext(), tempFile, fileName)
        val uri = uriResult.getOrThrow()
        tempFile.delete()
        return Triple(fileName, uri.toString(), fileLen)
    }

    private fun resetUi() {
        binding.selectPdfButton.isEnabled = true
        binding.selectPdfButton.text = "Select PDF"
        binding.fileInfo.isVisible = false
        binding.unprotectControls.isVisible = false
        binding.selectPdfButton.isVisible = true
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
