package com.doczis.app.util

import android.content.Context
import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import androidx.core.content.FileProvider
import com.doczis.app.databinding.ViewPremiumSnackbarBinding
import com.doczis.app.ui.pdfviewer.PdfViewerActivity
import java.io.File

object PremiumSnackbar {

    private var currentSnackbar: ViewPremiumSnackbarBinding? = null
    private val interpolator = DecelerateInterpolator(1.5f)

    fun show(
        context: Context,
        parent: ViewGroup,
        title: String,
        subtitle: String = "",
        filePath: String? = null,
        autoDismissMs: Long = 3000L
    ) {
        dismiss()

        val binding = ViewPremiumSnackbarBinding.inflate(LayoutInflater.from(context), parent, false)
        parent.addView(binding.root)
        currentSnackbar = binding

        binding.snackbarTitle.text = title
        binding.snackbarSubtitle.text = subtitle

        if (filePath != null) {
            binding.snackbarAction.visibility = android.view.View.VISIBLE
            binding.snackbarAction.setOnClickListener {
                try {
                    val uri = if (filePath.startsWith("content://")) {
                        Uri.parse(filePath)
                    } else {
                        val file = File(filePath)
                        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                    }
                    val intent = android.content.Intent(context, PdfViewerActivity::class.java).apply {
                        data = uri
                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                } catch (_: Exception) {}
                dismiss()
            }
        } else {
            binding.snackbarAction.visibility = android.view.View.GONE
        }

        binding.root.alpha = 0f
        binding.root.translationY = 80f

        binding.root.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(400)
            .setInterpolator(interpolator)
            .start()

        binding.root.postDelayed({
            dismiss()
        }, autoDismissMs)
    }

    fun dismiss() {
        currentSnackbar?.let { binding ->
            binding.root.animate()
                .alpha(0f)
                .translationY(80f)
                .setDuration(300)
                .setInterpolator(interpolator)
                .withEndAction {
                    (binding.root.parent as? ViewGroup)?.removeView(binding.root)
                }
                .start()
        }
        currentSnackbar = null
    }
}
