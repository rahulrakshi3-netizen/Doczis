package com.doczis.app.ui.protectpdf

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
import com.doczis.app.databinding.FragmentProtectPdfBinding
import com.doczis.app.util.Debounce
import com.doczis.app.util.ErrorHandler
import com.doczis.app.util.FileSaveManager
import com.doczis.app.util.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import java.io.File
import java.io.FileOutputStream

class ProtectPdfFragment : Fragment() {

    private var _binding: FragmentProtectPdfBinding? = null
    private val binding get() = _binding!!
    private var sourceFile: File? = null
    private var originalSize = 0L
    private var suggestedFileName = "Doczis_Protected.pdf"

    private val filePicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { loadFile(it) }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProtectPdfBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.selectPdfButton.setOnClickListener {
            if (Debounce.isDuplicate()) return@setOnClickListener
            filePicker.launch(arrayOf("application/pdf"))
        }
        binding.protectButton.setOnClickListener {
            if (Debounce.isDuplicate()) return@setOnClickListener
            startProtect()
        }
    }

    private fun loadFile(uri: Uri) {
        lifecycleScope.launch {
            try {
                suggestedFileName = FileSaveManager.getFileName(requireContext(), uri, "Doczis_Protected.pdf")
                val dot = suggestedFileName.lastIndexOf('.')
                val base = if (dot > 0) suggestedFileName.substring(0, dot) else suggestedFileName
                suggestedFileName = "${base}_protected.pdf"

                binding.selectPdfButton.isEnabled = false
                binding.selectPdfButton.text = "Loading..."

                val file = withContext(Dispatchers.IO) {
                    val tempFile = File(requireContext().cacheDir, "protect_${System.nanoTime()}.tmp")
                    requireContext().contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(tempFile).use { output -> input.copyTo(output) }
                    }
                    tempFile
                }
                sourceFile = file
                originalSize = file.length()

                binding.fileInfo.text = "PDF — ${FileSaveManager.formatSize(originalSize)}"
                binding.fileInfo.isVisible = true
                binding.protectControls.isVisible = true
                binding.selectPdfButton.isVisible = false
            } catch (e: Exception) {
                ErrorHandler.logError("protectLoad", e)
                Toast.makeText(requireContext(), ErrorHandler.handleFileOpenError(requireContext(), e), Toast.LENGTH_LONG).show()
                resetUi()
            }
        }
    }

    private fun startProtect() {
        val password = binding.passwordInput.text?.toString()?.trim() ?: ""
        val confirm = binding.confirmPasswordInput.text?.toString()?.trim() ?: ""

        if (password.length < 4) {
            Toast.makeText(requireContext(), "Password must be at least 4 characters", Toast.LENGTH_SHORT).show()
            return
        }
        if (password != confirm) {
            Toast.makeText(requireContext(), "Passwords do not match", Toast.LENGTH_SHORT).show()
            return
        }

        binding.progressOverlay.isVisible = true
        binding.protectButton.isEnabled = false

        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    protectPdf(suggestedFileName, password)
                }
                (requireActivity().application as DoczisApp).fileRepository.insert(
                    FileEntity(
                        fileName = result.first,
                        filePath = result.second,
                        fileSize = result.third,
                        toolType = "protect"
                    )
                )
                NotificationHelper.showNotification(requireContext(), "Protected", "\u2713 ${result.first} saved — password set")
                Toast.makeText(requireContext(), "Saved: ${result.first} — password protected", Toast.LENGTH_LONG).show()
                findNavController().navigateUp()
            } catch (e: Exception) {
                ErrorHandler.logError("protectSave", e)
                Toast.makeText(requireContext(), ErrorHandler.handleSaveError(requireContext(), e), Toast.LENGTH_LONG).show()
            } finally {
                binding.progressOverlay.isVisible = false
                binding.protectButton.isEnabled = true
            }
        }
    }

    private fun protectPdf(fileName: String, password: String): Triple<String, String, Long> {
        val file = sourceFile ?: throw Exception("No file loaded")

        val doc = PDDocument.load(file)
        val permission = AccessPermission()
        permission.setCanModify(false)
        permission.setCanExtractContent(false)
        permission.setCanPrint(true)
        permission.setCanAssembleDocument(false)
        permission.setCanModifyAnnotations(false)
        permission.setCanFillInForm(false)

        val policy = StandardProtectionPolicy(password, password, permission)
        policy.encryptionKeyLength = 256
        policy.setPreferAES(true)
        doc.protect(policy)

        val tempFile = File(requireContext().cacheDir, "protect_out_${System.nanoTime()}.tmp")
        doc.save(tempFile)
        doc.close()

        val fileLen = tempFile.length()
        val uriResult = FileSaveManager.saveToDownloads(requireContext(), tempFile, fileName, "application/pdf")
        val uri = uriResult.getOrThrow()
        tempFile.delete()
        return Triple(fileName, uri.toString(), fileLen)
    }

    private fun resetUi() {
        binding.selectPdfButton.isEnabled = true
        binding.selectPdfButton.text = "Select PDF"
        binding.fileInfo.isVisible = false
        binding.protectControls.isVisible = false
        binding.selectPdfButton.isVisible = true
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
