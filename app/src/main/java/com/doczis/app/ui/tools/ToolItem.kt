package com.doczis.app.ui.tools

import androidx.annotation.DrawableRes
import com.doczis.app.ui.navigation.Screen

enum class ToolCategory(val displayName: String) {
    CONVERT_CREATE("Convert & Create"),
    ORGANIZE_PAGES("Organize Pages"),
    OPTIMIZE("Optimize"),
    SECURITY("Security")
}

sealed class ToolListItem {
    data class Header(val category: ToolCategory) : ToolListItem()
    data class Item(val tool: ToolItem) : ToolListItem()
}

data class ToolItem(
    val name: String,
    @DrawableRes val iconRes: Int,
    val screen: Screen,
    val description: String
)
