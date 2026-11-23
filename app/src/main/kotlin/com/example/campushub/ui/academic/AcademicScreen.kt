package com.example.campushub.ui.academic

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.text.TextUtils
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.campushub.data.academic.AcademicAttachment
import com.example.campushub.data.academic.AcademicNotice
import com.example.campushub.data.academic.ClassPeriodTime
import com.example.campushub.data.academic.ExamEntry
import com.example.campushub.data.academic.GradeTermSummary
import com.example.campushub.data.academic.TimetableEntry
import com.example.campushub.data.auth.TongjiSsoAutoLoginRunner
import com.example.campushub.ui.auth.TongjiCredentialDialog
import com.example.campushub.ui.auth.TongjiSsoStatusRow
import kotlin.math.abs

private val COURSE_BG_COLORS =
  listOf(
    Color(0xFFD6E6FF),
    Color(0xFFD3F2DC),
    Color(0xFFFFE4CC),
    Color(0xFFEDE0FF),
    Color(0xFFFFDAD6),
    Color(0xFFCFF3F8),
  )

private val COURSE_TEXT_COLORS =
  listOf(
    Color(0xFF0A3678),
    Color(0xFF0B4E20),
    Color(0xFF6B3400),
    Color(0xFF420D5E),
    Color(0xFF7A140F),
    Color(0xFF064B57),
  )

