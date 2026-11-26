package com.example.campushub.ui.mail

import android.content.Context
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.campushub.data.local.CalendarEvent
import com.example.campushub.data.local.CalendarRepository
import com.example.campushub.data.local.EventCategory
import com.example.campushub.data.local.EventSource
import com.example.campushub.data.mail.ImapMailClient
import com.example.campushub.data.mail.MailAttachment
import com.example.campushub.data.mail.MailConfig
import com.example.campushub.data.mail.MailConfigStore
import com.example.campushub.data.mail.MailDetail
import com.example.campushub.data.mail.MailHeader
import com.example.campushub.ui.common.ImagePreviewPayload
import com.example.campushub.util.FileOpener
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MailUiState(
  val emailInput: String,
  val passwordInput: String,
  val imapHostInput: String,
  val imapPortInput: String,
  val isConfigExpanded: Boolean,
  val isLoadingList: Boolean = false,
  val isLoadingDetail: Boolean = false,
  val isOpeningAttachment: Boolean = false,
  val messages: List<MailHeader> = emptyList(),
  val selectedDetail: MailDetail? = null,
  val errorMessage: String? = null,
  val statusMessage: String? = null,
  val previewImage: ImagePreviewPayload? = null,
) {
  val isShowingDetail: Boolean
    get() = selectedDetail != null || isLoadingDetail
}

