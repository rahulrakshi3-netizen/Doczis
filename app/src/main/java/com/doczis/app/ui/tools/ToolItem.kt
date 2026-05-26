package com.doczis.app.ui.tools

import androidx.annotation.DrawableRes
import com.doczis.app.ui.navigation.Screen

data class ToolItem(
    val name: String,
    @DrawableRes val iconRes: Int,
    val screen: Screen,
    val description: String
)
