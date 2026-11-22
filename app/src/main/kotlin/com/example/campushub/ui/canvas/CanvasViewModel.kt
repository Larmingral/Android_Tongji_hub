package com.example.campushub.ui.canvas

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.campushub.data.canvas.CanvasApiClient
import com.example.campushub.data.canvas.CanvasAssignmentDetail
import com.example.campushub.data.canvas.CanvasConfigStore
import com.example.campushub.data.canvas.CanvasCourse
import com.example.campushub.data.canvas.CanvasCourseDetailData
import com.example.campushub.data.canvas.CanvasDashboardData
import com.example.campushub.data.canvas.CanvasFileItem
import com.example.campushub.data.canvas.CanvasModuleItem
import com.example.campushub.data.canvas.CanvasTaskItem
import com.example.campushub.data.canvas.CanvasViewMode
import com.example.campushub.data.local.CalendarEvent
import com.example.campushub.data.local.CalendarRepository
import com.example.campushub.data.local.EventCategory
import com.example.campushub.data.local.EventSource
import com.example.campushub.util.FileOpener
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface CanvasSubPage {
  data object Dashboard : CanvasSubPage

  data class CourseDetail(
    val course: CanvasCourse,
    val isLoading: Boolean = true,
    val data: CanvasCourseDetailData? = null,
    val errorMessage: String? = null,
  ) : CanvasSubPage

  data class AssignmentDetail(
    val courseId: Long,
    val assignmentId: Long,
    val courseName: String,
    val fallbackTitle: String,
    val fallbackHtmlUrl: String?,
    val isLoading: Boolean = true,
    val data: CanvasAssignmentDetail? = null,
    val errorMessage: String? = null,
  ) : CanvasSubPage
}

data class CanvasUiState(
  val baseUrl: String,
  val tokenInput: String,
  val mode: CanvasViewMode,
  val isConfigExpanded: Boolean,
  val isLoading: Boolean = false,
  val isFileOpening: Boolean = false,
  val dashboard: CanvasDashboardData? = null,
  val completedTasksExpanded: Boolean = false,
  val backStack: List<CanvasSubPage> = listOf(CanvasSubPage.Dashboard),
  val errorMessage: String? = null,
  val statusMessage: String? = null,
  val webUrlToLoad: String,
  val webReloadNonce: Int = 0,
) {
  val currentSubPage: CanvasSubPage
    get() = backStack.lastOrNull() ?: CanvasSubPage.Dashboard

  val canNavigateBackInNative: Boolean
    get() = mode == CanvasViewMode.NATIVE_API && backStack.size > 1
}

