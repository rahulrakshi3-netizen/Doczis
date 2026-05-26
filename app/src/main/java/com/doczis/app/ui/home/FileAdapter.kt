package com.doczis.app.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.doczis.app.data.db.FileEntity
import com.doczis.app.databinding.ItemRecentFileBinding

class FileAdapter(
    private val onItemClick: (FileEntity) -> Unit,
    private val onItemDelete: (FileEntity) -> Unit,
    private val onItemRename: (FileEntity) -> Unit
) : ListAdapter<FileEntity, FileAdapter.ListViewHolder>(DiffCallback()) {

    val selectedIds = mutableSetOf<Long>()
    var isSelectMode = false
        private set
    var onSelectionChanged: ((Int) -> Unit)? = null

    private val allItems: List<FileEntity>
        get() = currentList

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ListViewHolder {
        val binding = ItemRecentFileBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ListViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ListViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    fun toggleSelection(id: Long) {
        if (selectedIds.contains(id)) selectedIds.remove(id) else selectedIds.add(id)
        if (selectedIds.isEmpty()) exitSelectMode()
        notifyDataSetChanged()
        onSelectionChanged?.invoke(selectedIds.size)
    }

    fun enterSelectMode(id: Long) {
        isSelectMode = true
        selectedIds.add(id)
        notifyDataSetChanged()
        onSelectionChanged?.invoke(selectedIds.size)
    }

    fun exitSelectMode() {
        isSelectMode = false
        selectedIds.clear()
        notifyDataSetChanged()
        onSelectionChanged?.invoke(0)
    }

    fun getSelectedFiles(): List<FileEntity> = currentList.filter { selectedIds.contains(it.id) }

    fun selectAll() {
        selectedIds.addAll(currentList.map { it.id })
        notifyDataSetChanged()
        onSelectionChanged?.invoke(selectedIds.size)
    }

    inner class ListViewHolder(
        private val binding: ItemRecentFileBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(file: FileEntity) {
            val isSelected = selectedIds.contains(file.id)
            binding.fileName.text = file.fileName
            binding.fileSize.text = formatSize(file.fileSize)
            binding.toolType.text = file.toolType

            binding.selectionCheckbox.visibility = if (isSelectMode) View.VISIBLE else View.GONE
            binding.deleteButton.visibility = if (isSelectMode) View.GONE else View.VISIBLE
            binding.selectionCheckbox.isChecked = isSelected
            binding.root.isActivated = isSelected

            binding.root.setOnClickListener {
                if (isSelectMode) {
                    toggleSelection(file.id)
                } else {
                    onItemClick(file)
                }
            }
            binding.root.setOnLongClickListener {
                if (!isSelectMode) {
                    enterSelectMode(file.id)
                } else {
                    toggleSelection(file.id)
                }
                true
            }
            binding.deleteButton.setOnClickListener { onItemDelete(file) }
        }
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<FileEntity>() {
        override fun areItemsTheSame(old: FileEntity, new: FileEntity) = old.id == new.id
        override fun areContentsTheSame(old: FileEntity, new: FileEntity) = old == new
    }
}
