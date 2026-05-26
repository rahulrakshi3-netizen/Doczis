package com.doczis.app.ui.merge

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.doczis.app.databinding.ItemMergeFileBinding
import java.io.File

data class MergeFile(val file: File, val name: String, val size: Long, val pageCount: Int = 0)

class MergeFileAdapter(
    private val files: MutableList<MergeFile>,
    private val onRemove: (Int) -> Unit
) : RecyclerView.Adapter<MergeFileAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemMergeFileBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(files[position], position)
    }

    override fun getItemCount() = files.size

    inner class ViewHolder(private val binding: ItemMergeFileBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: MergeFile, index: Int) {
            binding.orderNumber.text = "${index + 1}"
            binding.fileName.text = item.name
            binding.fileSize.text = formatSize(item.size)
            binding.pageCount.text = if (item.pageCount > 0) "${item.pageCount} pages" else "PDF"
            binding.removeButton.setOnClickListener { onRemove(index) }
        }
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
        }
    }
}
