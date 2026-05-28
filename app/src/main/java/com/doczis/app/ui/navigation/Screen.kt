package com.doczis.app.ui.navigation

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Tools : Screen("tools")
    data object Settings : Screen("settings")

    data object ImageToPdf : Screen("image_to_pdf")
    data object ReorderPages : Screen("reorder_pages")
    data object DeletePages : Screen("delete_pages")
    data object Compress : Screen("compress")
    data object Convert : Screen("convert")
    data object Split : Screen("split")
    data object Merge : Screen("merge")
    data object Ocr : Screen("ocr")
    data object PdfViewer : Screen("pdf_viewer")

    data object ProtectPdf : Screen("protect_pdf")
    data object UnprotectPdf : Screen("unprotect_pdf")
    data object Watermark : Screen("watermark")
    data object SignPdf : Screen("sign_pdf")
}
