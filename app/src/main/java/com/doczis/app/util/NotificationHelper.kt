package com.doczis.app.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.FileProvider
import com.doczis.app.R
import com.doczis.app.ui.pdfviewer.PdfViewerActivity
import java.io.File

object NotificationHelper {

    private const val CHANNEL_ID = "doczis_file_operations"
    private const val CHANNEL_NAME = "DOCZIS File Operations"
    private const val GROUP_KEY = "com.doczis.app.FILE_OPERATIONS"
    private var notificationId = 1000

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "File operation results"
                setShowBadge(true)
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    fun showNotification(
        context: Context,
        title: String,
        message: String,
        filePath: String? = null,
        mimeType: String = "application/pdf"
    ) {
        val intent = buildFileIntent(context, filePath, mimeType)
        val pendingIntent = if (intent != null) {
            PendingIntent.getActivity(
                context,
                System.currentTimeMillis().toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else null

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_img_to_pdf)
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(true)
            .setGroup(GROUP_KEY)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        try {
            val nm = NotificationManagerCompat.from(context)
            nm.notify(notificationId++, notification)
        } catch (e: SecurityException) {
            android.util.Log.w("NotificationHelper", "No notification permission", e)
        }
    }

    fun showFileSavedNotification(
        context: Context,
        operation: String,
        fileName: String,
        filePath: String?,
        mimeType: String = "application/pdf"
    ) {
        showNotification(
            context = context,
            title = "$operation — Complete",
            message = "\u2713 $fileName saved successfully",
            filePath = filePath,
            mimeType = mimeType
        )
    }

    private fun buildFileIntent(context: Context, filePath: String?, mimeType: String): Intent? {
        if (filePath == null) return null
        return try {
            val uri = if (filePath.startsWith("content://")) {
                Uri.parse(filePath)
            } else {
                val file = File(filePath)
                if (file.exists()) {
                    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                } else return null
            }
            val isImage = mimeType.startsWith("image/")
            if (isImage) {
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, mimeType)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            } else {
                Intent(context, PdfViewerActivity::class.java).apply {
                    data = uri
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
        } catch (e: Exception) {
            null
        }
    }
}
