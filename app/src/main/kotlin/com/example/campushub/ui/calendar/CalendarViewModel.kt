package com.example.campushub.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.campushub.data.local.CalendarEvent
import com.example.campushub.data.local.CalendarRepository
import com.example.campushub.data.local.EventCategory
import com.example.campushub.data.local.EventSource
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class CalendarDisplayMode(val label: String) {
  MONTH("月"),
  WEEK("周"),
}

data class EventEditorState(
  val editingId: Long? = null,
  val title: String = "",
  val date: LocalDate = LocalDate.now(),
  val timeRange: String = "",
  val note: String = "",
  val category: EventCategory = EventCategory.STUDY,
  val existingSource: EventSource = EventSource.LOCAL,
  val existingExternalId: String? = null,
  val existingCompleted: Boolean = false,
)

data class CalendarUiState(
  val displayMode: CalendarDisplayMode = CalendarDisplayMode.MONTH,
  val currentMonth: YearMonth = YearMonth.now(),
  val currentWeekStart: LocalDate = startOfWeek(LocalDate.now()),
  val eventsByDate: Map<LocalDate, List<CalendarEvent>> = emptyMap(),
  val selectedDate: LocalDate? = null,
  val editorState: EventEditorState? = null,
)

class CalendarViewModel(private val repository: CalendarRepository) : ViewModel() {

  private val displayMode = MutableStateFlow(CalendarDisplayMode.MONTH)
  private val currentMonth = MutableStateFlow(YearMonth.now())
  private val currentWeekStart = MutableStateFlow(startOfWeek(LocalDate.now()))
  private val selectedDate = MutableStateFlow<LocalDate?>(null)
  private val editorState = MutableStateFlow<EventEditorState?>(null)

  val uiState: StateFlow<CalendarUiState> =
    combine(
        displayMode,
        currentMonth,
        currentWeekStart,
        repository.events,
        selectedDate,
        editorState,
      ) { values ->
        val mode = values[0] as CalendarDisplayMode
        val month = values[1] as YearMonth
        val weekStart = values[2] as LocalDate
        @Suppress("UNCHECKED_CAST") val events = values[3] as List<CalendarEvent>
        val selected = values[4] as LocalDate?
        val editor = values[5] as EventEditorState?
        CalendarUiState(
          displayMode = mode,
          currentMonth = month,
          currentWeekStart = weekStart,
          eventsByDate = events.groupBy { it.date },
          selectedDate = selected,
          editorState = editor,
        )
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CalendarUiState(),
      )

  fun setDisplayMode(mode: CalendarDisplayMode) {
    if (mode == CalendarDisplayMode.WEEK && displayMode.value != CalendarDisplayMode.WEEK) {
      val anchorDate =
        selectedDate.value
          ?: if (currentMonth.value == YearMonth.now()) LocalDate.now()
          else currentMonth.value.atDay(1)
      currentWeekStart.value = startOfWeek(anchorDate)
    } else if (mode == CalendarDisplayMode.MONTH && displayMode.value != CalendarDisplayMode.MONTH) {
      currentMonth.value = YearMonth.from(currentWeekStart.value)
    }
    displayMode.value = mode
  }

  fun setMonth(month: YearMonth) {
    currentMonth.value = month
  }

  fun setWeekStart(weekStart: LocalDate) {
    val normalized = startOfWeek(weekStart)
    currentWeekStart.value = normalized
    currentMonth.value = YearMonth.from(normalized)
  }

  fun previousPeriod() {
    when (displayMode.value) {
      CalendarDisplayMode.MONTH -> currentMonth.value = currentMonth.value.minusMonths(1)
      CalendarDisplayMode.WEEK -> setWeekStart(currentWeekStart.value.minusWeeks(1))
    }
  }

  fun nextPeriod() {
    when (displayMode.value) {
      CalendarDisplayMode.MONTH -> currentMonth.value = currentMonth.value.plusMonths(1)
      CalendarDisplayMode.WEEK -> setWeekStart(currentWeekStart.value.plusWeeks(1))
    }
  }

  fun jumpToToday() {
    val today = LocalDate.now()
    currentMonth.value = YearMonth.from(today)
    currentWeekStart.value = startOfWeek(today)
  }

  fun selectDate(date: LocalDate) {
    selectedDate.value = date
  }

  fun dismissDayDetail() {
    selectedDate.value = null
  }

  fun openNewEventEditor(defaultDate: LocalDate = selectedDate.value ?: LocalDate.now()) {
    editorState.value = EventEditorState(date = defaultDate)
  }

  fun openEditEventEditor(event: CalendarEvent) {
    editorState.value =
      EventEditorState(
        editingId = event.id,
        title = event.title,
        date = event.date,
        timeRange = event.timeRange,
        note = event.note,
        category = event.category,
        existingSource = event.source,
        existingExternalId = event.externalId,
        existingCompleted = event.isCompleted,
      )
  }

  fun updateEditor(transform: (EventEditorState) -> EventEditorState) {
    editorState.value = editorState.value?.let(transform)
  }

  fun dismissEditor() {
    editorState.value = null
  }

  fun saveEditorEvent() {
    val state = editorState.value ?: return
    val cleanTitle = state.title.trim()
    if (cleanTitle.isEmpty()) return

    viewModelScope.launch {
      val event =
        CalendarEvent(
          id = state.editingId ?: 0L,
          title = cleanTitle,
          dateEpochDay = state.date.toEpochDay(),
          timeRange = state.timeRange.trim(),
          note = state.note.trim(),
          category = state.category,
          isCompleted = state.existingCompleted,
          source = state.existingSource,
          externalId = state.existingExternalId,
        )
      if (state.editingId == null) {
        repository.addEvent(event)
      } else {
        repository.updateEvent(event)
      }
      currentMonth.value = YearMonth.from(state.date)
      currentWeekStart.value = startOfWeek(state.date)
      editorState.value = null
    }
  }

  fun toggleEventCompleted(eventId: Long) {
    viewModelScope.launch { repository.toggleCompleted(eventId) }
  }

  fun deleteEvent(eventId: Long) {
    viewModelScope.launch { repository.deleteEvent(eventId) }
  }

  class Factory(private val repository: CalendarRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
      CalendarViewModel(repository) as T
  }
}
