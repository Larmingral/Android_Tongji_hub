package com.example.campushub.ui.canvas

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.campushub.data.auth.TongjiSsoAutoLoginRunner
import com.example.campushub.data.canvas.CanvasCourse
import com.example.campushub.data.canvas.CanvasJsonParser
import com.example.campushub.data.canvas.CanvasTaskItem
import com.example.campushub.data.canvas.CanvasViewMode
import com.example.campushub.ui.auth.TongjiCredentialDialog
import com.example.campushub.ui.auth.TongjiSsoStatusRow
import com.example.campushub.util.HtmlTextCleaner
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CanvasScreen(
  viewModel: CanvasViewModel,
  modifier: Modifier = Modifier,
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()

  BackHandler(enabled = uiState.canNavigateBackInNative) {
    viewModel.navigateBackInNative()
  }

  Column(modifier = modifier.fillMaxSize()) {
    // Top switcher: Native API vs Built-in WebView
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(1f)) {
        SegmentedButton(
          selected = uiState.mode == CanvasViewMode.NATIVE_API,
          onClick = { viewModel.switchMode(CanvasViewMode.NATIVE_API) },
          shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
        ) {
          Text("原生 API 看板")
        }
        SegmentedButton(
          selected = uiState.mode == CanvasViewMode.WEB_VIEW,
          onClick = { viewModel.switchMode(CanvasViewMode.WEB_VIEW) },
          shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
        ) {
          Text("内置免登网页")
        }
      }
    }

    if (uiState.isFileOpening) {
      LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(2.dp))
    }

    when (uiState.mode) {
      CanvasViewMode.NATIVE_API -> {
        when (val subPage = uiState.currentSubPage) {
          is CanvasSubPage.Dashboard -> NativeCanvasDashboard(uiState = uiState, viewModel = viewModel)
          is CanvasSubPage.CourseDetail ->
            NativeCourseDetailPage(page = subPage, statusMessage = uiState.statusMessage, viewModel = viewModel)
          is CanvasSubPage.AssignmentDetail ->
            NativeAssignmentDetailPage(page = subPage, viewModel = viewModel)
        }
      }

      CanvasViewMode.WEB_VIEW ->
        PersistentCanvasWebView(
          homeUrl = uiState.baseUrl,
          targetUrl = uiState.webUrlToLoad,
          reloadNonce = uiState.webReloadNonce,
          buildAutofillJs = viewModel::buildSsoAutofillJsIfSaved,
          onWebLoginLanded = viewModel::onWebLoginLanded,
          modifier = Modifier.weight(1f).fillMaxWidth(),
        )
    }
  }

  if (uiState.showCredentialDialog) {
    TongjiCredentialDialog(
      hasSavedCredentials = uiState.hasSavedCredentials,
      maskedUsername = uiState.maskedUsername,
      onDismiss = viewModel::dismissCredentialDialog,
      onSave = viewModel::saveSsoCredentials,
      onClear = viewModel::clearSsoCredentials,
    )
  }
}