class MailViewModel(
  private val configStore: MailConfigStore,
  private val mailClient: ImapMailClient,
  private val calendarRepository: CalendarRepository,
) : ViewModel() {

  private val initialConfig = configStore.load()

  private val _uiState =
    MutableStateFlow(
      MailUiState(
        emailInput = initialConfig.email,
        passwordInput = initialConfig.appPassword,
        imapHostInput = initialConfig.imapHost,
        imapPortInput = initialConfig.imapPort.toString(),
        isConfigExpanded = initialConfig.email.isBlank() || initialConfig.appPassword.isBlank(),
      )
    )
  val uiState: StateFlow<MailUiState> = _uiState.asStateFlow()

  init {
    if (initialConfig.email.isNotBlank() && initialConfig.appPassword.isNotBlank()) {
      connectAndRefresh()
    }
  }

  fun updateEmail(value: String) {
    _uiState.update { it.copy(emailInput = value, errorMessage = null) }
  }

  fun updatePassword(value: String) {
    _uiState.update { it.copy(passwordInput = value, errorMessage = null) }
  }

  fun updateHost(value: String) {
    _uiState.update { it.copy(imapHostInput = value, errorMessage = null) }
  }

  fun updatePort(value: String) {
    _uiState.update { it.copy(imapPortInput = value.filter(Char::isDigit), errorMessage = null) }
  }

  fun toggleConfigExpanded() {
    _uiState.update { it.copy(isConfigExpanded = !it.isConfigExpanded) }
  }

  fun connectAndRefresh() {
    val current = _uiState.value
    val port = current.imapPortInput.toIntOrNull() ?: MailConfig.DEFAULT_IMAP_PORT
    val config =
      MailConfig(
        email = current.emailInput.trim(),
        appPassword = current.passwordInput.trim(),
        imapHost = current.imapHostInput.trim().ifEmpty { MailConfig.DEFAULT_IMAP_HOST },
        imapPort = port,
      )
    configStore.save(config)

    viewModelScope.launch {
      _uiState.update {
        it.copy(
          emailInput = config.email,
          imapHostInput = config.imapHost,
          imapPortInput = config.imapPort.toString(),
          isLoadingList = true,
          errorMessage = null,
          statusMessage = null,
        )
      }
      mailClient
        .fetchRecentMessages(config)
        .onSuccess { list ->
          _uiState.update {
            it.copy(
              isLoadingList = false,
              messages = list,
              isConfigExpanded = false,
              errorMessage = null,
            )
          }
        }
        .onFailure { err ->
          _uiState.update {
            it.copy(
              isLoadingList = false,
              isConfigExpanded = true,
              errorMessage = err.message ?: "连接同济邮箱失败",
            )
          }
        }
    }
  }

  fun openMessage(context: Context, header: MailHeader) {
    val config = currentConfig()
    val inlineDir = File(FileOpener.downloadsDir(context), "mail_inline")
    viewModelScope.launch {
      _uiState.update { it.copy(isLoadingDetail = true, errorMessage = null, statusMessage = null) }
      mailClient
        .fetchMessageDetail(config, header.uid, inlineDir)
        .onSuccess { detail ->
          _uiState.update { state ->
            state.copy(
              isLoadingDetail = false,
              selectedDetail = detail,
              messages =
                state.messages.map { item ->
                  if (item.uid == header.uid) item.copy(isUnread = false) else item
                },
            )
          }
        }
        .onFailure { err ->
          _uiState.update {
            it.copy(
              isLoadingDetail = false,
              errorMessage = err.message ?: "读取邮件正文失败",
            )
          }
        }
    }
  }

  fun closeMessageDetail() {
    _uiState.update { it.copy(selectedDetail = null, isLoadingDetail = false, statusMessage = null) }
  }

  fun openImagePreview(payload: ImagePreviewPayload) {
    _uiState.update { it.copy(previewImage = payload) }
  }

  fun dismissImagePreview() {
    _uiState.update { it.copy(previewImage = null) }
  }

  fun openAttachment(context: Context, uid: Long, attachment: MailAttachment) {
    val config = currentConfig()
    val safeName = FileOpener.sanitizeFileName(attachment.fileName, "mail_attachment")
    val tempFile = File(FileOpener.downloadsDir(context), "mail_${uid}_${System.nanoTime()}_$safeName")

    viewModelScope.launch {
      _uiState.update { it.copy(isOpeningAttachment = true, statusMessage = null) }
      mailClient
        .downloadAttachment(config, uid, attachment.partIndex, tempFile)
        .onSuccess { file ->
          val savedResult =
            FileOpener.saveFileToPublicDownloads(
              context = context,
              sourceFile = file,
              desiredFileName = safeName,
              explicitMimeType = attachment.mimeType,
            )
          file.delete()
          savedResult.fold(
            onSuccess = { saved ->
              if (FileOpener.isImageFile(saved.fileName, saved.mimeType)) {
                _uiState.update {
                  it.copy(
                    isOpeningAttachment = false,
                    statusMessage = "已保存至 ${saved.displayPath}",
                    previewImage =
                      ImagePreviewPayload(
                        title = saved.fileName,
                        contentUri = saved.uri,
                        mimeType = saved.mimeType,
                        alreadySavedPath = saved.displayPath,
                      ),
                  )
                }
              } else {
                _uiState.update {
                  it.copy(isOpeningAttachment = false, statusMessage = "已保存至 ${saved.displayPath}")
                }
                FileOpener.openUri(context, saved.uri, saved.mimeType).onFailure { err ->
                  _uiState.update {
                    it.copy(statusMessage = "已保存至 ${saved.displayPath}，但系统无匹配打开应用：${err.message}")
                  }
                }
              }
            },
            onFailure = { err ->
              _uiState.update {
                it.copy(isOpeningAttachment = false, statusMessage = "保存到下载目录失败：${err.message}")
              }
            },
          )
        }
        .onFailure { err ->
          tempFile.delete()
          _uiState.update {
            it.copy(isOpeningAttachment = false, statusMessage = err.message ?: "下载附件失败")
          }
        }
    }
  }

  fun addSelectedMailToCalendar() {
    val detail = _uiState.value.selectedDetail ?: return
    val snippet = detail.bodyText.lineSequence().take(4).joinToString("\n").take(180)
    viewModelScope.launch {
      val count =
        calendarRepository.upsertCanvasEvents(
          listOf(
            CalendarEvent(
              title = detail.subject,
              dateEpochDay = detail.sentDate.toEpochDay(),
              timeRange = "",
              note = "来自邮件：${detail.fromDisplay}\n$snippet",
              category = EventCategory.OTHER,
              isCompleted = false,
              source = EventSource.LOCAL,
              externalId = "mail_${detail.uid}",
            )
          )
        )
      if (count > 0) {
        _uiState.update { it.copy(statusMessage = "已将此邮件添加到 ${detail.sentDate} 的日历中") }
      }
    }
  }

  fun clearStatusMessage() {
    _uiState.update { it.copy(statusMessage = null) }
  }

  private fun currentConfig(): MailConfig {
    val state = _uiState.value
    return MailConfig(
      email = state.emailInput.trim(),
      appPassword = state.passwordInput.trim(),
      imapHost = state.imapHostInput.trim().ifEmpty { MailConfig.DEFAULT_IMAP_HOST },
      imapPort = state.imapPortInput.toIntOrNull() ?: MailConfig.DEFAULT_IMAP_PORT,
    )
  }

  class Factory(
    private val configStore: MailConfigStore,
    private val mailClient: ImapMailClient,
    private val calendarRepository: CalendarRepository,
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
      MailViewModel(configStore, mailClient, calendarRepository) as T
  }
}