class CanvasViewModel(
  private val configStore: CanvasConfigStore,
  private val apiClient: CanvasApiClient,
  private val calendarRepository: CalendarRepository,
) : ViewModel() {

  private val initialUrl = configStore.baseUrl
  private val initialToken = configStore.accessToken

  private val _uiState =
    MutableStateFlow(
      CanvasUiState(
        baseUrl = initialUrl,
        tokenInput = initialToken,
        mode = configStore.preferredMode,
        isConfigExpanded = initialToken.isBlank(),
        webUrlToLoad = initialUrl,
      )
    )
  val uiState: StateFlow<CanvasUiState> = _uiState.asStateFlow()

  init {
    if (initialToken.isNotBlank()) {
      connectAndFetch()
    }
  }

  fun switchMode(mode: CanvasViewMode) {
    configStore.preferredMode = mode
    _uiState.update { it.copy(mode = mode, statusMessage = null) }
  }

  fun updateBaseUrl(value: String) {
    _uiState.update { it.copy(baseUrl = value, errorMessage = null) }
  }

  fun updateTokenInput(value: String) {
    _uiState.update { it.copy(tokenInput = value, errorMessage = null) }
  }

  fun toggleConfigExpanded() {
    _uiState.update { it.copy(isConfigExpanded = !it.isConfigExpanded) }
  }

  fun toggleCompletedTasksExpanded() {
    _uiState.update { it.copy(completedTasksExpanded = !it.completedTasksExpanded) }
  }

  fun connectAndFetch() {
    val current = _uiState.value
    val cleanUrl = CanvasConfigStore.normalizeBaseUrl(current.baseUrl)
    val cleanToken = current.tokenInput.trim()

    configStore.baseUrl = cleanUrl
    configStore.accessToken = cleanToken

    if (cleanToken.isEmpty()) {
      _uiState.update {
        it.copy(
          baseUrl = cleanUrl,
          isConfigExpanded = true,
          errorMessage = "请先填入 Canvas Access Token，或直接切换上方“内置网页”模式使用",
        )
      }
      return
    }

    viewModelScope.launch {
      _uiState.update {
        it.copy(
          baseUrl = cleanUrl,
          tokenInput = cleanToken,
          isLoading = true,
          errorMessage = null,
          statusMessage = null,
        )
      }
      apiClient
        .fetchDashboard(cleanUrl, cleanToken)
        .onSuccess { data ->
          _uiState.update {
            it.copy(
              isLoading = false,
              dashboard = data,
              isConfigExpanded = false,
              errorMessage = null,
            )
          }
        }
        .onFailure { err ->
          _uiState.update {
            it.copy(
              isLoading = false,
              isConfigExpanded = true,
              errorMessage = err.message ?: "连接 Canvas API 失败",
            )
          }
        }
    }
  }

  fun openCourse(course: CanvasCourse) {
    val page = CanvasSubPage.CourseDetail(course = course, isLoading = true)
    _uiState.update { it.copy(backStack = it.backStack + page, statusMessage = null) }

    val state = _uiState.value
    viewModelScope.launch {
      apiClient
        .fetchCourseDetail(state.baseUrl, state.tokenInput, course)
        .onSuccess { detail ->
          updateTopCoursePage(course.id) {
            it.copy(isLoading = false, data = detail, errorMessage = null)
          }
        }
        .onFailure { err ->
          updateTopCoursePage(course.id) {
            it.copy(isLoading = false, errorMessage = err.message ?: "加载课程详情失败")
          }
        }
    }
  }

  fun openTask(task: CanvasTaskItem) {
    val cId = task.courseId
    val aId = task.assignmentId
    if (cId != null && aId != null) {
      openAssignment(
        courseId = cId,
        assignmentId = aId,
        courseName = task.courseName,
        fallbackTitle = task.title,
        fallbackHtmlUrl = task.htmlUrl,
      )
    } else if (!task.htmlUrl.isNullOrBlank()) {
      openUrlInWebView(task.htmlUrl)
    }
  }

  fun openAssignment(
    courseId: Long,
    assignmentId: Long,
    courseName: String,
    fallbackTitle: String,
    fallbackHtmlUrl: String?,
  ) {
    val page =
      CanvasSubPage.AssignmentDetail(
        courseId = courseId,
        assignmentId = assignmentId,
        courseName = courseName,
        fallbackTitle = fallbackTitle,
        fallbackHtmlUrl = fallbackHtmlUrl,
        isLoading = true,
      )
    _uiState.update { it.copy(backStack = it.backStack + page, statusMessage = null) }

    val state = _uiState.value
    viewModelScope.launch {
      apiClient
        .fetchAssignmentDetail(state.baseUrl, state.tokenInput, courseId, assignmentId, courseName)
        .onSuccess { detail ->
          updateTopAssignmentPage(assignmentId) {
            it.copy(isLoading = false, data = detail, errorMessage = null)
          }
        }
        .onFailure { err ->
          updateTopAssignmentPage(assignmentId) {
            it.copy(isLoading = false, errorMessage = err.message ?: "加载作业详情失败")
          }
        }
    }
  }

  fun navigateBackInNative() {
    _uiState.update {
      if (it.backStack.size > 1) it.copy(backStack = it.backStack.dropLast(1)) else it
    }
  }

  fun syncTasksToCalendar() {
    val tasks = _uiState.value.dashboard?.tasks.orEmpty()
    val syncable =
      tasks.mapNotNull { task ->
        val due = task.dueDate ?: return@mapNotNull null
        CalendarEvent(
          title = task.title,
          dateEpochDay = due.toEpochDay(),
          timeRange = if (task.dueTimeText.isNotBlank()) "截止 ${task.dueTimeText}" else "",
          note = task.courseName,
          category = EventCategory.DDL,
          isCompleted = task.isSubmitted,
          source = EventSource.CANVAS_DDL,
          externalId = task.externalId,
        )
      }

    if (syncable.isEmpty()) {
      _uiState.update { it.copy(statusMessage = "当前待办与作业中没有带截止日期的任务可同步") }
      return
    }

    val pendingCount = syncable.count { !it.isCompleted }
    val doneCount = syncable.count { it.isCompleted }

    viewModelScope.launch {
      val count = calendarRepository.upsertCanvasEvents(syncable)
      _uiState.update {
        it.copy(statusMessage = "已同步 $count 条任务到日历（待完成 $pendingCount · 已完成 $doneCount）")
      }
    }
  }

  fun openCourseFile(context: Context, fileItem: CanvasFileItem) {
    val downloadUrl = fileItem.downloadUrl
    if (downloadUrl.isNullOrBlank()) {
      _uiState.update { it.copy(statusMessage = "该文件暂无可用下载地址") }
      return
    }
    downloadAndOpen(
      context = context,
      fileName = fileItem.displayName,
      mimeType = fileItem.mimeType,
      downloadUrl = downloadUrl,
    )
  }

  fun openModuleItem(context: Context, course: CanvasCourse, item: CanvasModuleItem, cachedFiles: List<CanvasFileItem>) {
    if (item.type.equals("File", ignoreCase = true)) {
      val matched = item.contentId?.let { cid -> cachedFiles.firstOrNull { it.id == cid } }
      if (matched != null && !matched.downloadUrl.isNullOrBlank()) {
        openCourseFile(context, matched)
        return
      }
      val apiUrl = item.apiUrl
      if (!apiUrl.isNullOrBlank()) {
        val token = _uiState.value.tokenInput
        viewModelScope.launch {
          _uiState.update { it.copy(isFileOpening = true, statusMessage = null) }
          apiClient
            .resolveFileByApiUrl(apiUrl, token)
            .onSuccess { resolved ->
              _uiState.update { it.copy(isFileOpening = false) }
              openCourseFile(context, resolved)
            }
            .onFailure {
              _uiState.update { it.copy(isFileOpening = false) }
              item.htmlUrl?.let(::openUrlInWebView)
            }
        }
        return
      }
    }

    if (item.type.equals("Assignment", ignoreCase = true) && item.contentId != null) {
      openAssignment(
        courseId = course.id,
        assignmentId = item.contentId,
        courseName = course.name,
        fallbackTitle = item.title,
        fallbackHtmlUrl = item.htmlUrl,
      )
      return
    }

    item.htmlUrl?.let(::openUrlInWebView)
  }

  private fun downloadAndOpen(
    context: Context,
    fileName: String,
    mimeType: String?,
    downloadUrl: String,
  ) {
    val token = _uiState.value.tokenInput
    val safeName = FileOpener.sanitizeFileName(fileName, "canvas_file")
    val targetFile = File(FileOpener.downloadsDir(context), safeName)

    viewModelScope.launch {
      _uiState.update { it.copy(isFileOpening = true, statusMessage = null) }
      apiClient
        .downloadFile(downloadUrl, token, targetFile)
        .onSuccess { downloaded ->
          _uiState.update { it.copy(isFileOpening = false) }
          FileOpener.openFile(context, downloaded, mimeType).onFailure { err ->
            _uiState.update {
              it.copy(statusMessage = "已下载 ${downloaded.name}，但本机无匹配打开应用：${err.message}")
            }
          }
        }
        .onFailure { err ->
          _uiState.update {
            it.copy(isFileOpening = false, statusMessage = err.message ?: "下载课件失败")
          }
        }
    }
  }

  fun openUrlInWebView(url: String) {
    configStore.preferredMode = CanvasViewMode.WEB_VIEW
    _uiState.update {
      it.copy(
        mode = CanvasViewMode.WEB_VIEW,
        webUrlToLoad = url,
        webReloadNonce = it.webReloadNonce + 1,
      )
    }
  }

  fun clearStatusMessage() {
    _uiState.update { it.copy(statusMessage = null) }
  }

  private fun updateTopCoursePage(
    courseId: Long,
    transform: (CanvasSubPage.CourseDetail) -> CanvasSubPage.CourseDetail,
  ) {
    _uiState.update { state ->
      val top = state.backStack.lastOrNull() as? CanvasSubPage.CourseDetail ?: return@update state
      if (top.course.id != courseId) return@update state
      state.copy(backStack = state.backStack.dropLast(1) + transform(top))
    }
  }

  private fun updateTopAssignmentPage(
    assignmentId: Long,
    transform: (CanvasSubPage.AssignmentDetail) -> CanvasSubPage.AssignmentDetail,
  ) {
    _uiState.update { state ->
      val top = state.backStack.lastOrNull() as? CanvasSubPage.AssignmentDetail ?: return@update state
      if (top.assignmentId != assignmentId) return@update state
      state.copy(backStack = state.backStack.dropLast(1) + transform(top))
    }
  }

  class Factory(
    private val configStore: CanvasConfigStore,
    private val apiClient: CanvasApiClient,
    private val calendarRepository: CalendarRepository,
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
      CanvasViewModel(configStore, apiClient, calendarRepository) as T
  }
}
