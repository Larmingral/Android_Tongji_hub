package com.example.campushub.ui.academic

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.campushub.data.academic.AcademicAttachment
import com.example.campushub.data.academic.AcademicAuthRequiredException
import com.example.campushub.data.academic.AcademicCalendarTerm
import com.example.campushub.data.academic.AcademicDashboardData
import com.example.campushub.data.academic.AcademicNotice
import com.example.campushub.data.academic.ClassPeriodTime
import com.example.campushub.data.academic.TimetableEntry
import com.example.campushub.data.academic.TongjiAcademicApiClient
import com.example.campushub.data.academic.TongjiJsonParser
import com.example.campushub.data.auth.SsoRefreshResult
import com.example.campushub.data.auth.TongjiCredentialStore
import com.example.campushub.data.auth.TongjiSsoAutoLoginRunner
import com.example.campushub.data.local.CalendarRepository
import com.example.campushub.util.FileOpener
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AcademicSection(val label: String) {
  NOTICES("通知"),
  TIMETABLE("课表"),
  EXAMS("考试"),
  GRADES("成绩"),
}

data class AcademicUiState(
  val homeUrl: String = DEFAULT_URL,
  val activeSection: AcademicSection = AcademicSection.TIMETABLE,
  val isLoading: Boolean = false,
  val isSwitchingTerm: Boolean = false,
  val needsWebLogin: Boolean = false,
  val showWebFallback: Boolean = false,
  val webFallbackNotice: String? = null,
  val errorMessage: String? = null,
  val timetableErrorOverride: String? = null,
  val dashboard: AcademicDashboardData? = null,
  val selectedCalendarId: Long = 0L,
  val selectedWeek: Int = 1,
  val overridePeriods: Map<Int, ClassPeriodTime>? = null,
  val overrideTimetable: List<TimetableEntry>? = null,
  val selectedNotice: AcademicNotice? = null,
  val isNoticeDetailLoading: Boolean = false,
  val noticeDetailError: String? = null,
  val isAttachmentOpening: Boolean = false,
  val attachmentStatus: String? = null,
  val selectedCourseSlot: TimetableEntry? = null,
  val hasSavedCredentials: Boolean = false,
  val maskedUsername: String? = null,
  val isAutoLoggingIn: Boolean = false,
  val showCredentialDialog: Boolean = false,
  val examSyncMessage: String? = null,
) {
  val activePeriods: Map<Int, ClassPeriodTime>
    get() = overridePeriods ?: dashboard?.periodTimes.orEmpty()

  val activeTimetable: List<TimetableEntry>
    get() = overrideTimetable ?: dashboard?.timetable.orEmpty()

  val activeTimetableError: String?
    get() = timetableErrorOverride ?: if (overrideTimetable == null) dashboard?.timetableError else null

  val selectedTerm: AcademicCalendarTerm?
    get() = dashboard?.terms?.firstOrNull { it.id == selectedCalendarId }

  val distinctYears: List<String>
    get() =
      dashboard?.terms.orEmpty()
        .map { it.yearLabel.ifBlank { it.fullName } }
        .distinct()

  val selectedYearLabel: String
    get() =
      selectedTerm?.let { it.yearLabel.ifBlank { it.fullName } }
        ?: distinctYears.firstOrNull().orEmpty()

  val termsInSelectedYear: List<AcademicCalendarTerm>
    get() {
      val year = selectedYearLabel
      return dashboard?.terms.orEmpty().filter { it.yearLabel.ifBlank { it.fullName } == year }
    }

  val maxWeeks: Int
    get() = selectedTerm?.weekNum ?: dashboard?.session?.totalWeeks ?: 20

  companion object {
    const val DEFAULT_URL = "https://1.tongji.edu.cn/workbench"
  }
}

