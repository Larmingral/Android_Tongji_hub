package com.example.campushub.data.mail

import com.example.campushub.util.FileOpener
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Properties
import javax.mail.Address
import javax.mail.AuthenticationFailedException
import javax.mail.FetchProfile
import javax.mail.Flags
import javax.mail.Folder
import javax.mail.Message
import javax.mail.Multipart
import javax.mail.Part
import javax.mail.Session
import javax.mail.UIDFolder
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeUtility
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ImapMailClient {

  private val listDateFormatter = DateTimeFormatter.ofPattern("MM/dd HH:mm")
  private val detailDateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

  suspend fun fetchRecentMessages(config: MailConfig, limit: Int = 30): Result<List<MailHeader>> =
    withContext(Dispatchers.IO) {
      val cleanEmail = config.email.trim()
      val cleanPwd = config.appPassword.trim()
      if (cleanEmail.isEmpty() || cleanPwd.isEmpty()) {
        return@withContext Result.failure(IllegalArgumentException("请填写完整邮箱和客户端专用密码"))
      }

      runCatching {
        withInbox(config, Folder.READ_ONLY) { folder ->
          val total = folder.messageCount
          if (total <= 0) return@withInbox emptyList()

          val start = (total - limit + 1).coerceAtLeast(1)
          val messages = folder.getMessages(start, total)
          val uidFolder = folder as? UIDFolder

          val profile =
            FetchProfile().apply {
              add(FetchProfile.Item.ENVELOPE)
              add(FetchProfile.Item.FLAGS)
              add(FetchProfile.Item.CONTENT_INFO)
              if (uidFolder != null) add(UIDFolder.FetchProfileItem.UID)
            }
          folder.fetch(messages, profile)

          messages.reversed().mapIndexed { index, msg ->
            val uid = uidFolder?.getUID(msg) ?: (total - index).toLong()
            val sent = msg.sentDate ?: msg.receivedDate ?: Date()
            val (localDate, dateLabel) = formatDate(sent, listDateFormatter)
            MailHeader(
              uid = uid,
              subject = decodeSubject(msg.subject),
              fromDisplay = formatAddresses(msg.from),
              sentDateText = dateLabel,
              sentDate = localDate,
              isUnread = !msg.isSet(Flags.Flag.SEEN),
              hasAttachments = hasLikelyAttachment(msg),
            )
          }
        }
      }.recoverCatching { throw mapMailException(it) }
    }

  suspend fun fetchMessageDetail(
    config: MailConfig,
    uid: Long,
    inlineImageDir: File,
  ): Result<MailDetail> =
    withContext(Dispatchers.IO) {
      runCatching {
        withInbox(config, Folder.READ_WRITE) { folder ->
          val uidFolder = folder as? UIDFolder ?: error("服务器不支持 UID 访问")
          val msg = uidFolder.getMessageByUID(uid) ?: error("找不到对应邮件（UID=$uid）")
          msg.setFlag(Flags.Flag.SEEN, true)

          val accumulator = MimeContentAccumulator(uid = uid, inlineImageDir = inlineImageDir)
          walkPart(msg, accumulator, captureInlineBytes = true)

          val htmlCombined = accumulator.htmlTexts.joinToString("\n").trim()
          val bodyBlocks =
            if (htmlCombined.isNotEmpty()) {
              MailBodyParser.parseHtml(htmlCombined, accumulator.inlineImageFiles.keys)
            } else {
              buildList {
                val plain = accumulator.plainTexts.joinToString("\n\n").trim()
                if (plain.isNotEmpty()) add(MailBodyBlock.TextBlock(plain))
                for (cid in accumulator.inlineImageFiles.keys) {
                  add(MailBodyBlock.ImageBlock(MailImageSource.Cid(cid)))
                }
              }
            }.ifEmpty {
              listOf(MailBodyBlock.TextBlock("（该邮件无文本正文内容）"))
            }

          val plainSummary =
            bodyBlocks
              .filterIsInstance<MailBodyBlock.TextBlock>()
              .joinToString("\n\n") { it.text }
              .trim()
              .ifBlank { "（该邮件主要包含图片内容）" }

          val sent = msg.sentDate ?: msg.receivedDate ?: Date()
          val (localDate, dateLabel) = formatDate(sent, detailDateFormatter)

          MailDetail(
            uid = uid,
            subject = decodeSubject(msg.subject),
            fromDisplay = formatAddresses(msg.from),
            toDisplay = formatAddresses(msg.getRecipients(Message.RecipientType.TO)),
            sentDateText = dateLabel,
            sentDate = localDate,
            bodyText = plainSummary,
            bodyBlocks = bodyBlocks,
            inlineImageFiles = accumulator.inlineImageFiles,
            attachments = accumulator.attachments,
          )
        }
      }.recoverCatching { throw mapMailException(it) }
    }

  suspend fun downloadAttachment(
    config: MailConfig,
    uid: Long,
    partIndex: Int,
    destFile: File,
  ): Result<File> =
    withContext(Dispatchers.IO) {
      runCatching {
        withInbox(config, Folder.READ_ONLY) { folder ->
          val uidFolder = folder as? UIDFolder ?: error("服务器不支持 UID 访问")
          val msg = uidFolder.getMessageByUID(uid) ?: error("找不到对应邮件")
          val accumulator = MimeContentAccumulator(uid = uid, inlineImageDir = destFile.parentFile)
          walkPart(msg, accumulator, captureInlineBytes = false)

          val targetPart =
            accumulator.attachmentParts.getOrNull(partIndex) ?: error("未找到指定附件")
          destFile.parentFile?.mkdirs()
          targetPart.inputStream.use { input ->
            destFile.outputStream().use { output -> input.copyTo(output) }
          }
          destFile
        }
      }.recoverCatching { throw mapMailException(it) }
    }

  private inline fun <T> withInbox(config: MailConfig, mode: Int, block: (Folder) -> T): T {
    val props =
      Properties().apply {
        put("mail.store.protocol", "imaps")
        put("mail.imaps.host", config.imapHost.trim())
        put("mail.imaps.port", config.imapPort.toString())
        put("mail.imaps.ssl.enable", "true")
        put("mail.imaps.connectiontimeout", "15000")
        put("mail.imaps.timeout", "20000")
      }
    val session = Session.getInstance(props)
    val store = session.getStore("imaps")
    try {
      store.connect(config.imapHost.trim(), config.imapPort, config.email.trim(), config.appPassword.trim())
      val inbox = store.getFolder("INBOX")
      inbox.open(mode)
      return try {
        block(inbox)
      } finally {
        runCatching { inbox.close(false) }
      }
    } finally {
      runCatching { store.close() }
    }
  }

  private fun walkPart(
    part: Part,
    acc: MimeContentAccumulator,
    captureInlineBytes: Boolean,
  ) {
    val disposition = runCatching { part.disposition }.getOrNull()
    val rawFileName = runCatching { part.fileName }.getOrNull()?.let(::decodeMimeWord)
    val cleanMime = runCatching { part.contentType }.getOrNull().orEmpty().substringBefore(';').trim().lowercase()
    val contentId =
      runCatching { part.getHeader("Content-ID")?.firstOrNull() }
        .getOrNull()
        ?.let(MailBodyParser::normalizeCid)
        ?.takeIf { it.isNotBlank() }
    val isImage = cleanMime.startsWith("image/") || runCatching { part.isMimeType("image/*") }.getOrDefault(false)

    // Inline images (multipart/related with Content-ID or inline disposition) belong in the body, not attachments.
    val isInlineImage =
      isImage &&
        (contentId != null || Part.INLINE.equals(disposition, ignoreCase = true))

    if (isInlineImage) {
      val key = contentId ?: "inline_${acc.inlineImageFiles.size}"
      if (captureInlineBytes && acc.inlineImageDir != null) {
        runCatching {
          acc.inlineImageDir.mkdirs()
          val ext = extensionForMime(cleanMime, rawFileName)
          val safeKey = FileOpener.sanitizeFileName(key, "img")
          val targetFile = File(acc.inlineImageDir, "${acc.uid}_$safeKey.$ext")
          if (!targetFile.exists() || targetFile.length() == 0L) {
            part.inputStream.use { input ->
              targetFile.outputStream().use { output -> input.copyTo(output) }
            }
          }
          acc.inlineImageFiles[key] = targetFile.absolutePath
        }
      }
      return
    }

    val isAttachment =
      Part.ATTACHMENT.equals(disposition, ignoreCase = true) ||
        (!rawFileName.isNullOrBlank() &&
          !part.isMimeType("text/plain") &&
          !part.isMimeType("text/html") &&
          !part.isMimeType("multipart/*"))

    if (isAttachment) {
      val idx = acc.attachmentParts.size
      acc.attachmentParts.add(part)
      acc.attachments.add(
        MailAttachment(
          partIndex = idx,
          fileName = rawFileName?.ifBlank { null } ?: "附件_${idx + 1}",
          mimeType = cleanMime,
          sizeBytes = part.size.toLong(),
        )
      )
      return
    }

    when {
      part.isMimeType("multipart/*") -> {
        val multipart = runCatching { part.content as? Multipart }.getOrNull() ?: return
        for (i in 0 until multipart.count) {
          walkPart(multipart.getBodyPart(i), acc, captureInlineBytes)
        }
      }
      part.isMimeType("text/plain") -> {
        val text = runCatching { part.content?.toString() }.getOrNull().orEmpty()
        if (text.isNotBlank()) acc.plainTexts.add(text)
      }
      part.isMimeType("text/html") -> {
        val html = runCatching { part.content?.toString() }.getOrNull().orEmpty()
        if (html.isNotBlank()) acc.htmlTexts.add(html)
      }
    }
  }

  private fun extensionForMime(mime: String, fileName: String?): String {
    val fromName = fileName?.substringAfterLast('.', "")?.lowercase().orEmpty()
    if (fromName.length in 2..5 && fromName.all(Char::isLetterOrDigit)) return fromName
    return when (mime) {
      "image/png" -> "png"
      "image/gif" -> "gif"
      "image/webp" -> "webp"
      else -> "jpg"
    }
  }

  private fun hasLikelyAttachment(msg: Message): Boolean {
    val contentType = runCatching { msg.contentType }.getOrNull().orEmpty().lowercase()
    return contentType.contains("multipart/mixed")
  }

  private fun formatDate(date: Date, formatter: DateTimeFormatter): Pair<LocalDate, String> {
    val zoned = Instant.ofEpochMilli(date.time).atZone(ZoneId.systemDefault())
    return zoned.toLocalDate() to zoned.format(formatter)
  }

  private fun mapMailException(t: Throwable): Throwable {
    if (t is AuthenticationFailedException) {
      return IllegalStateException(
        "邮箱认证失败：请确认账号为完整邮箱（xxx@tongji.edu.cn），且密码填写的是在 mail.tongji.edu.cn 生成的“客户端专用密码”"
      )
    }
    return t
  }

  private class MimeContentAccumulator(
    val uid: Long,
    val inlineImageDir: File?,
  ) {
    val plainTexts = mutableListOf<String>()
    val htmlTexts = mutableListOf<String>()
    val inlineImageFiles = LinkedHashMap<String, String>()
    val attachments = mutableListOf<MailAttachment>()
    val attachmentParts = mutableListOf<Part>()
  }

  companion object {
    fun decodeSubject(raw: String?): String {
      val clean = raw?.trim().orEmpty()
      if (clean.isEmpty()) return "（无主题）"
      return decodeMimeWord(clean).ifBlank { "（无主题）" }
    }

    fun formatAddresses(addresses: Array<Address>?): String {
      if (addresses.isNullOrEmpty()) return "未知发件人"
      return addresses.joinToString(", ") { addr ->
        if (addr is InternetAddress) {
          val personal = addr.personal?.let(::decodeMimeWord)?.trim().orEmpty()
          if (personal.isNotEmpty()) "$personal <${addr.address}>" else addr.address ?: addr.toString()
        } else {
          addr.toString()
        }
      }
    }

    private fun decodeMimeWord(text: String): String =
      runCatching { MimeUtility.decodeText(text) }.getOrDefault(text)
  }
}
