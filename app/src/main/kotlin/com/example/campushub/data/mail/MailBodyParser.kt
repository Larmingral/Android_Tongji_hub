package com.example.campushub.data.mail

import com.example.campushub.util.HtmlTextCleaner

sealed interface MailImageSource {
  data class Cid(val contentId: String) : MailImageSource

  data class Base64Data(val mimeType: String, val base64: String) : MailImageSource

  data class RemoteUrl(val url: String) : MailImageSource
}

sealed interface MailBodyBlock {
  data class TextBlock(val text: String) : MailBodyBlock

  data class ImageBlock(val source: MailImageSource, val altText: String = "") : MailBodyBlock
}

/**
 * Splits an HTML email body into ordered text and inline-image blocks without requiring a WebView.
 */
object MailBodyParser {

  private val imgTagRegex = Regex("<img\\b[^>]*>", RegexOption.IGNORE_CASE)
  private val attrRegex =
    Regex(
      """([a-zA-Z0-9_-]+)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+))""",
      RegexOption.IGNORE_CASE,
    )

  fun normalizeCid(raw: String): String =
    raw
      .trim()
      .removePrefix("<")
      .removeSuffix(">")
      .trim()
      .replace(Regex("^cid:", RegexOption.IGNORE_CASE), "")
      .trim()
      .removePrefix("<")
      .removeSuffix(">")
      .trim()
      .lowercase()

  fun parseHtml(
    html: String,
    inlineCids: Set<String> = emptySet(),
  ): List<MailBodyBlock> {
    if (html.isBlank()) return emptyList()

    val blocks = mutableListOf<MailBodyBlock>()
    val referencedCids = mutableSetOf<String>()
    var cursor = 0

    for (match in imgTagRegex.findAll(html)) {
      if (match.range.first > cursor) {
        val textSlice = HtmlTextCleaner.toReadableText(html.substring(cursor, match.range.first))
        if (textSlice.isNotBlank()) {
          blocks.add(MailBodyBlock.TextBlock(textSlice))
        }
      }

      val attrs = parseAttributes(match.value)
      val src = attrs["src"].orEmpty().trim()
      val alt = attrs["alt"].orEmpty().trim()
      val imageSource = classifyImageSource(src)
      if (imageSource != null) {
        if (imageSource is MailImageSource.Cid) {
          referencedCids.add(imageSource.contentId)
        }
        blocks.add(MailBodyBlock.ImageBlock(source = imageSource, altText = alt))
      } else if (alt.isNotBlank()) {
        blocks.add(MailBodyBlock.TextBlock("[图片: $alt]"))
      }

      cursor = match.range.last + 1
    }

    if (cursor < html.length) {
      val tailText = HtmlTextCleaner.toReadableText(html.substring(cursor))
      if (tailText.isNotBlank()) {
        blocks.add(MailBodyBlock.TextBlock(tailText))
      }
    }

    // Append any MIME inline images that were not explicitly referenced via cid: in HTML.
    for (cid in inlineCids) {
      if (cid !in referencedCids) {
        blocks.add(MailBodyBlock.ImageBlock(source = MailImageSource.Cid(cid)))
      }
    }

    return blocks
  }

  private fun parseAttributes(tag: String): Map<String, String> {
    val map = HashMap<String, String>()
    for (m in attrRegex.findAll(tag)) {
      val key = m.groupValues[1].lowercase()
      val value = m.groupValues[2].ifEmpty { m.groupValues[3].ifEmpty { m.groupValues[4] } }
      map[key] = value
    }
    return map
  }

  fun classifyImageSource(src: String): MailImageSource? {
    if (src.isBlank()) return null
    val lower = src.lowercase()
    return when {
      lower.startsWith("cid:") -> {
        val cid = normalizeCid(src)
        if (cid.isNotBlank()) MailImageSource.Cid(cid) else null
      }
      lower.startsWith("data:image/") && lower.contains(";base64,") -> {
        val commaIdx = src.indexOf(',')
        if (commaIdx <= 0 || commaIdx >= src.lastIndex) return null
        val header = src.substring(5, commaIdx) // after "data:"
        val mime = header.substringBefore(';').lowercase()
        val base64Payload = src.substring(commaIdx + 1).replace("\\s+".toRegex(), "")
        if (base64Payload.isNotBlank()) MailImageSource.Base64Data(mime, base64Payload) else null
      }
      lower.startsWith("https://") || lower.startsWith("http://") -> {
        MailImageSource.RemoteUrl(src)
      }
      else -> null
    }
  }
}