private val DAY_LABELS = listOf("一", "二", "三", "四", "五", "六", "日")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AcademicScreen(
  viewModel: AcademicViewModel,
  modifier: Modifier = Modifier,
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()

  Column(modifier = modifier.fillMaxSize()) {
    if (uiState.showWebFallback) {
      EmergencyAcademicWebView(uiState = uiState, viewModel = viewModel)
    } else {
      NativeAcademicView(uiState = uiState, viewModel = viewModel)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NativeAcademicView(
  uiState: AcademicUiState,
  viewModel: AcademicViewModel,
) {
  val dash = uiState.dashboard

  Column(modifier = Modifier.fillMaxSize()) {
    // Compact single top row: Student Info + Refresh + Settings
    Surface(
      color = MaterialTheme.colorScheme.surfaceContainerLow,
      shape = RoundedCornerShape(12.dp),
      modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          val userLine =
            if (dash != null) {
              listOf(dash.session.studentName, dash.session.majorName.ifBlank { dash.session.facultyName })
                .filter { it.isNotBlank() }
                .joinToString(" · ")
                .ifBlank { "同济教务已连接" }
            } else {
              "同济教务"
            }
          val subLine =
            if (dash != null) {
              "${dash.session.currentTermName} · 第 ${dash.session.currentWeek} 周"
            } else if (uiState.hasSavedCredentials) {
              "已开启自动免登（${uiState.maskedUsername ?: "已保存"}）"
            } else {
              "点击右侧齿轮可保存账密自动免登"
            }
          Text(
            text = userLine,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text = subLine,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        IconButton(
          onClick = viewModel::refreshDashboard,
          enabled = !uiState.isLoading,
          modifier = Modifier.size(36.dp),
        ) {
          Icon(Icons.Default.Refresh, contentDescription = "刷新教务数据", modifier = Modifier.size(20.dp))
        }
        IconButton(
          onClick = viewModel::openCredentialDialog,
          modifier = Modifier.size(36.dp),
        ) {
          Icon(Icons.Default.Settings, contentDescription = "统一身份认证自动登录设置", modifier = Modifier.size(20.dp))
        }
      }
    }

    if (uiState.isLoading && dash == null) {
      Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          CircularProgressIndicator()
          Spacer(modifier = Modifier.height(10.dp))
          Text(
            text =
              if (uiState.isAutoLoggingIn) "登录态已过期，正在后台自动续期同济统一身份认证..."
              else "正在同步同济教务数据...",
            style = MaterialTheme.typography.bodyMedium,
          )
        }
      }
      return
    }

    if (uiState.needsWebLogin || (dash == null && uiState.errorMessage != null)) {
      Box(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        contentAlignment = Alignment.Center,
      ) {
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
          shape = RoundedCornerShape(16.dp),
        ) {
          Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            Text(
              text = "需要同济统一身份认证",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
            )
            Text(
              text = uiState.errorMessage ?: "请先保存账密开启自动续期，或在“网页登录(免登)”完成一次登录。",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              textAlign = TextAlign.Center,
            )
            TongjiSsoStatusRow(
              hasSavedCredentials = uiState.hasSavedCredentials,
              maskedUsername = uiState.maskedUsername,
              onOpenDialog = viewModel::openCredentialDialog,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
              Button(onClick = viewModel::openCredentialDialog) {
                Text(if (uiState.hasSavedCredentials) "重设自动登录" else "记住账密免登")
              }
              OutlinedButton(onClick = { viewModel.openWebFallback() }) {
                Text("网页验证")
              }
            }
            TextButton(onClick = viewModel::refreshDashboard) {
              Text("重试加载")
            }
          }
        }
      }
      return
    }

    if (uiState.isLoading) {
      LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(2.dp))
    }

    // 4 sub-section tabs
    PrimaryTabRow(selectedTabIndex = uiState.activeSection.ordinal) {
      AcademicSection.entries.forEach { sec ->
        Tab(
          selected = uiState.activeSection == sec,
          onClick = { viewModel.selectSection(sec) },
          text = { Text(sec.label) },
        )
      }
    }

    if (dash != null) {
      when (uiState.activeSection) {
        AcademicSection.NOTICES -> NoticesSection(notices = dash.notices, onOpenNotice = viewModel::openNotice)
        AcademicSection.TIMETABLE -> TimetableSection(uiState = uiState, viewModel = viewModel)
        AcademicSection.EXAMS -> ExamsSection(uiState = uiState)
        AcademicSection.GRADES -> GradesSection(uiState = uiState)
      }
    }
  }

  val notice = uiState.selectedNotice
  if (notice != null) {
    NoticeDetailDialog(
      notice = notice,
      isLoadingDetail = uiState.isNoticeDetailLoading,
      detailError = uiState.noticeDetailError,
      isAttachmentOpening = uiState.isAttachmentOpening,
      attachmentStatus = uiState.attachmentStatus,
      onOpenLink = viewModel::openNoticeLink,
      onOpenAttachment = viewModel::openAttachment,
      onDismiss = viewModel::dismissNotice,
    )
  }

  val slot = uiState.selectedCourseSlot
  if (slot != null) {
    CourseSlotDialog(
      entry = slot,
      periodTimes = uiState.activePeriods,
      onDismiss = viewModel::dismissCourseSlot,
    )
  }
}

