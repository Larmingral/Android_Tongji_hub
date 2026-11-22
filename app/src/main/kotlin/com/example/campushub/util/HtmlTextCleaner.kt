package com.example.campushub.util

/**
 * Pure-Kotlin HTML-to-readable-text converter shared by Canvas descriptions/announcements
 * and native HTML email rendering.
 */
object HtmlTextCleaner {

  private val scriptStyleRegex =
    Regex("<(script|style)[^>]*>[\\s\\S]*?</\\1>", RegexOption.IGNORE_CASE)
  private val brRegex = Regex("<br\\s*/?>", RegexOption.IGNORE_CASE)
  private val blockEndRegex =
    Regex("</(p|div|li|tr|h[1-6]|blockquote)>", RegexOption.IGNORE_CASE)
  private val liStartRegex = Regex("<li[^>]*>", RegexOption.IGNORE_CASE)
  private val anyTagRegex = Regex("<[^>]+>")
  private val multiNewlinesRegex = Regex("\\n{3,}")
  private val trailingSpacesRegex = Regex("[ \\t]+\\n")

  fun toReadableText(rawHtml: String?): String {
    if (rawHtml.isNullOrBlank()) return ""
    var text = scriptStyleRegex.replace(rawHtml, "")
    text = brRegex.replace(text, "\n")
    text = liStartRegex.replace(text, "\n• ")
    text = blockEndRegex.replace(text, "\n")
    text = anyTagRegex.replace(text, "")
    text = decodeBasicEntities(text)
    text = text.replace("\r\n", "\n").replace("\r", "\n")
    text = trailingSpacesRegex.replace(text, "\n")
    text = multiNewlinesRegex.replace(text, "\n\n")
    return text.trim()
  }

  fun formatFileSize(bytes: Long): String {
    if (bytes <= 0L) return "未知大小"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return String.format("%.1f KB", kb.coerceAtLeast(0.1))
    val mb = kb / 1024.0
    return String.format("%.2f MB", mb)
  }

  private fun decodeBasicEntities(input: String): String {
    return input
      .replace("&nbsp;", " ")
      .replace("&amp;", "&")
      .replace("&lt;", "<")
      .replace("&gt;", ">")
      .replace("&quot;", "\"")
      .replace("&#39;", "'")
      .replace("&apos;", "'")
  }
}