class AcademicViewModel(
  private val apiClient: TongjiAcademicApiClient,
  private val credentialStore: TongjiCredentialStore,
  private val calendarRepository: CalendarRepository,
) : ViewModel() {

  private val _uiState =
    MutableStateFlow(
      AcademicUiState(
        hasSavedCredentials = credentialStore.statusFlow.value.hasCredentials,
        maskedUsername = credentialStore.statusFlow.value.maskedUsername,
      )
    )
  val uiState: StateFlow<AcademicUiState> = _uiState.asStateFlow()

  init {
    viewModelScope.launch {
      credentialStore.statusFlow.collect { summary ->
        _uiState.update {
          it.copy(
            hasSavedCredentials = summary.hasCredentials,
            maskedUsername = summary.maskedUsername,
          )
        }
      }
    }
    refreshDashboard()
  }

  fun selectSection(section: AcademicSection) {
    _uiState.update { it.copy(activeSection = section) }
  }

  fun openCredentialDialog() {
    _uiState.update { it.copy(showCredentialDialog = true) }
  }

  fun dismissCredentialDialog() {
    _uiState.update { it.copy(showCredentialDialog = false) }
  }

  fun saveSsoCredentials(username: String, password: String) {
    credentialStore.saveCredentials(username, password)
    _uiState.update { it.copy(showCredentialDialog = false, showWebFallback = false, webFallbackNotice = null) }
    refreshDashboard()
  }

  fun clearSsoCredentials() {
    credentialStore.clearCredentials()
    _uiState.update { it.copy(showCredentialDialog = false) }
  }

  fun openWebFallback(reason: String? = null) {
    _uiState.update { it.copy(showWebFallback = true, webFallbackNotice = reason) }
  }

  fun closeWebFallback() {
    _uiState.update { it.copy(showWebFallback = false, webFallbackNotice = null) }
    if (_uiState.value.dashboard == null || _uiState.value.needsWebLogin) {
      refreshDashboard()
    }
  }

  fun buildSsoAutofillJsIfSaved(): String? {
    val creds = credentialStore.loadCredentials() ?: return null
    return TongjiSsoAutoLoginRunner.buildAutofillJs(
      username = creds.username,
      password = creds.password,
      autoSubmit = false,
    )
  }

  fun onWebLoginLanded() {
    if (_uiState.value.showWebFallback) {
      _uiState.update { it.copy(showWebFallback = false, webFallbackNotice = null) }
    }
    refreshDashboard()
  }

  fun refreshDashboard() {
    viewModelScope.launch {
      _uiState.update { it.copy(isLoading = true, errorMessage = null, timetableErrorOverride = null) }
      val outcome = withAutoSsoRetry { apiClient.fetchDashboard() }
      outcome.result
        .onSuccess { data ->
          _uiState.update {
            it.copy(
              isLoading = false,
              needsWebLogin = false,
              showWebFallback = false,
              webFallbackNotice = null,
              errorMessage = null,
              timetableErrorOverride = null,
              dashboard = data,
              selectedCalendarId = data.session.currentCalendarId,
              selectedWeek = data.session.currentWeek,
              overridePeriods = null,
              overrideTimetable = null,
            )
          }
        }
        .onFailure { err ->
          val authNeeded = err is AcademicAuthRequiredException
          _uiState.update {
            it.copy(
              isLoading = false,
              needsWebLogin = authNeeded,
              showWebFallback = outcome.requiresInteractiveFallback,
              webFallbackNotice = if (outcome.requiresInteractiveFallback) err.message else null,
              errorMessage = err.message ?: "加载教务数据失败",
            )
          }
        }
    }
  }

  fun retryCurrentTimetable() {
    val dash = _uiState.value.dashboard ?: return refreshDashboard()
    val calId = _uiState.value.selectedCalendarId.takeIf { it > 0L } ?: dash.session.currentCalendarId
    val term = dash.terms.firstOrNull { it.id == calId }
    if (term != null) {
      loadTermTimetable(term.id, dash.session.uid, _uiState.value.selectedWeek)
    } else {
      refreshDashboard()
    }
  }

  fun selectYear(yearLabel: String) {
    val state = _uiState.value
    val terms = state.dashboard?.terms.orEmpty().filter { it.yearLabel.ifBlank { it.fullName } == yearLabel }
    if (terms.isEmpty()) return
    val currentTermsInYear = state.termsInSelectedYear
    val prevIdx = currentTermsInYear.indexOfFirst { it.id == state.selectedCalendarId }.coerceAtLeast(0)
    val targetTerm = terms.firstOrNull { it.isCurrent } ?: terms.getOrElse(prevIdx) { terms.first() }
    selectTerm(targetTerm)
  }

  fun selectTerm(term: AcademicCalendarTerm) {
    val uid = _uiState.value.dashboard?.session?.uid.orEmpty()
    if (uid.isBlank() || term.id == _uiState.value.selectedCalendarId) return
    val currentSessionCalId = _uiState.value.dashboard?.session?.currentCalendarId
    val targetWeek =
      if (term.id == currentSessionCalId) _uiState.value.dashboard?.session?.currentWeek ?: 1 else 1
    loadTermTimetable(term.id, uid, targetWeek)
  }

  private fun loadTermTimetable(calendarId: Long, uid: String, targetWeek: Int) {
    val auths = _uiState.value.dashboard?.session?.auths.orEmpty()
    viewModelScope.launch {
      _uiState.update { it.copy(isSwitchingTerm = true, timetableErrorOverride = null) }
      val outcome = withAutoSsoRetry { apiClient.fetchTimetableForTerm(calendarId, uid, auths) }
      outcome.result
        .onSuccess { (periods, entries) ->
          _uiState.update {
            it.copy(
              isSwitchingTerm = false,
              selectedCalendarId = calendarId,
              selectedWeek = targetWeek,
              overridePeriods = periods,
              overrideTimetable = entries,
              timetableErrorOverride = null,
            )
          }
        }
        .onFailure { err ->
          _uiState.update {
            it.copy(
              isSwitchingTerm = false,
              selectedCalendarId = calendarId,
              overrideTimetable = emptyList(),
              showWebFallback = outcome.requiresInteractiveFallback,
              webFallbackNotice = if (outcome.requiresInteractiveFallback) err.message else null,
              timetableErrorOverride = "拉取课表失败 (calendarId=$calendarId)：${err.message ?: "未知错误"}",
            )
          }
        }
    }
  }

  fun previousWeek() {
    _uiState.update { it.copy(selectedWeek = (it.selectedWeek - 1).coerceAtLeast(1)) }
  }

  fun nextWeek() {
    _uiState.update { it.copy(selectedWeek = (it.selectedWeek + 1).coerceAtMost(it.maxWeeks)) }
  }

  fun jumpToCurrentWeek() {
    val current = _uiState.value.dashboard?.session ?: return
    val currentTerm = _uiState.value.dashboard?.terms?.firstOrNull { it.id == current.currentCalendarId }
    if (_uiState.value.selectedCalendarId != current.currentCalendarId && currentTerm != null) {
      selectTerm(currentTerm)
    } else {
      _uiState.update { it.copy(selectedWeek = current.currentWeek) }
    }
  }

  fun openNotice(notice: AcademicNotice) {
    _uiState.update {
      it.copy(
        selectedNotice = notice,
        isNoticeDetailLoading = !notice.isDetailLoaded,
        noticeDetailError = null,
      )
    }
    if (notice.isDetailLoaded) return

    viewModelScope.launch {
      val outcome = withAutoSsoRetry { apiClient.fetchNoticeDetail(notice) }
      outcome.result
        .onSuccess { detailed ->
          _uiState.update { state ->
            val updatedNotices =
              state.dashboard?.notices?.map { item ->
                if (item.id == detailed.id) detailed else item
              }
            state.copy(
              isNoticeDetailLoading = false,
              selectedNotice =
                if (state.selectedNotice?.id == detailed.id) detailed else state.selectedNotice,
              dashboard = state.dashboard?.copy(notices = updatedNotices ?: state.dashboard.notices),
            )
          }
        }
        .onFailure { err ->
          _uiState.update { state ->
            state.copy(
              isNoticeDetailLoading = false,
              showWebFallback = outcome.requiresInteractiveFallback,
              webFallbackNotice = if (outcome.requiresInteractiveFallback) err.message else null,
              noticeDetailError = err.message ?: "加载通知正文失败",
            )
          }
        }
    }
  }

  fun dismissNotice() {
    _uiState.update {
      it.copy(
        selectedNotice = null,
        isNoticeDetailLoading = false,
        noticeDetailError = null,
        isAttachmentOpening = false,
        attachmentStatus = null,
      )
    }
  }

  fun openAttachment(context: Context, attachment: AcademicAttachment) {
    val safeName = FileOpener.sanitizeFileName(attachment.fileName, "tongji_attachment")
    val targetFile = File(FileOpener.downloadsDir(context), safeName)
    val session = _uiState.value.dashboard?.session

    viewModelScope.launch {
      _uiState.update { it.copy(isAttachmentOpening = true, attachmentStatus = null) }
      val outcome = withAutoSsoRetry { apiClient.downloadAttachment(attachment, session, targetFile) }
      outcome.result
        .onSuccess { downloaded ->
          _uiState.update { it.copy(isAttachmentOpening = false) }
          FileOpener.openFile(context, downloaded).onFailure { err ->
            _uiState.update {
              it.copy(attachmentStatus = "已下载 ${downloaded.name}，但本机无匹配打开应用：${err.message}")
            }
          }
        }
        .onFailure { err ->
          _uiState.update {
            it.copy(
              isAttachmentOpening = false,
              showWebFallback = outcome.requiresInteractiveFallback,
              webFallbackNotice = if (outcome.requiresInteractiveFallback) err.message else null,
              attachmentStatus = err.message ?: "下载附件失败",
            )
          }
        }
    }
  }

  fun openNoticeLink(context: Context, rawUrl: String) {
    val cleanUrl = rawUrl.trim()
    if (cleanUrl.isEmpty()) return

    if (cleanUrl.contains("1.tongji.edu.cn", ignoreCase = true) &&
      cleanUrl.contains("obsfile/downloadfile", ignoreCase = true)
    ) {
      val fallbackName =
        runCatching { Uri.parse(cleanUrl).getQueryParameter("fileName") }.getOrNull()
          ?.takeIf { it.isNotBlank() }
          ?: "notice_attachment"
      val targetFile = File(FileOpener.downloadsDir(context), FileOpener.sanitizeFileName(fallbackName))
      val session = _uiState.value.dashboard?.session
      viewModelScope.launch {
        _uiState.update { it.copy(isAttachmentOpening = true, attachmentStatus = null) }
        val outcome = withAutoSsoRetry { apiClient.downloadUrlWithSession(cleanUrl, session, targetFile) }
        outcome.result
          .onSuccess { downloaded ->
            _uiState.update { it.copy(isAttachmentOpening = false) }
            FileOpener.openFile(context, downloaded).onFailure { err ->
              _uiState.update { it.copy(attachmentStatus = "已下载文件，无法打开：${err.message}") }
            }
          }
          .onFailure { err ->
            _uiState.update {
              it.copy(
                isAttachmentOpening = false,
                attachmentStatus = err.message ?: "链接下载失败",
              )
            }
          }
      }
      return
    }

    runCatching {
      val intent =
        Intent(Intent.ACTION_VIEW, Uri.parse(cleanUrl)).apply {
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
      context.startActivity(intent)
    }.onFailure { err ->
      _uiState.update { it.copy(attachmentStatus = "无法打开链接：${err.message}") }
    }
  }

  fun openCourseSlot(entry: TimetableEntry) {
    _uiState.update { it.copy(selectedCourseSlot = entry) }
  }

  fun dismissCourseSlot() {
    _uiState.update { it.copy(selectedCourseSlot = null) }
  }

  fun syncExamsToCalendar() {
    val exams = _uiState.value.dashboard?.examResult?.exams.orEmpty()
    val events = TongjiJsonParser.toExamCalendarEvents(exams)
    if (events.isEmpty()) {
      _uiState.update { it.copy(examSyncMessage = "当前暂无可加入日历的考试安排") }
      return
    }
    viewModelScope.launch {
      val count = calendarRepository.upsertCanvasEvents(events)
      _uiState.update {
        it.copy(examSyncMessage = "已同步 $count 门考试到日历（紫标「考试/重要」，重复点击自动去重）")
      }
    }
  }

  fun clearExamSyncMessage() {
    _uiState.update { it.copy(examSyncMessage = null) }
  }

  private data class AutoRetryOutcome<T>(
    val result: Result<T>,
    val requiresInteractiveFallback: Boolean = false,
  )

  private suspend fun <T> withAutoSsoRetry(action: suspend () -> Result<T>): AutoRetryOutcome<T> {
    val first = action()
    if (first.isSuccess || first.exceptionOrNull() !is AcademicAuthRequiredException) {
      return AutoRetryOutcome(first)
    }
    val creds = credentialStore.loadCredentials() ?: return AutoRetryOutcome(first)

    _uiState.update { it.copy(isAutoLoggingIn = true, errorMessage = null) }
    val refreshResult =
      TongjiSsoAutoLoginRunner.refreshSession(
        context = credentialStore.appContext,
        entryUrl = AcademicUiState.DEFAULT_URL,
        expectedHost = "1.tongji.edu.cn",
        credentials = creds,
      ) {
        apiClient.hasValidSession()
      }
    _uiState.update { it.copy(isAutoLoggingIn = false) }

    return when (refreshResult) {
      is SsoRefreshResult.Success -> AutoRetryOutcome(action())
      is SsoRefreshResult.Failed ->
        AutoRetryOutcome(
          result = Result.failure(AcademicAuthRequiredException(refreshResult.reason)),
          requiresInteractiveFallback = refreshResult.requiresInteractiveLogin,
        )
    }
  }

  class Factory(
    private val apiClient: TongjiAcademicApiClient,
    private val credentialStore: TongjiCredentialStore,
    private val calendarRepository: CalendarRepository,
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
      AcademicViewModel(apiClient, credentialStore, calendarRepository) as T
  }
}
