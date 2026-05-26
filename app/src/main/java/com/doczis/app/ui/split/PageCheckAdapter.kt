package com.doczis.app.ui.split

import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.doczis.app.databinding.ItemConvertPageBinding

data class CheckablePage(
    val index: Int,
    val thumbnail: Bitmap,
    var isSelected: Boolean = true
)

class PageCheckAdapter(
    val pages: MutableList<CheckablePage>
) : RecyclerView.Adapter<PageCheckAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemConvertPageBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(pages[position])
    }

    override fun getItemCount() = pages.size

    inner class ViewHolder(private val binding: ItemConvertPageBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(page: CheckablePage) {
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
}
