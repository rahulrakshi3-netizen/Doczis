package com.doczis.app.ui.home

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.doczis.app.R
import com.doczis.app.databinding.FragmentHomeBinding
import com.doczis.app.ui.documentviewer.DocumentViewerActivity
import com.doczis.app.ui.pdfviewer.PdfViewerActivity
import com.doczis.app.util.FileSaveManager
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: HomeViewModel
    private lateinit var adapter: FileAdapter

    private val openDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { openFile(it) }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(findNavController().getBackStackEntry(R.id.homeFragment))[HomeViewModel::class.java]

        adapter = FileAdapter(
            onItemClick = { file -> launchViewer(file) },
            onItemDelete = { file -> deleteFileDirect(file) },
            onItemRename = { file -> showRenameDialog(file) }
        )
        adapter.onSelectionChanged = { count -> updateSelectionBar(count) }

        binding.recentFilesRecyclerView.apply {
            adapter = this@HomeFragment.adapter
            layoutManager = LinearLayoutManager(requireContext())
        }

        binding.openFab.setOnClickListener {
            openDocumentLauncher.launch(arrayOf(
                "application/pdf",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            ))
        }

        binding.deleteSelectedButton.setOnClickListener { deleteSelected() }
        binding.renameSelectedButton.setOnClickListener { renameSelected() }
        binding.cancelSelectionButton.setOnClickListener { adapter.exitSelectMode() }
        binding.selectAllButton.setOnClickListener { adapter.selectAll() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.displayFiles.collectLatest { files ->
                binding.emptyState.isVisible = files.isEmpty()
                binding.recentFilesRecyclerView.isVisible = files.isNotEmpty()
                adapter.submitList(files)
            }
        }
    }

    private fun updateSelectionBar(count: Int) {
        if (count > 0) {
            binding.selectionBar.isVisible = true
            binding.openFab.hide()
            binding.selectionCount.text = "$count selected"
        } else {
            binding.selectionBar.isVisible = false
            binding.openFab.show()
        }
    }

    private fun deleteSelected() {
        val selected = adapter.getSelectedFiles()
        for (file in selected) {
            viewModel.deleteFile(file)
            lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    try {
                        if (file.filePath.startsWith("content://")) {
                            val uri = Uri.parse(file.filePath)
                            requireContext().contentResolver.delete(uri, null, null)
                        } else {
                            File(file.filePath).delete()
                        }
                    } catch (_: Exception) {}
                }
            }
        }
        adapter.exitSelectMode()
        Toast.makeText(requireContext(), "${selected.size} deleted", Toast.LENGTH_SHORT).show()
    }

    private fun renameSelected() {
        val selected = adapter.getSelectedFiles()
        if (selected.isEmpty()) return
        showRenameDialog(selected.first())
    }

    private fun launchViewer(file: com.doczis.app.data.db.FileEntity) {
        val uri = uriFromPath(file.filePath) ?: return
        val name = file.fileName.lowercase()
        val cls = when {
            name.endsWith(".pdf") -> PdfViewerActivity::class.java
            name.endsWith(".docx") || name.endsWith(".pptx") -> DocumentViewerActivity::class.java
            else -> PdfViewerActivity::class.java
        }
        val intent = Intent(requireContext(), cls).apply {
            data = uri
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(intent)
    }

    private fun openFile(uri: Uri) {
        val name = uri.lastPathSegment?.lowercase() ?: ""
        val cls = when {
            name.endsWith(".pdf") -> PdfViewerActivity::class.java
            name.endsWith(".docx") || name.endsWith(".pptx") -> DocumentViewerActivity::class.java
            else -> PdfViewerActivity::class.java
        }
        val intent = Intent(requireContext(), cls).apply {
            data = uri
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(intent)
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val doczisDir = File(requireContext().cacheDir, "recent")
                doczisDir.mkdirs()
                val hash = uri.toString().hashCode().toLong()
                val tempFile = File(doczisDir, "recent_$hash.pdf")
                withContext(Dispatchers.IO) {
                    requireContext().contentResolver.openInputStream(uri)?.use { input ->
                        tempFile.outputStream().use { output -> input.copyTo(output) }
                    }
                }
                viewModel.saveRecentFile(
                    fileName = FileSaveManager.getFileName(requireContext(), uri, "document.pdf"),
                    filePath = tempFile.absolutePath,
                    fileSize = tempFile.length()
                )
            } catch (_: Exception) {}
        }
    }

    private fun uriFromPath(path: String): Uri? {
        return if (path.startsWith("content://")) {
            Uri.parse(path)
        } else {
            val file = File(path)
            if (!file.exists()) {
                Toast.makeText(requireContext(), "File not found", Toast.LENGTH_SHORT).show()
                return null
            }
            FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.fileprovider", file)
        }
    }

    private fun deleteFileDirect(file: com.doczis.app.data.db.FileEntity) {
        viewModel.deleteFile(file)
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    if (file.filePath.startsWith("content://")) {
                        val uri = Uri.parse(file.filePath)
                        requireContext().contentResolver.delete(uri, null, null)
                    } else {
                        File(file.filePath).delete()
                    }
                } catch (_: Exception) {}
            }
        }
        Toast.makeText(requireContext(), "${file.fileName} deleted", Toast.LENGTH_SHORT).show()
    }

    private fun showRenameDialog(file: com.doczis.app.data.db.FileEntity) {
        val input = EditText(requireContext()).apply {
            setText(file.fileName)
            val dot = file.fileName.lastIndexOf('.')
            if (dot > 0) setSelection(0, dot)
        }
        AlertDialog.Builder(requireContext()).apply {
            setTitle("Rename")
            setView(input)
            setPositiveButton("Rename") { _, _ ->
                var newName = input.text.toString().trim()
                if (newName.isBlank()) return@setPositiveButton
                val ext = file.fileName.substringAfterLast('.', "pdf")
                if (!newName.endsWith(".$ext", true)) newName += ".$ext"
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        try {
                            val newPath: String
                            if (file.filePath.startsWith("content://")) {
                                val uri = Uri.parse(file.filePath)
                                val values = android.content.ContentValues().apply {
                                    put(android.provider.OpenableColumns.DISPLAY_NAME, newName)
                                }
                                requireContext().contentResolver.update(uri, values, null, null)
                                newPath = file.filePath
                            } else {
                                val oldFile = File(file.filePath)
                                val newFile = File(oldFile.parent, newName)
                                if (oldFile.exists()) oldFile.renameTo(newFile)
                                newPath = newFile.absolutePath
                            }
                            viewModel.renameFile(file, newName, newPath)
                        } catch (_: Exception) {}
                    }
                }
                adapter.exitSelectMode()
            }
            setNegativeButton("Cancel", null)
            show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