@Composable
private fun NoticesSection(
  notices: List<AcademicNotice>,
  onOpenNotice: (AcademicNotice) -> Unit,
) {
  if (notices.isEmpty()) {
    CenteredNotice("暂无通知公告")
    return
  }

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(12.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    items(notices, key = { it.id }) { notice ->
      Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth().clickable { onOpenNotice(notice) },
      ) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(12.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            if (notice.isPinned) {
              Text(
                text = "置顶",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier =
                  Modifier.clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = 5.dp, vertical = 1.dp),
              )
            }
            Text(
              text = notice.title,
              style = MaterialTheme.typography.bodyLarge,
              fontWeight = FontWeight.Medium,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
            )
          }
          val meta = listOf(notice.publisher, notice.publishTime).filter { it.isNotBlank() }
          if (meta.isNotEmpty()) {
            Text(
              text = meta.joinToString(" · "),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun TimetableSection(
  uiState: AcademicUiState,
  viewModel: AcademicViewModel,
) {
  val allEntries = uiState.activeTimetable
  val weekEntries =
    remember(allEntries, uiState.selectedWeek) {
      allEntries.filter { entry ->
        entry.weeks.isEmpty() || uiState.selectedWeek in entry.weeks
      }
    }

  var showYearMenu by remember { mutableStateOf(false) }
  val currentSession = uiState.dashboard?.session
  val isCurrentWeek =
    currentSession != null &&
      uiState.selectedCalendarId == currentSession.currentCalendarId &&
      uiState.selectedWeek == currentSession.currentWeek

  val years = uiState.distinctYears
  val yearTerms = uiState.termsInSelectedYear

  Column(modifier = Modifier.fillMaxSize()) {
    // Year dropdown + Binary Term toggle + Week stepper (Grid-only view)
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      if (years.isNotEmpty()) {
        Box {
          OutlinedButton(
            onClick = { showYearMenu = true },
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
            modifier = Modifier.height(30.dp),
          ) {
            Text(
              text = uiState.selectedYearLabel.removeSuffix("学年").ifBlank { "学年" },
              fontSize = 11.sp,
              maxLines = 1,
            )
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "选择学年", modifier = Modifier.size(15.dp))
          }
          DropdownMenu(expanded = showYearMenu, onDismissRequest = { showYearMenu = false }) {
            years.forEach { year ->
              DropdownMenuItem(
                text = { Text(year) },
                onClick = {
                  showYearMenu = false
                  viewModel.selectYear(year)
                },
              )
            }
          }
        }
        Spacer(modifier = Modifier.width(6.dp))
      }

      if (yearTerms.isNotEmpty()) {
        Surface(
          shape = RoundedCornerShape(8.dp),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
          color = MaterialTheme.colorScheme.surface,
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            yearTerms.forEachIndexed { index, term ->
              if (index > 0) {
                Box(
                  modifier =
                    Modifier.width(1.dp)
                      .height(18.dp)
                      .background(MaterialTheme.colorScheme.outlineVariant)
                )
              }
              val selected = term.id == uiState.selectedCalendarId
              Box(
                modifier =
                  Modifier.background(
                    if (selected) MaterialTheme.colorScheme.primaryContainer
                    else Color.Transparent
                  )
                    .clickable { viewModel.selectTerm(term) }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
              ) {
                Text(
                  text = term.termLabel.ifBlank { term.fullName },
                  fontSize = 11.sp,
                  fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                  color =
                    if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                  maxLines = 1,
                )
              }
            }
          }
        }
      }

      Spacer(modifier = Modifier.weight(1f))

      IconButton(onClick = viewModel::previousWeek, modifier = Modifier.size(28.dp)) {
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "上一周")
      }
      Text(
        text = if (isCurrentWeek) "第${uiState.selectedWeek}周(本周)" else "第${uiState.selectedWeek}周",
        fontSize = 11.5.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clickable(onClick = viewModel::jumpToCurrentWeek),
      )
      IconButton(onClick = viewModel::nextWeek, modifier = Modifier.size(28.dp)) {
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "下一周")
      }
    }

    if (uiState.isSwitchingTerm) {
      LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(2.dp))
    }

    val timetableError = uiState.activeTimetableError
    if (timetableError != null) {
      Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Text(
          text = timetableError,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.error,
          textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Button(onClick = viewModel::retryCurrentTimetable) { Text("重拉课表") }
          OutlinedButton(onClick = { viewModel.openWebFallback(timetableError) }) {
            Text("网页验证")
          }
        }
      }
      return
    }

    if (weekEntries.isEmpty()) {
      CenteredNotice("第 ${uiState.selectedWeek} 周暂无课程安排")
      return
    }

    TimetableGrid(
      entries = weekEntries,
      periodTimes = uiState.activePeriods,
      onSlotClick = viewModel::openCourseSlot,
    )
  }
}

