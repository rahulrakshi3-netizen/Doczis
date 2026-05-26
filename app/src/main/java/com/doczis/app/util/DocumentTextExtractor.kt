package com.doczis.app.util

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.util.zip.ZipFile

object DocumentTextExtractor {

    fun extractText(file: File): String {
        return when {
            file.name.endsWith(".docx", true) -> extractDocx(file)
            file.name.endsWith(".pptx", true) -> extractPptx(file)
            else -> "Unsupported file format"
        }
    }

    private fun extractDocx(file: File): String {
        try {
            ZipFile(file).use { zip ->
                val entry = zip.getEntry("word/document.xml") ?: return "No document content found"
                val parser = Xml.newPullParser()
                parser.setInput(zip.getInputStream(entry), "UTF-8")
                val sb = StringBuilder()
                var eventType = parser.eventType
                while (eventType != XmlPullParser.END_DOCUMENT) {
                    if (eventType == XmlPullParser.START_TAG && parser.name == "w:t") {
                        parser.next()
                        if (parser.eventType == XmlPullParser.TEXT) {
                            sb.append(parser.text)
                        }
                    }
                    if (eventType == XmlPullParser.START_TAG && parser.name == "w:p") {
                        if (sb.isNotEmpty() && sb.last() != '\n') sb.append('\n')
                    }
                    eventType = parser.next()
                }
                return sb.toString().trim()
            }
        } catch (e: Exception) {
            return "Error reading document: ${e.message}"
        }
    }

    private fun extractPptx(file: File): String {
        try {
            ZipFile(file).use { zip ->
                val entries = zip.entries()
                val sb = StringBuilder()
                var slideNum = 0
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.name.startsWith("ppt/slides/slide") && entry.name.endsWith(".xml")) {
                        slideNum++
                        sb.append("\n--- Slide $slideNum ---\n")
                        val parser = Xml.newPullParser()
                        parser.setInput(zip.getInputStream(entry), "UTF-8")
                        var eventType = parser.eventType
                        while (eventType != XmlPullParser.END_DOCUMENT) {
                            if (eventType == XmlPullParser.START_TAG && parser.name == "a:t") {
                                parser.next()
                                if (parser.eventType == XmlPullParser.TEXT) {
                                    sb.append(parser.text)
                                }
                            }
                            if (eventType == XmlPullParser.START_TAG && parser.name == "a:p") {
                                if (sb.isNotEmpty() && sb.last() != '\n') sb.append('\n')
                            }
                            eventType = parser.next()
                        }
                    }
                }
                return sb.toString().trim()
            }
        } catch (e: Exception) {
            return "Error reading presentation: ${e.message}"
        }
    }
}
