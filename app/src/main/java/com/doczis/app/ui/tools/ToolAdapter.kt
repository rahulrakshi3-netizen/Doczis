package com.doczis.app.ui.tools

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.doczis.app.databinding.ItemToolCardBinding

class ToolAdapter(
    private val tools: List<ToolItem>,
    private val onToolClick: (ToolItem) -> Unit
) : RecyclerView.Adapter<ToolAdapter.ToolViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ToolViewHolder {
        val binding = ItemToolCardBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ToolViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ToolViewHolder, position: Int) {
        holder.bind(tools[position])
    }

    override fun getItemCount() = tools.size

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
