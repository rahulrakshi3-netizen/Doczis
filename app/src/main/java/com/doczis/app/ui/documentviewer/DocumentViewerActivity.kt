package com.doczis.app.ui.documentviewer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.doczis.app.databinding.ActivityDocumentViewerBinding
import com.doczis.app.util.DocumentTextExtractor
import com.doczis.app.util.ErrorHandler
import com.doczis.app.util.FileSaveManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class DocumentViewerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDocumentViewerBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDocumentViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }

        val uri = intent.data ?: run {
            Toast.makeText(this, "No file specified", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        loadDocument(uri)
    }

    private fun loadDocument(uri: Uri) {
        binding.loadingBar.isVisible = true
        binding.toolbar.title = FileSaveManager.getFileName(this, uri, "Document")

        lifecycleScope.launch {
            var tempFile: File? = null
            try {
                tempFile = withContext(Dispatchers.IO) {
                    val file = File(cacheDir, "doc_${System.nanoTime()}.tmp")
                    contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(file).use { output -> input.copyTo(output) }
                    }
                    file
                }

                val fileSize = tempFile?.length() ?: 0
                val maxFileSize = 50L * 1024 * 1024
                if (fileSize > maxFileSize) {
                    binding.loadingBar.isVisible = false
                    Toast.makeText(this@DocumentViewerActivity, "File too large for text preview", Toast.LENGTH_LONG).show()
                    offerOpenWith(uri)
                    finish()
                    return@launch
                }

                val text = withContext(Dispatchers.IO) {
                    DocumentTextExtractor.extractText(tempFile!!)
                }

                binding.documentContent.text = text
                binding.loadingBar.isVisible = false
            } catch (e: Exception) {
                ErrorHandler.logError("documentLoad", e)
                binding.loadingBar.isVisible = false
                binding.documentContent.text = "Error loading document"
                Toast.makeText(this@DocumentViewerActivity, ErrorHandler.handleFileOpenError(this@DocumentViewerActivity, e), Toast.LENGTH_LONG).show()
                offerOpenWith(uri)
            } finally {
                tempFile?.delete()
            }
        }
    }

    private fun offerOpenWith(uri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(Intent.createChooser(intent, "Open with"))
        } catch (_: Exception) {}
    }
}
