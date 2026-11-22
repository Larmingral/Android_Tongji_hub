package com.example.campushub.ui.academic

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.campushub.data.academic.AcademicAuthRequiredException
import com.example.campushub.data.academic.AcademicCalendarTerm
import com.example.campushub.data.academic.AcademicDashboardData
import com.example.campushub.data.academic.AcademicNotice
import com.example.campushub.data.academic.ClassPeriodTime
import com.example.campushub.data.academic.TimetableEntry
import com.example.campushub.data.academic.TongjiAcademicApiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AcademicDisplayMode(val label: String) {
  NATIVE("原生教务"),
  WEB_LOGIN("网页登录(免登)"),
}

enum class AcademicSection(val label: String) {
  NOTICES("通知"),
  TIMETABLE("课表"),
  EXAMS("考试"),
  GRADES("成绩"),
}

enum class TimetableViewStyle(val label: String) {
  GRID("网格"),
  LIST("列表"),
}

data class AcademicUiState(
  val homeUrl: String = DEFAULT_URL,
  val displayMode: AcademicDisplayMode = AcademicDisplayMode.NATIVE,
  val activeSection: AcademicSection = AcademicSection.TIMETABLE,
  val timetableStyle: TimetableViewStyle = TimetableViewStyle.GRID,
  val isLoading: Boolean = false,
  val isSwitchingTerm: Boolean = false,
  val needsWebLogin: Boolean = false,
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
  val selectedCourseSlot: TimetableEntry? = null,
  val probeReport: String? = null,
) {
  val activePeriods: Map<Int, ClassPeriodTime>
    get() = overridePeriods ?: dashboard?.periodTimes.orEmpty()

  val activeTimetable: List<TimetableEntry>
    get() = overrideTimetable ?: dashboard?.timetable.orEmpty()

  val activeTimetableError: String?
    get() = timetableErrorOverride ?: if (overrideTimetable == null) dashboard?.timetableError else null

  val selectedTerm: AcademicCalendarTerm?
    get() = dashboard?.terms?.firstOrNull { it.id == selectedCalendarId }

  val maxWeeks: Int
    get() = selectedTerm?.weekNum ?: dashboard?.session?.totalWeeks ?: 20

  companion object {
    const val DEFAULT_URL = "https://1.tongji.edu.cn/workbench"
  }
}

class AcademicViewModel(
  private val apiClient: TongjiAcademicApiClient,
) : ViewModel() {

  private val _uiState = MutableStateFlow(AcademicUiState())
  val uiState: StateFlow<AcademicUiState> = _uiState.asStateFlow()

  init {
    refreshDashboard()
  }

  fun switchDisplayMode(mode: AcademicDisplayMode) {
    _uiState.update { it.copy(displayMode = mode) }
    if (mode == AcademicDisplayMode.NATIVE &&
      (_uiState.value.dashboard == null || _uiState.value.needsWebLogin)
    ) {
      refreshDashboard()
    }
  }

  fun selectSection(section: AcademicSection) {
    _uiState.update { it.copy(activeSection = section) }
  }

  fun setTimetableStyle(style: TimetableViewStyle) {
    _uiState.update { it.copy(timetableStyle = style) }
  }

  fun onWebLoginLanded() {
    if (_uiState.value.needsWebLogin || _uiState.value.dashboard == null) {
      refreshDashboard()
    }
  }

  fun refreshDashboard() {
    viewModelScope.launch {
      _uiState.update { it.copy(isLoading = true, errorMessage = null, timetableErrorOverride = null) }
      apiClient
        .fetchDashboard()
        .onSuccess { data ->
          _uiState.update {
            it.copy(
              isLoading = false,
              needsWebLogin = false,
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
      apiClient
        .fetchTimetableForTerm(calendarId, uid, auths)
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
      apiClient
        .fetchNoticeDetail(notice)
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
              noticeDetailError = err.message ?: "加载通知正文失败",
            )
          }
        }
    }
  }

  fun dismissNotice() {
    _uiState.update {
      it.copy(selectedNotice = null, isNoticeDetailLoading = false, noticeDetailError = null)
    }
  }

  fun openCourseSlot(entry: TimetableEntry) {
    _uiState.update { it.copy(selectedCourseSlot = entry) }
  }

  fun dismissCourseSlot() {
    _uiState.update { it.copy(selectedCourseSlot = null) }
  }

  fun showProbeReport(report: String) {
    _uiState.update { it.copy(probeReport = report) }
  }

  fun dismissProbeReport() {
    _uiState.update { it.copy(probeReport = null) }
  }

  class Factory(private val apiClient: TongjiAcademicApiClient) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
      AcademicViewModel(apiClient) as T
  }
}