@Composable
private fun TimetableGrid(
  entries: List<TimetableEntry>,
  periodTimes: Map<Int, ClassPeriodTime>,
  onSlotClick: (TimetableEntry) -> Unit,
) {
  val hasWeekend = entries.any { it.dayOfWeek > 5 }
  val dayCount = if (hasWeekend) 7 else 5
  val maxPeriod = maxOf(11, entries.maxOfOrNull { it.timeEnd } ?: 11, periodTimes.keys.maxOrNull() ?: 11)
  val slotHeight = 48.dp
  val gridHeight = slotHeight * maxPeriod

  Column(modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp)) {
    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
      Spacer(modifier = Modifier.width(34.dp))
      for (d in 1..dayCount) {
        Text(
          text = "周${DAY_LABELS[d - 1]}",
          modifier = Modifier.weight(1f),
          textAlign = TextAlign.Center,
          style = MaterialTheme.typography.labelMedium,
          fontWeight = FontWeight.Medium,
        )
      }
    }

    Row(
      modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
    ) {
      // Period index column
      Column(modifier = Modifier.width(34.dp)) {
        for (p in 1..maxPeriod) {
          val period = periodTimes[p]
          Column(
            modifier =
              Modifier.fillMaxWidth()
                .height(slotHeight)
                .border(0.4.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
          ) {
            Text(text = p.toString(), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            if (period != null && period.beginTime.isNotBlank()) {
              Text(
                text = period.beginTime,
                fontSize = 8.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }

      // Day columns
      for (day in 1..dayCount) {
        val dayItems = entries.filter { it.dayOfWeek == day }
        Box(modifier = Modifier.weight(1f).height(gridHeight)) {
          // Grid background rows
          Column(modifier = Modifier.fillMaxSize()) {
            for (p in 1..maxPeriod) {
              Box(
                modifier =
                  Modifier.fillMaxWidth()
                    .height(slotHeight)
                    .border(0.4.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
              )
            }
          }

          // Course blocks
          dayItems.forEach { entry ->
            val topOffset = slotHeight * (entry.timeStart - 1) + 1.dp
            val blockHeight = slotHeight * (entry.timeEnd - entry.timeStart + 1) - 2.dp
            val colorIdx = abs(entry.courseName.hashCode()) % COURSE_BG_COLORS.size

            Column(
              modifier =
                Modifier.fillMaxWidth()
                  .padding(horizontal = 1.5.dp)
                  .offset(y = topOffset)
                  .height(blockHeight)
                  .clip(RoundedCornerShape(6.dp))
                  .background(COURSE_BG_COLORS[colorIdx])
                  .clickable { onSlotClick(entry) }
                  .padding(horizontal = 3.dp, vertical = 3.dp),
              verticalArrangement = Arrangement.SpaceBetween,
            ) {
              Text(
                text = entry.courseName,
                fontSize = 10.sp,
                lineHeight = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = COURSE_TEXT_COLORS[colorIdx],
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
              )
              if (entry.roomLabel.isNotBlank()) {
                Text(
                  text = entry.roomLabel,
                  fontSize = 8.5.sp,
                  lineHeight = 10.sp,
                  color = COURSE_TEXT_COLORS[colorIdx].copy(alpha = 0.85f),
                  maxLines = 2,
                  overflow = TextOverflow.Ellipsis,
                )
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun ExamsSection(uiState: AcademicUiState) {
  val examResult = uiState.dashboard?.examResult
  if (examResult == null || examResult.exams.isEmpty()) {
    CenteredNotice(examResult?.statusMessage ?: "当前学期暂无考试安排")
    return
  }

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(12.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    items(examResult.exams) { exam: ExamEntry ->
      Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
      ) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(12.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Text(
            text = exam.courseName,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
          )
          if (exam.examTime.isNotBlank()) {
            Text(
              text = "时间：${exam.examTime}",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.error,
            )
          }
          val loc = listOf(exam.campus, exam.examRoom, exam.seatNo.takeIf { it.isNotBlank() }?.let { "座位 $it" })
            .filterNotNull()
            .filter { it.isNotBlank() }
          if (loc.isNotEmpty()) {
            Text(
              text = "地点：${loc.joinToString(" · ")}",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          if (exam.remark.isNotBlank()) {
            Text(
              text = exam.remark,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun GradesSection(uiState: AcademicUiState) {
  val grades = uiState.dashboard?.grades
  if (grades == null) {
    CenteredNotice("暂未获取到成绩单数据")
    return
  }

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(12.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    item {
      Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(14.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(16.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          GradeStatColumn("总平均绩点", grades.totalGradePoint)
          GradeStatColumn("实得学分", grades.actualCredit)
          GradeStatColumn("不及格门数", grades.failingCourseCount)
        }
      }
    }

    items(grades.terms) { term: GradeTermSummary ->
      Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
      ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = term.termName,
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
              modifier = Modifier.weight(1f),
            )
            if (term.averagePoint.isNotBlank()) {
              Text(
                text = "均绩 ${term.averagePoint}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
              )
            }
          }

          if (term.courses.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            term.courses.forEachIndexed { idx, course ->
              if (idx > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = course.courseName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                  )
                  val sub = listOf(course.courseCode, course.credits.takeIf { it.isNotBlank() }?.let { "${it}学分" }, course.courseType)
                    .filterNotNull()
                    .filter { it.isNotBlank() }
                  if (sub.isNotEmpty()) {
                    Text(
                      text = sub.joinToString(" · "),
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                }
                Column(horizontalAlignment = Alignment.End) {
                  Text(
                    text = course.score.ifBlank { "-" },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color =
                      if (course.isPassed == false) MaterialTheme.colorScheme.error
                      else MaterialTheme.colorScheme.onSurface,
                  )
                  if (course.gradePoint.isNotBlank()) {
                    Text(
                      text = "绩点 ${course.gradePoint}",
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
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

@Composable
private fun GradeStatColumn(label: String, value: String) {
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Text(
      text = value,
      style = MaterialTheme.typography.headlineSmall,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colorScheme.onPrimaryContainer,
    )
    Text(
      text = label,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
    )
  }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun NoticeDetailDialog(
  notice: AcademicNotice,
  isLoadingDetail: Boolean,
  detailError: String?,
  isAttachmentOpening: Boolean,
  attachmentStatus: String?,
  onOpenLink: (android.content.Context, String) -> Unit,
  onOpenAttachment: (android.content.Context, AcademicAttachment) -> Unit,
  onDismiss: () -> Unit,
) {
  val context = LocalContext.current
  val textColorHex = String.format("#%06X", 0xFFFFFF and MaterialTheme.colorScheme.onSurface.toArgb())
  val linkColorHex = String.format("#%06X", 0xFFFFFF and MaterialTheme.colorScheme.primary.toArgb())
  val borderColorHex = String.format("#%06X", 0xFFFFFF and MaterialTheme.colorScheme.outlineVariant.toArgb())

  val htmlPayload =
    remember(notice.id, notice.contentHtml, notice.contentText, textColorHex, linkColorHex, borderColorHex) {
      val inner =
        notice.contentHtml.trim().ifBlank {
          "<p>${TextUtils.htmlEncode(notice.contentText).replace("\n", "<br/>")}</p>"
        }
      """
      <!doctype html>
      <html>
      <head>
        <meta charset="utf-8"/>
        <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=3.0"/>
        <style>
          html, body {
            margin: 0;
            padding: 0;
            background: transparent;
            color: $textColorHex;
            font-size: 14.5px;
            line-height: 1.65;
            word-break: break-word;
            overflow-wrap: anywhere;
          }
          a { color: $linkColorHex; text-decoration: underline; }
          img { max-width: 100%; height: auto; }
          table {
            width: 100% !important;
            max-width: 100%;
            border-collapse: collapse;
            font-size: 13px;
            display: block;
            overflow-x: auto;
          }
          td, th { border: 1px solid $borderColorHex; padding: 4px 6px; }
          p { margin: 0 0 8px 0; }
        </style>
      </head>
      <body>$inner</body>
      </html>
      """.trimIndent()
    }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Surface(
      shape = RoundedCornerShape(18.dp),
      color = MaterialTheme.colorScheme.surfaceContainerHigh,
      modifier = Modifier.fillMaxWidth(0.94f).heightIn(max = 680.dp),
    ) {
      Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Text(
          text = notice.title,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
        )
        val meta = listOf(notice.publisher, notice.publishTime).filter { it.isNotBlank() }
        if (meta.isNotEmpty()) {
          Text(
            text = meta.joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }

        if (isLoadingDetail || isAttachmentOpening) {
          LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(2.dp))
        }

        if (detailError != null) {
          Text(
            text = detailError,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
          )
        } else {
          val contentTag = notice.id to htmlPayload
          AndroidView(
            modifier = Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(min = 140.dp, max = 380.dp),
            factory = { ctx ->
              WebView(ctx).apply {
                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                settings.javaScriptEnabled = false
                settings.useWideViewPort = false
                settings.loadWithOverviewMode = true
                settings.defaultTextEncodingName = "utf-8"
                webViewClient =
                  object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                      view: WebView?,
                      request: WebResourceRequest?,
                    ): Boolean {
                      val target = request?.url?.toString().orEmpty()
                      if (target.isNotBlank()) {
                        onOpenLink(context, target)
                      }
                      return true
                    }
                  }
                tag = contentTag
                loadDataWithBaseURL("https://1.tongji.edu.cn/", htmlPayload, "text/html", "utf-8", null)
              }
            },
            update = { webView ->
              if (webView.tag != contentTag) {
                webView.tag = contentTag
                webView.loadDataWithBaseURL("https://1.tongji.edu.cn/", htmlPayload, "text/html", "utf-8", null)
              }
            },
          )
        }

        if (notice.attachments.isNotEmpty()) {
          HorizontalDivider()
          Text(
            text = "公告附件 (${notice.attachments.size}) · 点击下载打开",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
          )
          Column(
            modifier = Modifier.fillMaxWidth().heightIn(max = 150.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            notice.attachments.forEach { att ->
              Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                modifier =
                  Modifier.fillMaxWidth()
                    .clickable(enabled = !isAttachmentOpening) { onOpenAttachment(context, att) },
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Text(
                    text = att.fileName,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                  )
                  Spacer(modifier = Modifier.width(8.dp))
                  Text(
                    text = if (isAttachmentOpening) "处理中" else "下载",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                  )
                }
              }
            }
          }
        }

        if (attachmentStatus != null) {
          Text(
            text = attachmentStatus,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
          )
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
          TextButton(onClick = onDismiss) { Text("关闭") }
        }
      }
    }
  }
}

@Composable
private fun CourseSlotDialog(
  entry: TimetableEntry,
  periodTimes: Map<Int, ClassPeriodTime>,
  onDismiss: () -> Unit,
) {
  val start = periodTimes[entry.timeStart]?.beginTime.orEmpty()
  val end = periodTimes[entry.timeEnd]?.endTime.orEmpty()
  val periodText =
    if (start.isNotBlank() && end.isNotBlank()) {
      "周${DAY_LABELS.getOrElse(entry.dayOfWeek - 1) { "" }} 第${entry.timeStart}-${entry.timeEnd}节 ($start - $end)"
    } else {
      "周${DAY_LABELS.getOrElse(entry.dayOfWeek - 1) { "" }} 第${entry.timeStart}-${entry.timeEnd}节"
    }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(entry.courseName) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (entry.courseCode.isNotBlank()) Text("课程代码：${entry.courseCode}")
        Text("上课时间：$periodText")
        if (entry.weekStr.isNotBlank()) Text("上课周次：${entry.weekStr}")
        if (entry.roomLabel.isNotBlank()) Text("上课地点：${entry.roomLabel}")
        if (entry.campus.isNotBlank()) Text("校区：${entry.campus}")
        if (entry.teacherName.isNotBlank()) Text("任课教师：${entry.teacherName}")
        if (entry.credits != null) Text("学分：${entry.credits}")
        if (entry.compulsory.isNotBlank()) Text("课程属性：${entry.compulsory}")
        if (entry.assessmentMode.isNotBlank()) Text("考核方式：${entry.assessmentMode}")
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) { Text("关闭") }
    },
  )
}

@Composable
private fun CenteredNotice(text: String) {
  Box(
    modifier = Modifier.fillMaxSize().padding(24.dp),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = text,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
    )
  }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun EmergencyAcademicWebView(
  uiState: AcademicUiState,
  viewModel: AcademicViewModel,
) {
  var webViewRef by remember { mutableStateOf<WebView?>(null) }
  var progress by remember { mutableIntStateOf(0) }
  var canGoBack by remember { mutableStateOf(false) }
  var currentTitle by remember { mutableStateOf("同济统一身份认证") }

  BackHandler(enabled = true) {
    if (canGoBack) {
      webViewRef?.goBack()
    } else {
      viewModel.closeWebFallback()
    }
  }

  DisposableEffect(Unit) {
    onDispose { CookieManager.getInstance().flush() }
  }

  Column(modifier = Modifier.fillMaxSize()) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      TextButton(onClick = viewModel::closeWebFallback) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(2.dp))
        Text("返回原生", fontSize = 12.sp)
      }
      Text(
        text = currentTitle,
        style = MaterialTheme.typography.bodySmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
      )
      IconButton(onClick = { webViewRef?.reload() }, modifier = Modifier.size(34.dp)) {
        Icon(Icons.Default.Refresh, contentDescription = "刷新", modifier = Modifier.size(18.dp))
      }
      IconButton(onClick = { webViewRef?.loadUrl(uiState.homeUrl) }, modifier = Modifier.size(34.dp)) {
        Icon(Icons.Default.Home, contentDescription = "教务首页", modifier = Modifier.size(18.dp))
      }
      IconButton(
        onClick = {
          val cm = CookieManager.getInstance()
          cm.removeAllCookies {
            cm.flush()
            webViewRef?.clearCache(true)
            webViewRef?.loadUrl(uiState.homeUrl)
          }
        },
        modifier = Modifier.size(34.dp),
      ) {
        Icon(Icons.Default.Delete, contentDescription = "清除登录态", modifier = Modifier.size(18.dp))
      }
    }

    if (!uiState.webFallbackNotice.isNullOrBlank()) {
      Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text(
          text = "${uiState.webFallbackNotice}；完成登录后将自动返回原生界面。",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSecondaryContainer,
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
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
      factory = { ctx ->
        WebView(ctx).apply {
          val cm = CookieManager.getInstance()
          cm.setAcceptCookie(true)
          cm.setAcceptThirdPartyCookies(this, true)

          settings.javaScriptEnabled = true
          settings.domStorageEnabled = true
          settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
          settings.useWideViewPort = true
          settings.loadWithOverviewMode = true
          settings.setSupportZoom(true)
          settings.builtInZoomControls = true
          settings.displayZoomControls = false

          webViewClient =
            object : WebViewClient() {
              override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                canGoBack = view?.canGoBack() == true
              }

              override fun onPageFinished(view: WebView?, url: String?) {
                canGoBack = view?.canGoBack() == true
                currentTitle = view?.title?.ifBlank { "同济统一身份认证" } ?: "同济统一身份认证"
                CookieManager.getInstance().flush()
                if (TongjiSsoAutoLoginRunner.isSsoHost(url)) {
                  viewModel.buildSsoAutofillJsIfSaved()?.let { js ->
                    view?.evaluateJavascript(js, null)
                  }
                }
                if (url != null && url.contains("1.tongji.edu.cn/workbench")) {
                  viewModel.onWebLoginLanded()
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
          loadUrl(uiState.homeUrl)
        }
      },
      update = { webViewRef = it },
    )
  }
}
