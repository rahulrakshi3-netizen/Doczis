package com.doczis.app.ui.reorderpages

import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.doczis.app.databinding.ItemReorderPageBinding

class PageReorderAdapter(
    private val pages: MutableList<PageData>,
    private val onDragStart: (RecyclerView.ViewHolder) -> Unit
) : RecyclerView.Adapter<PageReorderAdapter.PageViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
        val binding = ItemReorderPageBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return PageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
        holder.bind(pages[position], position)
    }

    override fun getItemCount() = pages.size

    fun move(from: Int, to: Int) {
        val item = pages.removeAt(from)
        pages.add(to, item)
        notifyItemMoved(from, to)
    }

    inner class PageViewHolder(
        private val binding: ItemReorderPageBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(data: PageData, index: Int) {
            binding.pageThumbnail.setImageBitmap(data.bitmap)
            binding.pageNumber.text = "Page ${index + 1}"
            binding.dragHandle.setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    onDragStart(this)
                }
                false
            }
        }
    }
}

data class PageData(
    val bitmap: Bitmap,
    val originalIndex: Int,
    val origWidth: Int,
    val origHeight: Int
)
