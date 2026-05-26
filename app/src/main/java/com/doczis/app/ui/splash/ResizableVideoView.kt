package com.doczis.app.ui.splash

import android.content.Context
import android.util.AttributeSet
import android.widget.VideoView

class ResizableVideoView(context: Context, attrs: AttributeSet? = null) : VideoView(context, attrs) {

    fun setRoundCorners(radius: Float) {
        outlineProvider = object : android.view.ViewOutlineProvider() {
            override fun getOutline(view: android.view.View, outline: android.graphics.Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, radius)
            }
        }
        clipToOutline = true
    }
}
