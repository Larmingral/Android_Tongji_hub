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
  val dashboard: AcademicDashboardData? = null,
  val selectedCalendarId: Long = 0L,
  val selectedWeek: Int = 1,
  val overridePeriods: Map<Int, ClassPeriodTime>? = null,
  val overrideTimetable: List<TimetableEntry>? = null,
  val selectedNotice: AcademicNotice? = null,
  val selectedCourseSlot: TimetableEntry? = null,
  val probeReport: String? = null,
) {
  val activePeriods: Map<Int, ClassPeriodTime>
    get() = overridePeriods ?: dashboard?.periodTimes.orEmpty()

  val activeTimetable: List<TimetableEntry>
    get() = overrideTimetable ?: dashboard?.timetable.orEmpty()

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
      _uiState.update { it.copy(isLoading = true, errorMessage = null) }
      apiClient
        .fetchDashboard()
        .onSuccess { data ->
          _uiState.update {
            it.copy(
              isLoading = false,
              needsWebLogin = false,
              errorMessage = null,
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

  fun selectTerm(term: AcademicCalendarTerm) {
    val uid = _uiState.value.dashboard?.session?.uid.orEmpty()
    if (uid.isBlank() || term.id == _uiState.value.selectedCalendarId) return

    viewModelScope.launch {
      _uiState.update { it.copy(isSwitchingTerm = true) }
      apiClient
        .fetchTimetableForTerm(term.id, uid)
        .onSuccess { (periods, entries) ->
          val currentSessionCalId = _uiState.value.dashboard?.session?.currentCalendarId
          val defaultWeek =
            if (term.id == currentSessionCalId) {
              _uiState.value.dashboard?.session?.currentWeek ?: 1
            } else {
              1
            }
          _uiState.update {
            it.copy(
              isSwitchingTerm = false,
              selectedCalendarId = term.id,
              selectedWeek = defaultWeek,
              overridePeriods = periods,
              overrideTimetable = entries,
            )
          }
        }
        .onFailure { err ->
          _uiState.update {
            it.copy(
              isSwitchingTerm = false,
              errorMessage = err.message ?: "切换学期课表失败",
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
    _uiState.update { it.copy(selectedNotice = notice) }
  }

  fun dismissNotice() {
    _uiState.update { it.copy(selectedNotice = null) }
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
