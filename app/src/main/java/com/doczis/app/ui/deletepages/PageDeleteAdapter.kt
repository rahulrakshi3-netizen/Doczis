package com.doczis.app.ui.deletepages

import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.doczis.app.databinding.ItemDeletePageBinding

class PageDeleteAdapter(
    private val pages: MutableList<DeletePageData>
) : RecyclerView.Adapter<PageDeleteAdapter.PageViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
        val binding = ItemDeletePageBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return PageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
        holder.bind(pages[position], position)
    }

    override fun getItemCount() = pages.size

    fun getRemainingPages(): List<DeletePageData> {
        return pages.filter { !it.checked }
    }

    inner class PageViewHolder(
        private val binding: ItemDeletePageBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(data: DeletePageData, index: Int) {
            binding.pageThumbnail.setImageBitmap(data.bitmap)
            binding.pageNumber.text = "Page ${index + 1}"
            binding.pageCheckbox.isChecked = data.checked
            binding.pageCheckbox.setOnCheckedChangeListener { _, isChecked ->
                data.checked = isChecked
            }
            binding.root.setOnClickListener {
                val newChecked = !data.checked
                data.checked = newChecked
                binding.pageCheckbox.isChecked = newChecked
            }
        }
    }
}

data class DeletePageData(
    val bitmap: Bitmap,
    val originalIndex: Int,
    val origWidth: Int,
    val origHeight: Int,
    var checked: Boolean = false
)
