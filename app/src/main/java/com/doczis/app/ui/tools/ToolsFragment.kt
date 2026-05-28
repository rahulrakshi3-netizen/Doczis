package com.doczis.app.ui.tools

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.doczis.app.R
import com.doczis.app.databinding.FragmentToolsBinding
import com.doczis.app.ui.navigation.Screen

class ToolsFragment : Fragment() {

    private var _binding: FragmentToolsBinding? = null
    private val binding get() = _binding!!

    private val tools = listOf(
        ToolListItem.Header(ToolCategory.CONVERT_CREATE),
        ToolListItem.Item(ToolItem("Image to PDF", R.drawable.ic_img_to_pdf, Screen.ImageToPdf, "Images to PDF")),
        ToolListItem.Item(ToolItem("Convert", R.drawable.ic_convert, Screen.Convert, "PDF ↔ Images")),
        ToolListItem.Item(ToolItem("OCR", R.drawable.ic_ocr, Screen.Ocr, "Images to text")),

        ToolListItem.Header(ToolCategory.ORGANIZE_PAGES),
        ToolListItem.Item(ToolItem("Reorder Pages", R.drawable.ic_reorder, Screen.ReorderPages, "Change page order")),
        ToolListItem.Item(ToolItem("Delete Pages", R.drawable.ic_delete, Screen.DeletePages, "Remove pages")),
        ToolListItem.Item(ToolItem("Split", R.drawable.ic_split, Screen.Split, "Extract pages")),
        ToolListItem.Item(ToolItem("Merge", R.drawable.ic_merge, Screen.Merge, "Combine PDFs")),

        ToolListItem.Header(ToolCategory.OPTIMIZE),
        ToolListItem.Item(ToolItem("Compress", R.drawable.ic_compress, Screen.Compress, "Reduce file size")),

        ToolListItem.Header(ToolCategory.SECURITY),
        ToolListItem.Item(ToolItem("Protect PDF", R.drawable.ic_lock, Screen.ProtectPdf, "Password protect")),
        ToolListItem.Item(ToolItem("Unprotect PDF", R.drawable.ic_unprotect, Screen.UnprotectPdf, "Remove password")),
        ToolListItem.Item(ToolItem("Watermark", R.drawable.ic_watermark, Screen.Watermark, "Add text overlay")),
        ToolListItem.Item(ToolItem("Sign PDF", R.drawable.ic_sign, Screen.SignPdf, "Add signature"))
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentToolsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolsRecyclerView.apply {
            val layoutManager = GridLayoutManager(requireContext(), 2)
            val adapter = ToolAdapter(tools) { tool ->
                openTool(tool.screen)
            }
            adapter.attachSpanSizeLookup(layoutManager)
            this.layoutManager = layoutManager
            this.adapter = adapter
        }
    }

    private fun openTool(screen: Screen) {
        val id = when (screen) {
            Screen.ImageToPdf -> R.id.imageToPdfFragment
            Screen.ReorderPages -> R.id.reorderPagesFragment
            Screen.DeletePages -> R.id.deletePagesFragment
            Screen.Compress -> R.id.compressFragment
            Screen.Convert -> R.id.convertFragment
            Screen.Split -> R.id.splitFragment
            Screen.Merge -> R.id.mergeFragment
            Screen.Ocr -> R.id.ocrFragment
            Screen.ProtectPdf -> R.id.protectPdfFragment
            Screen.UnprotectPdf -> R.id.unprotectPdfFragment
            Screen.Watermark -> R.id.watermarkFragment
            Screen.SignPdf -> R.id.signPdfFragment
            else -> return
        }
        findNavController().navigate(id)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
