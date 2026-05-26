package com.doczis.app.ui.convert

import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.doczis.app.databinding.ItemConvertPageBinding

data class ConvertPage(
    val index: Int,
    val thumbnail: Bitmap,
    var isSelected: Boolean = true
)

class ConvertPageAdapter : ListAdapter<ConvertPage, ConvertPageAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemConvertPageBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemConvertPageBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(page: ConvertPage) {
            binding.pageThumbnail.setImageBitmap(page.thumbnail)
            binding.pageCheckbox.isChecked = page.isSelected
            binding.pageNumber.text = "Page ${page.index + 1}"
            binding.root.setOnClickListener {
                page.isSelected = !page.isSelected
                binding.pageCheckbox.isChecked = page.isSelected
            }
            binding.pageCheckbox.setOnClickListener {
                page.isSelected = binding.pageCheckbox.isChecked
            }
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<ConvertPage>() {
        override fun areItemsTheSame(old: ConvertPage, new: ConvertPage) = old.index == new.index
        override fun areContentsTheSame(old: ConvertPage, new: ConvertPage) = old.index == new.index && old.isSelected == new.isSelected
    }
}
