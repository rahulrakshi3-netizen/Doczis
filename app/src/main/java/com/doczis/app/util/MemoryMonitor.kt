package com.doczis.app.util

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Debug

object MemoryMonitor {

    private var lastLogTime = 0L
    private const val LOG_INTERVAL_MS = 5000L

    fun getHeapInfo(context: Context): String {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return "N/A"
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        val heapFree = Debug.getNativeHeapFreeSize() / (1024 * 1024)
        val heapAlloc = Debug.getNativeHeapAllocatedSize() / (1024 * 1024)
        val heapSize = Debug.getNativeHeapSize() / (1024 * 1024)
        val availMem = mi.availMem / (1024 * 1024)
        return "Heap: ${heapAlloc}M/${heapSize}M (free: ${heapFree}M) | Avail: ${availMem}M"
    }

    fun isLowMemory(context: Context): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return false
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        return mi.lowMemory
    }

    fun logMemory(context: Context, tag: String) {
        val now = System.currentTimeMillis()
        if (now - lastLogTime < LOG_INTERVAL_MS) return
        lastLogTime = now
        ErrorHandler.log("Memory[$tag]", getHeapInfo(context))
    }

    fun canAllocateBitmap(width: Int, height: Int, config: Bitmap.Config?): Boolean {
        val bytesPerPixel = when (config) {
            android.graphics.Bitmap.Config.ARGB_8888 -> 4
            android.graphics.Bitmap.Config.RGB_565 -> 2
            android.graphics.Bitmap.Config.ALPHA_8 -> 1
            else -> 4
        }
        val requiredBytes = width.toLong() * height * bytesPerPixel
        val maxMemory = Runtime.getRuntime().maxMemory()
        val usedMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
        val available = maxMemory - usedMemory
        return requiredBytes < available * 0.8
    }
}
