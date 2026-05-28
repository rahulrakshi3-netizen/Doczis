package com.doczis.app.util

import android.view.View

object Debounce {
    private val lastClickTimes = mutableMapOf<Int, Long>()
    private const val DEBOUNCE_MS = 400L

    fun isDuplicate(view: View): Boolean {
        val now = System.currentTimeMillis()
        val id = view.id
        val last = lastClickTimes[id] ?: 0L
        if (now - last < DEBOUNCE_MS) return true
        lastClickTimes[id] = now
        return false
    }

    fun isDuplicate(): Boolean {
        val now = System.currentTimeMillis()
        val last = lastClickTimes[0] ?: 0L
        if (now - last < DEBOUNCE_MS) return true
        lastClickTimes[0] = now
        return false
    }

    fun reset() {
        lastClickTimes.clear()
    }
}
