package com.doczis.app.util

import android.content.Context
import android.widget.Toast
import androidx.annotation.StringRes
import com.doczis.app.R

object ErrorHandler {

    private const val TAG = "DoczisError"

    fun handleFileOpenError(context: Context, error: Throwable): String {
        val message = when (error) {
            is OutOfMemoryError -> "File too large to open. Try a smaller file."
            is java.io.FileNotFoundException -> "File not found. It may have been moved or deleted."
            is SecurityException -> "Permission denied. Cannot access this file."
            is IllegalStateException -> "File is corrupted or in an invalid format."
            is java.io.IOException -> "Error reading file. Please try again."
            is IllegalArgumentException -> "Invalid file format. Please select a valid PDF."
            else -> {
                val msg = error.message
                if (msg.isNullOrBlank()) "An unexpected error occurred." else msg
            }
        }
        android.util.Log.e(TAG, "File open error: ${error.message}", error)
        return message
    }

    fun handleRendererError(context: Context, error: Throwable): String {
        val message = when (error) {
            is OutOfMemoryError -> "Not enough memory to render this PDF."
            is IllegalStateException -> "This PDF may be corrupted or password-protected."
            is SecurityException -> "Cannot access this document."
            else -> "Failed to render PDF: ${error.message ?: "Unknown error"}"
        }
        android.util.Log.e(TAG, "Renderer error: ${error.message}", error)
        return message
    }

    fun handleSaveError(context: Context, error: Throwable): String {
        val message = when (error) {
            is java.io.FileNotFoundException -> "Could not save file. Storage may be full."
            is SecurityException -> "Permission denied. Cannot save to this location."
            is OutOfMemoryError -> "Not enough memory to complete this operation."
            else -> "Save failed: ${error.message ?: "Unknown error"}"
        }
        android.util.Log.e(TAG, "Save error: ${error.message}", error)
        return message
    }

    fun showError(context: Context, message: String) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    fun showError(context: Context, @StringRes resId: Int) {
        Toast.makeText(context, resId, Toast.LENGTH_LONG).show()
    }

    fun log(operation: String, detail: String = "") {
        android.util.Log.d(TAG, "[$operation] $detail")
    }

    fun logError(operation: String, error: Throwable) {
        android.util.Log.e(TAG, "[$operation] Error: ${error.message}", error)
    }
}
