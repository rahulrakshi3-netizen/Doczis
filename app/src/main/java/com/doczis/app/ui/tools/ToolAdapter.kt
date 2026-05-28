package com.doczis.app.ui.tools

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.doczis.app.databinding.ItemCategoryHeaderBinding
import com.doczis.app.databinding.ItemToolCardBinding

class ToolAdapter(
    private val items: List<ToolListItem>,
    private val onToolClick: (ToolItem) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_TOOL = 1
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is ToolListItem.Header -> TYPE_HEADER
            is ToolListItem.Item -> TYPE_TOOL
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_HEADER -> {
                val binding = ItemCategoryHeaderBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
                HeaderViewHolder(binding)
            }
            else -> {
                val binding = ItemToolCardBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
                ToolViewHolder(binding)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is ToolListItem.Header -> (holder as HeaderViewHolder).bind(item.category)
            is ToolListItem.Item -> (holder as ToolViewHolder).bind(item.tool)
        }
    }

    override fun getItemCount() = items.size

    fun attachSpanSizeLookup(layoutManager: GridLayoutManager) {
        layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                return when (items[position]) {
                    is ToolListItem.Header -> layoutManager.spanCount
                    is ToolListItem.Item -> 1
                }
            }
        }
    }

    inner class HeaderViewHolder(
        private val binding: ItemCategoryHeaderBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(category: ToolCategory) {
            binding.categoryTitle.text = category.displayName
        }
    }

    inner class ToolViewHolder(
        private val binding: ItemToolCardBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(tool: ToolItem) {
            binding.toolIcon.setImageResource(tool.iconRes)
            binding.toolName.text = tool.name
            binding.toolDescription.text = tool.description
            binding.root.setOnClickListener { onToolClick(tool) }
        }
    }
}
