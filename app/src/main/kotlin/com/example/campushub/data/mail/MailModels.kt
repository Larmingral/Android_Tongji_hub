package com.example.campushub.data.mail

import java.time.LocalDate

data class MailHeader(
  val uid: Long,
  val subject: String,
  val fromDisplay: String,
  val sentDateText: String,
  val sentDate: LocalDate,
  val isUnread: Boolean,
  val hasAttachments: Boolean,
)

data class MailAttachment(
  val partIndex: Int,
  val fileName: String,
  val mimeType: String,
  val sizeBytes: Long,
)

data class MailDetail(
  val uid: Long,
  val subject: String,
  val fromDisplay: String,
  val toDisplay: String,
  val sentDateText: String,
  val sentDate: LocalDate,
  val bodyText: String,
  val bodyBlocks: List<MailBodyBlock> = emptyList(),
  val inlineImageFiles: Map<String, String> = emptyMap(),
  val attachments: List<MailAttachment> = emptyList(),
)
