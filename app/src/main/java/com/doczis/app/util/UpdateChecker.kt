package com.doczis.app.util

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

object UpdateChecker {

    private const val GITHUB_API = "https://api.github.com/repos/rahulrakshi3-netizen/Doczis/releases/latest"
    private const val APK_NAME = "app-debug.apk"

    data class ReleaseInfo(
        val tagName: String,
        val downloadUrl: String
    )

    fun check(context: Context, lifecycleOwner: LifecycleOwner) {
        lifecycleOwner.lifecycleScope.launch {
            val release = withContext(Dispatchers.IO) { fetchLatestRelease() }
            if (release == null) return@launch

            val currentVer = currentVersionName(context) ?: return@launch
            val latestVer = release.tagName.removePrefix("v")

            if (isNewer(latestVer, currentVer)) {
                showUpdateDialog(context, release)
            }
        }
    }

    private fun fetchLatestRelease(): ReleaseInfo? = runCatching {
        val conn = URL(GITHUB_API).openConnection() as HttpURLConnection
        conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        val json = conn.inputStream.bufferedReader().readText()
        val obj = JSONObject(json)
        val tag = obj.getString("tag_name")
        val assets = obj.getJSONArray("assets")
        var dlUrl = ""
        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            if (asset.getString("name") == APK_NAME) {
                dlUrl = asset.getString("browser_download_url")
                break
            }
        }
        if (dlUrl.isBlank()) return null
        ReleaseInfo(tag, dlUrl)
    }.getOrNull()

    private fun currentVersionName(context: Context): String? = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull()

    private fun isNewer(latest: String, current: String): Boolean {
        val lParts = latest.split(".").mapNotNull { it.toIntOrNull() }
        val cParts = current.split(".").mapNotNull { it.toIntOrNull() }
        for (i in 0 until maxOf(lParts.size, cParts.size)) {
            val l = lParts.getOrElse(i) { 0 }
            val c = cParts.getOrElse(i) { 0 }
            if (l != c) return l > c
        }
        return false
    }

    private fun showUpdateDialog(context: Context, release: ReleaseInfo) {
        if (context !is Activity || context.isFinishing) return
        AlertDialog.Builder(context)
            .setTitle("Update Available")
            .setMessage("DOCZIS ${release.tagName} is available.\nDownload and install now?")
            .setPositiveButton("Update Now") { _, _ ->
                downloadAndInstall(context, release)
            }
            .setNegativeButton("Later", null)
            .show()
    }

    private fun downloadAndInstall(context: Context, release: ReleaseInfo) {
        val lifecycleOwner = context as? LifecycleOwner ?: return
        lifecycleOwner.lifecycleScope.launch {
            try {
                Toast.makeText(context, "Downloading...", Toast.LENGTH_SHORT).show()
                val apkFile = withContext(Dispatchers.IO) {
                    downloadApk(context, release.downloadUrl)
                }
                installApk(context, apkFile)
            } catch (e: Exception) {
                ErrorHandler.logError("updateDl", e)
                Toast.makeText(context, "Update failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun downloadApk(context: Context, url: String): File {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 15000
        conn.connect()
        val inputStream = conn.inputStream
        val apkFile = File(context.cacheDir, APK_NAME)
        FileOutputStream(apkFile).use { output ->
            inputStream.copyTo(output)
        }
        return apkFile
    }

    private fun installApk(context: Context, apkFile: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
