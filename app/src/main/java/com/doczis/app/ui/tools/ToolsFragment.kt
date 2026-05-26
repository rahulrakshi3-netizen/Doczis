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
        ToolItem("Image to PDF", R.drawable.ic_img_to_pdf, Screen.ImageToPdf, "Images to PDF"),
        ToolItem("Reorder Pages", R.drawable.ic_reorder, Screen.ReorderPages, "Change page order"),
        ToolItem("Delete Pages", R.drawable.ic_delete, Screen.DeletePages, "Remove pages"),
        ToolItem("Compress", R.drawable.ic_compress, Screen.Compress, "Reduce file size"),
        ToolItem("Convert", R.drawable.ic_convert, Screen.Convert, "PDF ↔ Images"),
        ToolItem("Split", R.drawable.ic_split, Screen.Split, "Extract pages"),
        ToolItem("Merge", R.drawable.ic_merge, Screen.Merge, "Combine PDFs"),
        ToolItem("OCR", R.drawable.ic_ocr, Screen.Ocr, "Images to text")
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
            layoutManager = GridLayoutManager(requireContext(), 2)
            adapter = ToolAdapter(tools) { tool ->
                openTool(tool.screen)
            }
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
            else -> return
        }
        findNavController().navigate(id)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
