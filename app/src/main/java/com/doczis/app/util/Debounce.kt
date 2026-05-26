package com.doczis.app.util

import kotlinx.coroutines.Job
import kotlinx.coroutines.delay

object Debounce {
    private var lastClickTime = 0L
    private const val DEBOUNCE_MS = 800L

    fun isDuplicate(): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastClickTime < DEBOUNCE_MS) return true
        lastClickTime = now
        return false
    }

    fun reset() {
        lastClickTime = 0L
    }
}