@Composable
private fun NativeCanvasDashboard(
  uiState: CanvasUiState,
  viewModel: CanvasViewModel,
) {
  val groupedTasks =
    remember(uiState.dashboard?.tasks) {
      uiState.dashboard?.tasks?.let { CanvasJsonParser.groupAndSortTasks(it) }
    }

  LazyColumn(
    modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    item {
      Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            val user = uiState.dashboard?.user
            Text(
              text =
                if (user != null) "已连接：${user.name.ifBlank { user.shortName ?: "Canvas" }}"
                else "同济 Canvas（统一认证直连）",
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.SemiBold,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            Text(
              text =
                if (uiState.isAutoLoggingIn) "正在通过同济统一认证自动续期..."
                else if (uiState.hasSavedCredentials) "自动免登已开启（${uiState.maskedUsername ?: "已加密保存"}）"
                else "点击右侧齿轮可保存同济账密自动续期",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
          IconButton(
            onClick = viewModel::connectAndFetch,
            enabled = !uiState.isLoading,
            modifier = Modifier.size(36.dp),
          ) {
            Icon(Icons.Default.Refresh, contentDescription = "刷新 Canvas", modifier = Modifier.size(20.dp))
          }
          IconButton(
            onClick = viewModel::openCredentialDialog,
            modifier = Modifier.size(36.dp),
          ) {
            Icon(Icons.Default.Settings, contentDescription = "统一身份认证自动登录设置", modifier = Modifier.size(20.dp))
          }
        }
      }
    }

    if (uiState.isLoading && uiState.dashboard == null) {
      item {
        Row(
          modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
          horizontalArrangement = Arrangement.Center,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
          Spacer(modifier = Modifier.width(10.dp))
          Text(
            text =
              if (uiState.isAutoLoggingIn) "正在自动续期 Canvas 会话..."
              else "正在同步 Canvas 课程与 DDL...",
            style = MaterialTheme.typography.bodyMedium,
          )
        }
      }
    } else if (uiState.dashboard == null) {
      item {
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
          shape = RoundedCornerShape(14.dp),
        ) {
          Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Text(
              text = "需要同济统一身份认证",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.SemiBold,
            )
            Text(
              text = uiState.errorMessage ?: "无需 Token！保存一次同济学号密码（与教务共用），即可免密自动登录拉取 Canvas 课程与 DDL。",
              style = MaterialTheme.typography.bodyMedium,
              color =
                if (uiState.errorMessage != null) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TongjiSsoStatusRow(
              hasSavedCredentials = uiState.hasSavedCredentials,
              maskedUsername = uiState.maskedUsername,
              onOpenDialog = viewModel::openCredentialDialog,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              Button(
                onClick =
                  if (uiState.hasSavedCredentials) viewModel::connectAndFetch
                  else viewModel::openCredentialDialog,
                enabled = !uiState.isLoading,
                modifier = Modifier.weight(1f),
              ) {
                Text(if (uiState.hasSavedCredentials) "重新拉取数据" else "记住账密免登")
              }
              OutlinedButton(onClick = { viewModel.switchMode(CanvasViewMode.WEB_VIEW) }) {
                Text("内置网页验证")
              }
            }
          }
        }
      }
    } else if (uiState.errorMessage != null) {
      item {
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = MaterialTheme.colorScheme.errorContainer,
        ) {
          Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Text(
              text = uiState.errorMessage,
              color = MaterialTheme.colorScheme.onErrorContainer,
              style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = { viewModel.switchMode(CanvasViewMode.WEB_VIEW) }) {
              Text("打开内置网页验证")
            }
          }
        }
      }
    }

    if (uiState.statusMessage != null) {
      item {
        StatusBanner(
          message = uiState.statusMessage,
          onDismiss = viewModel::clearStatusMessage,
        )
      }
    }

    val dashboard = uiState.dashboard
    if (dashboard != null && groupedTasks != null) {
      val pendingTasks = groupedTasks.pendingWithDue
      val completedTasks = groupedTasks.completedWithDue
      val noDueTasks = groupedTasks.noDueDate

      item {
        Row(
          modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = "待完成 DDL (${pendingTasks.size})",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
          )
          FilledTonalButton(onClick = viewModel::syncTasksToCalendar) {
            Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("同步全部到日历", fontSize = 12.sp)
          }
        }
      }

      if (pendingTasks.isEmpty()) {
        item {
          Text(
            text = "近期暂无带截止日期的未完成任务",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 4.dp),
          )
        }
      } else {
        items(pendingTasks, key = { it.externalId }) { task ->
          CanvasTaskCard(task = task, onClick = { viewModel.openTask(task) })
        }
      }

      if (completedTasks.isNotEmpty()) {
        item {
          CollapsibleTaskHeader(
            title = "已完成 / 已提交 (${completedTasks.size})",
            expanded = uiState.completedTasksExpanded,
            onToggle = viewModel::toggleCompletedTasksExpanded,
          )
        }
        if (uiState.completedTasksExpanded) {
          items(completedTasks, key = { "done_${it.externalId}" }) { task ->
            CanvasTaskCard(task = task, onClick = { viewModel.openTask(task) })
          }
        }
      }

      if (noDueTasks.isNotEmpty()) {
        item {
          CollapsibleTaskHeader(
            title = "无截止日期任务 (${noDueTasks.size})",
            expanded = uiState.noDueTasksExpanded,
            onToggle = viewModel::toggleNoDueTasksExpanded,
          )
        }
        if (uiState.noDueTasksExpanded) {
          items(noDueTasks, key = { "nodue_${it.externalId}" }) { task ->
            CanvasTaskCard(task = task, onClick = { viewModel.openTask(task) })
          }
        }
      }

      item {
        Text(
          text = "本学期课程 (${dashboard.courses.size})",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
          modifier = Modifier.padding(top = 6.dp),
        )
      }

      items(dashboard.courses, key = { it.id }) { course ->
        CanvasCourseCard(
          course = course,
          onClick = { viewModel.openCourse(course) },
        )
      }
    }

    item { Spacer(modifier = Modifier.height(16.dp)) }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NativeCourseDetailPage(
  page: CanvasSubPage.CourseDetail,
  statusMessage: String?,
  viewModel: CanvasViewModel,
) {
  val context = LocalContext.current
  var selectedTab by rememberSaveable(page.course.id) { mutableIntStateOf(0) }
  val tabs = listOf("公告", "作业", "章节与课件")

  Column(modifier = Modifier.fillMaxSize()) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      IconButton(onClick = viewModel::navigateBackInNative) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
      }
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = page.course.name,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        if (!page.course.courseCode.isNullOrBlank()) {
          Text(
            text = page.course.courseCode,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
      TextButton(
        onClick = {
          viewModel.openUrlInWebView(
            "${viewModel.uiState.value.baseUrl}/courses/${page.course.id}"
          )
        }
      ) {
        Text("网页兜底", fontSize = 12.sp)
      }
    }

    if (statusMessage != null) {
      Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
        StatusBanner(message = statusMessage, onDismiss = viewModel::clearStatusMessage)
      }
    }

    PrimaryTabRow(selectedTabIndex = selectedTab) {
      tabs.forEachIndexed { idx, title ->
        Tab(
          selected = selectedTab == idx,
          onClick = { selectedTab = idx },
          text = { Text(title) },
        )
      }
    }

    when {
      page.isLoading -> {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          CircularProgressIndicator()
        }
      }
      page.errorMessage != null -> {
        Column(
          modifier = Modifier.fillMaxSize().padding(16.dp),
          verticalArrangement = Arrangement.Center,
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Text(page.errorMessage, color = MaterialTheme.colorScheme.error)
          Spacer(modifier = Modifier.height(8.dp))
          OutlinedButton(
            onClick = {
              viewModel.openUrlInWebView(
                "${viewModel.uiState.value.baseUrl}/courses/${page.course.id}"
              )
            }
          ) {
            Text("在内置网页中打开")
          }
        }
      }
      page.data != null -> {
        val data = page.data
        LazyColumn(
          modifier = Modifier.fillMaxSize(),
          contentPadding = PaddingValues(12.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          when (selectedTab) {
            0 -> {
              if (data.announcements.isEmpty()) {
                item { EmptyHint("暂无课程公告") }
              } else {
                items(data.announcements, key = { it.id }) { ann ->
                  Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                  ) {
                    Column(
                      modifier = Modifier.fillMaxWidth().padding(12.dp),
                      verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                      Text(
                        text = ann.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                      )
                      val meta = listOf(ann.authorName, ann.postedAtText).filter { it.isNotBlank() }
                      if (meta.isNotEmpty()) {
                        Text(
                          text = meta.joinToString(" · "),
                          style = MaterialTheme.typography.bodySmall,
                          color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                      }
                      SelectionContainer {
                        Text(
                          text = ann.bodyText,
                          style = MaterialTheme.typography.bodyMedium,
                        )
                      }
                    }
                  }
                }
              }
            }

            1 -> {
              if (data.assignments.isEmpty()) {
                item { EmptyHint("暂无课程作业") }
              } else {
                items(data.assignments, key = { it.externalId }) { assign ->
                  CanvasTaskCard(
                    task = assign,
                    onClick = { viewModel.openTask(assign) },
                  )
                }
              }
            }

            2 -> {
              if (data.modules.isEmpty() && data.files.isEmpty()) {
                item { EmptyHint("当前课程暂无可见章节或公开文件") }
              } else {
                items(data.modules, key = { it.id }) { mod ->
                  Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                  ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                      Text(
                        text = mod.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                      )
                      Spacer(modifier = Modifier.height(6.dp))
                      mod.items.forEachIndexed { idx, item ->
                        if (idx > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        Row(
                          modifier =
                            Modifier.fillMaxWidth()
                              .clickable {
                                viewModel.openModuleItem(context, page.course, item, data.files)
                              }
                              .padding(vertical = 6.dp),
                          verticalAlignment = Alignment.CenterVertically,
                        ) {
                          Text(
                            text = item.type,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier =
                              Modifier.clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                          )
                          Spacer(modifier = Modifier.width(8.dp))
                          Text(
                            text = item.title,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                          )
                        }
                      }
                    }
                  }
                }

                if (data.files.isNotEmpty()) {
                  item {
                    Text(
                      text = "课程文件 (${data.files.size})",
                      style = MaterialTheme.typography.titleSmall,
                      fontWeight = FontWeight.SemiBold,
                      modifier = Modifier.padding(top = 4.dp),
                    )
                  }
                  items(data.files, key = { it.id }) { file ->
                    Surface(
                      shape = RoundedCornerShape(10.dp),
                      border =
                        BorderStroke(
                          1.dp,
                          MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        ),
                      modifier =
                        Modifier.fillMaxWidth().clickable { viewModel.openCourseFile(context, file) },
                    ) {
                      Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                      ) {
                        Column(modifier = Modifier.weight(1f)) {
                          Text(
                            text = file.displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                          )
                          Text(
                            text = HtmlTextCleaner.formatFileSize(file.sizeBytes),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                          )
                        }
                        Text(
                          text = "下载打开",
                          style = MaterialTheme.typography.labelMedium,
                          color = MaterialTheme.colorScheme.primary,
                        )
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun NativeAssignmentDetailPage(
  page: CanvasSubPage.AssignmentDetail,
  viewModel: CanvasViewModel,
) {
  Column(modifier = Modifier.fillMaxSize()) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      IconButton(onClick = viewModel::navigateBackInNative) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
      }
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = page.data?.title ?: page.fallbackTitle,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = page.courseName,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    when {
      page.isLoading -> {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          CircularProgressIndicator()
        }
      }
      page.errorMessage != null -> {
        Column(
          modifier = Modifier.fillMaxSize().padding(16.dp),
          verticalArrangement = Arrangement.Center,
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Text(page.errorMessage, color = MaterialTheme.colorScheme.error)
          val url = page.fallbackHtmlUrl
          if (!url.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(onClick = { viewModel.openUrlInWebView(url) }) {
              Text("在内置网页中打开")
            }
          }
        }
      }
      page.data != null -> {
        val detail = page.data
        LazyColumn(
          modifier = Modifier.fillMaxSize(),
          contentPadding = PaddingValues(14.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          item {
            Surface(
              shape = RoundedCornerShape(14.dp),
              color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
              Column(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                val statusLabel =
                  when {
                    detail.score != null -> "已评分：${detail.score} / ${detail.pointsPossible ?: "-"}"
                    detail.isSubmitted -> "已提交"
                    detail.isMissing -> "已缺交"
                    else -> "未提交"
                  }
                Text(
                  text = statusLabel,
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.Bold,
                  color =
                    if (detail.isSubmitted) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error,
                )
                Text("截止时间：${detail.dueDateText}", style = MaterialTheme.typography.bodyMedium)
                if (detail.pointsPossible != null) {
                  Text("满分：${detail.pointsPossible}", style = MaterialTheme.typography.bodyMedium)
                }
                if (detail.submissionTypes.isNotEmpty()) {
                  Text(
                    "提交方式：${detail.submissionTypes.joinToString(", ")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
            }
          }

          item {
            Text(
              text = "作业要求",
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Surface(
              shape = RoundedCornerShape(12.dp),
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            ) {
              SelectionContainer {
                Text(
                  text = detail.descriptionText,
                  style = MaterialTheme.typography.bodyMedium,
                  modifier = Modifier.fillMaxWidth().padding(14.dp),
                )
              }
            }
          }

          if (detail.comments.isNotEmpty()) {
            item {
              Text(
                text = "教师/助教评语",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
              )
              Spacer(modifier = Modifier.height(6.dp))
              detail.comments.forEach { comment ->
                Surface(
                  shape = RoundedCornerShape(10.dp),
                  color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                  modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                ) {
                  Text(
                    text = comment,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(12.dp),
                  )
                }
              }
            }
          }

          val url = detail.htmlUrl ?: page.fallbackHtmlUrl
          if (!url.isNullOrBlank()) {
            item {
              OutlinedButton(
                onClick = { viewModel.openUrlInWebView(url) },
                modifier = Modifier.fillMaxWidth(),
              ) {
                Text("在内置网页中打开（用于提交作业 / 在线测验）")
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun CollapsibleTaskHeader(
  title: String,
  expanded: Boolean,
  onToggle: () -> Unit,
) {
  Surface(
    shape = RoundedCornerShape(10.dp),
    color = MaterialTheme.colorScheme.surfaceContainerLow,
    modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.weight(1f),
      )
      Icon(
        imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
        contentDescription = "展开或收起",
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun CanvasTaskCard(
  task: CanvasTaskItem,
  onClick: () -> Unit,
) {
  val today = LocalDate.now()
  val dueLabel =
    when (val d = task.dueDate) {
      null -> "无截止日期"
      else -> {
        val diff = ChronoUnit.DAYS.between(today, d)
        val relative =
          when {
            task.isSubmitted -> "已完成"
            diff < 0 -> "已逾期 ${-diff} 天"
            diff == 0L -> "今天截止"
            diff == 1L -> "明天截止"
            else -> "剩 $diff 天"
          }
        "${d.monthValue}/${d.dayOfMonth} ${task.dueTimeText} · $relative".trim()
      }
    }

  val scoreText =
    when {
      task.score != null && task.pointsPossible != null -> " · 得分 ${task.score}/${task.pointsPossible}"
      task.score != null -> " · 得分 ${task.score}"
      else -> ""
    }

  Surface(
    shape = RoundedCornerShape(12.dp),
    color = MaterialTheme.colorScheme.surface,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    modifier =
      Modifier.fillMaxWidth()
        .clickable(
          enabled = (task.courseId != null && task.assignmentId != null) || task.htmlUrl != null,
          onClick = onClick,
        ),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
          text = task.title,
          style = MaterialTheme.typography.bodyLarge,
          fontWeight = FontWeight.Medium,
          textDecoration = if (task.isSubmitted) TextDecoration.LineThrough else TextDecoration.None,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = task.courseName,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = "$dueLabel$scoreText",
          style = MaterialTheme.typography.labelMedium,
          color =
            if (task.isSubmitted) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.error,
        )
      }
    }
  }
}

@Composable
private fun CanvasCourseCard(
  course: CanvasCourse,
  onClick: () -> Unit,
) {
  Surface(
    shape = RoundedCornerShape(12.dp),
    color = MaterialTheme.colorScheme.surfaceContainerLow,
    modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
  ) {
    Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
      Text(
        text = course.name,
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.Medium,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
      if (!course.courseCode.isNullOrBlank()) {
        Text(
          text = course.courseCode,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

@Composable
private fun StatusBanner(message: String, onDismiss: () -> Unit) {
  Surface(
    shape = RoundedCornerShape(12.dp),
    color = MaterialTheme.colorScheme.secondaryContainer,
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
        Icons.Default.CheckCircle,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.size(18.dp),
      )
      Spacer(modifier = Modifier.width(8.dp))
      Text(
        text = message,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.weight(1f),
      )
      TextButton(onClick = onDismiss) { Text("知道了") }
    }
  }
}

@Composable
private fun EmptyHint(text: String) {
  Box(
    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = text,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun PersistentCanvasWebView(
  homeUrl: String,
  targetUrl: String,
  reloadNonce: Int,
  buildAutofillJs: () -> String?,
  onWebLoginLanded: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  var webViewRef by remember { mutableStateOf<WebView?>(null) }
  var progress by remember { mutableIntStateOf(0) }
  var canGoBack by remember { mutableStateOf(false) }
  var canGoForward by remember { mutableStateOf(false) }
  var currentTitle by remember { mutableStateOf("Canvas") }
  var lastRequestedKey by remember { mutableStateOf<Pair<String, Int>?>(null) }

  BackHandler(enabled = canGoBack) { webViewRef?.goBack() }

  DisposableEffect(Unit) {
    onDispose { CookieManager.getInstance().flush() }
  }

  Column(modifier = modifier) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      IconButton(onClick = { webViewRef?.goBack() }, enabled = canGoBack) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "后退")
      }
      IconButton(onClick = { webViewRef?.goForward() }, enabled = canGoForward) {
        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "前进")
      }
      IconButton(onClick = { webViewRef?.reload() }) {
        Icon(Icons.Default.Refresh, contentDescription = "刷新")
      }
      Text(
        text = currentTitle,
        style = MaterialTheme.typography.bodySmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
      )
      IconButton(onClick = { webViewRef?.loadUrl(homeUrl) }) {
        Icon(Icons.Default.Home, contentDescription = "回到 Canvas 首页")
      }
      IconButton(
        onClick = {
          val cm = CookieManager.getInstance()
          cm.removeAllCookies {
            cm.flush()
            webViewRef?.clearCache(true)
            webViewRef?.loadUrl(homeUrl)
          }
        }
      ) {
        Icon(Icons.Default.Delete, contentDescription = "清除登录 Cookie")
      }
    }

    if (progress in 1..99) {
      LinearProgressIndicator(
        progress = { progress / 100f },
        modifier = Modifier.fillMaxWidth().height(2.dp),
      )
    }

    AndroidView(
      modifier = Modifier.weight(1f).fillMaxWidth(),
      factory = { context ->
        WebView(context).apply {
          val cm = CookieManager.getInstance()
          cm.setAcceptCookie(true)
          cm.setAcceptThirdPartyCookies(this, true)

          settings.javaScriptEnabled = true
          settings.domStorageEnabled = true
          settings.useWideViewPort = true
          settings.loadWithOverviewMode = true
          settings.setSupportZoom(true)
          settings.builtInZoomControls = true
          settings.displayZoomControls = false

          webViewClient =
            object : WebViewClient() {
              override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                canGoBack = view?.canGoBack() == true
                canGoForward = view?.canGoForward() == true
              }

              override fun onPageFinished(view: WebView?, url: String?) {
                canGoBack = view?.canGoBack() == true
                canGoForward = view?.canGoForward() == true
                currentTitle = view?.title?.ifBlank { "Canvas" } ?: "Canvas"
                CookieManager.getInstance().flush()
                if (TongjiSsoAutoLoginRunner.isSsoHost(url)) {
                  buildAutofillJs()?.let { js ->
                    view?.evaluateJavascript(js, null)
                  }
                } else if (!url.isNullOrBlank()) {
                  onWebLoginLanded(url)
                }
              }
            }

          webChromeClient =
            object : WebChromeClient() {
              override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progress = newProgress
              }

              override fun onReceivedTitle(view: WebView?, title: String?) {
                if (!title.isNullOrBlank()) currentTitle = title
              }
            }

          webViewRef = this
          lastRequestedKey = targetUrl to reloadNonce
          loadUrl(targetUrl)
        }
      },
      update = { webView ->
        webViewRef = webView
        val key = targetUrl to reloadNonce
        if (lastRequestedKey != key) {
          lastRequestedKey = key
          webView.loadUrl(targetUrl)
        }
      },
    )
  }
}
