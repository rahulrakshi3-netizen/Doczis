package com.doczis.app.util

import android.content.ContentValues
import android.content.Context
import android.graphics.pdf.PdfDocument
import java.io.FileNotFoundException
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

object FileSaveManager {

    fun saveToDownloads(
        context: Context,
        sourceFile: File,
        fileName: String,
        mimeType: String = "application/pdf",
        subFolder: String = "Doczis"
    ): Result<Uri> = runCatching {
        val resolver = context.contentResolver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, mimeType)
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$subFolder")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                ?: throw IllegalStateException("Failed to create file in Downloads")
            resolver.openOutputStream(uri)?.use { out ->
                sourceFile.inputStream().use { it.copyTo(out) }
            } ?: throw IllegalStateException("Failed to write PDF")
            contentValues.clear()
            contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, contentValues, null, null)
            uri
        } else {
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                subFolder
            )
            dir.mkdirs()
            val dest = File(dir, fileName)
            sourceFile.copyTo(dest, overwrite = true)
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", dest)
        }
    }

    fun saveBytesToDownloads(
        context: Context,
        data: ByteArray,
        fileName: String,
        mimeType: String,
        subFolder: String = "Doczis"
    ): Result<Uri> = runCatching {
        val resolver = context.contentResolver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, mimeType)
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$subFolder")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                ?: throw IllegalStateException("Failed to create file")
            resolver.openOutputStream(uri)?.use { it.write(data) }
                ?: throw IllegalStateException("Failed to write")
            contentValues.clear()
            contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, contentValues, null, null)
            uri
        } else {
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                subFolder
            )
            dir.mkdirs()
            val dest = File(dir, fileName)
            dest.writeBytes(data)
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", dest)
        }
    }

    fun savePdfDocumentToDownloads(
        context: Context,
        document: PdfDocument,
        fileName: String,
        subFolder: String = "Doczis"
    ): Result<Uri> {
        val tempFile = File(context.cacheDir, "pdf_temp_${System.nanoTime()}.pdf")
        return try {
            FileOutputStream(tempFile).use { document.writeTo(it) }
            document.close()
            val uri = saveToDownloads(context, tempFile, fileName, "application/pdf", subFolder).getOrThrow()
            Result.success(uri)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            if (tempFile.exists()) tempFile.delete()
        }
    }

    fun copyUriToCache(context: Context, uri: Uri): Result<File> = runCatching {
        val tempFile = File(context.cacheDir, "cache_${System.nanoTime()}.tmp")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(tempFile).use { output -> input.copyTo(output) }
        } ?: throw FileNotFoundException("Cannot read URI: $uri")
        tempFile
    }

    fun getFileName(context: Context, uri: Uri, default: String = "file"): String {
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                val nameIdx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIdx >= 0 && it.moveToFirst()) return it.getString(nameIdx)
            }
        } else if (uri.scheme == "file") {
            return File(uri.path ?: "").name
        }
        return default
    }

    fun validateFile(file: File): Boolean {
        return file.exists() && file.length() > 0 && file.canRead()
    }

    fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
        }
    }
}
