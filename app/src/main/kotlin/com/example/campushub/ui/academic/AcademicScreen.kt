package com.example.campushub.ui.academic

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebChromeClient
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.campushub.data.academic.AcademicNotice
import com.example.campushub.data.academic.ClassPeriodTime
import com.example.campushub.data.academic.ExamEntry
import com.example.campushub.data.academic.GradeTermSummary
import com.example.campushub.data.academic.TimetableEntry
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

private const val INSTALL_HOOK_JS = """
(function() {
  if (window.__campusHookInstalled) return;
  window.__campusHookInstalled = true;
  window.__campusApiMap = window.__campusApiMap || {};
  function toSchema(val, depth) {
    if (depth > 4) return '...';
    if (val === null || val === undefined) return 'null';
    if (Array.isArray(val)) return val.length === 0 ? [] : [toSchema(val[0], depth + 1)];
    var t = typeof val;
    if (t === 'string') return '<string>';
    if (t === 'number') return '<number>';
    if (t === 'boolean') return '<boolean>';
    if (t === 'object') {
      var out = {};
      var keys = Object.keys(val).slice(0, 22);
      for (var i = 0; i < keys.length; i++) out[keys[i]] = toSchema(val[keys[i]], depth + 1);
      return out;
    }
    return '<' + t + '>';
  }
  function trySchema(raw) {
    if (!raw || typeof raw !== 'string') return null;
    try { return toSchema(JSON.parse(raw), 0); } catch (e) { return null; }
  }
  function recordApi(method, rawUrl, reqBodyRaw, respTextRaw) {
    try {
      var u = new URL(rawUrl, location.href);
      if (u.hostname.indexOf('tongji.edu.cn') === -1 || u.pathname.indexOf('/api/') === -1) return;
      var m = (method || 'GET').toUpperCase();
      var key = m + ' ' + u.pathname;
      var qKeys = [];
      u.searchParams.forEach(function(_, k) { if (qKeys.indexOf(k) === -1) qKeys.push(k); });
      var prev = window.__campusApiMap[key] || {};
      window.__campusApiMap[key] = {
        method: m, path: u.pathname,
        queryKeys: qKeys.length > 0 ? qKeys : (prev.queryKeys || []),
        reqSchema: trySchema(reqBodyRaw) || prev.reqSchema || null,
        respSchema: trySchema(respTextRaw) || prev.respSchema || null
      };
    } catch (e) {}
  }
  var origFetch = window.fetch;
  if (origFetch) {
    window.fetch = function(input, init) {
      var url = typeof input === 'string' ? input : (input && input.url) || '';
      var method = (init && init.method) || (input && input.method) || 'GET';
      var reqBody = init && typeof init.body === 'string' ? init.body : null;
      return origFetch.apply(this, arguments).then(function(resp) {
        try { resp.clone().text().then(function(txt) { recordApi(method, url, reqBody, txt); }).catch(function() {}); } catch (e) {}
        return resp;
      });
    };
  }
  var origOpen = XMLHttpRequest.prototype.open;
  XMLHttpRequest.prototype.open = function(method, url) {
    this.__campusMethod = method || 'GET';
    this.__campusUrl = url || '';
    return origOpen.apply(this, arguments);
  };
  var origSend = XMLHttpRequest.prototype.send;
  XMLHttpRequest.prototype.send = function(body) {
    var self = this;
    var reqBody = typeof body === 'string' ? body : null;
    this.addEventListener('load', function() {
      var respText = null;
      try { if (self.responseType === '' || self.responseType === 'text') respText = self.responseText; } catch (e) {}
      recordApi(self.__campusMethod, self.__campusUrl, reqBody, respText);
    });
    return origSend.apply(this, arguments);
  };
})();
"""

private const val PROBE_DOM_JS = """
(function() {
  var map = window.__campusApiMap || {};
  var apis = Object.keys(map).map(function(k) { return map[k]; });
  return JSON.stringify({
    title: document.title || '',
    route: location.pathname + (location.hash || ''),
    apis: apis,
    tableHeaders: [],
    menus: []
  });
})();
"""

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AcademicScreen(
  viewModel: AcademicViewModel,
  modifier: Modifier = Modifier,
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()

  Column(modifier = modifier.fillMaxSize()) {
    // Top mode switch: Native Academic vs Persistent Web Login
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(1f)) {
        AcademicDisplayMode.entries.forEachIndexed { idx, mode ->
          SegmentedButton(
            selected = uiState.displayMode == mode,
            onClick = { viewModel.switchDisplayMode(mode) },
            shape = SegmentedButtonDefaults.itemShape(index = idx, count = 2),
          ) {
            Text(mode.label, fontSize = 13.sp)
          }
        }
      }
    }

    when (uiState.displayMode) {
      AcademicDisplayMode.NATIVE -> NativeAcademicView(uiState = uiState, viewModel = viewModel)
      AcademicDisplayMode.WEB_LOGIN -> PersistentAcademicWebView(uiState = uiState, viewModel = viewModel)
    }
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
    // Student info & refresh header
    if (dash != null) {
      Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            val userLine =
              listOf(dash.session.studentName, dash.session.majorName.ifBlank { dash.session.facultyName })
                .filter { it.isNotBlank() }
                .joinToString(" · ")
            Text(
              text = userLine.ifBlank { "同济教务已连接" },
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.SemiBold,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            Text(
              text = "${dash.session.currentTermName} · 第 ${dash.session.currentWeek} 周",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          IconButton(onClick = viewModel::refreshDashboard, enabled = !uiState.isLoading) {
            Icon(Icons.Default.Refresh, contentDescription = "刷新教务数据")
          }
        }
      }
    }

    if (uiState.isLoading && dash == null) {
      Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          CircularProgressIndicator()
          Spacer(modifier = Modifier.height(10.dp))
          Text("正在同步同济教务数据...", style = MaterialTheme.typography.bodyMedium)
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
              text = uiState.errorMessage ?: "请先在“网页登录(免登)”完成一次登录，登录态会自动共享到原生界面。",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              textAlign = TextAlign.Center,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
              Button(onClick = { viewModel.switchDisplayMode(AcademicDisplayMode.WEB_LOGIN) }) {
                Text("去网页登录")
              }
              OutlinedButton(onClick = viewModel::refreshDashboard) {
                Text("重试加载")
              }
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
    AlertDialog(
      onDismissRequest = viewModel::dismissNotice,
      title = { Text(notice.title, style = MaterialTheme.typography.titleMedium) },
      text = {
        Column(
          modifier = Modifier.verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          val meta = listOf(notice.publisher, notice.publishTime).filter { it.isNotBlank() }
          if (meta.isNotEmpty()) {
            Text(
              text = meta.joinToString(" · "),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          SelectionContainer {
            Text(text = notice.contentText, style = MaterialTheme.typography.bodyMedium)
          }
        }
      },
      confirmButton = {
        TextButton(onClick = viewModel::dismissNotice) { Text("关闭") }
      },
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

  var showTermMenu by remember { mutableStateOf(false) }
  val currentSession = uiState.dashboard?.session
  val isCurrentWeek =
    currentSession != null &&
      uiState.selectedCalendarId == currentSession.currentCalendarId &&
      uiState.selectedWeek == currentSession.currentWeek

  Column(modifier = Modifier.fillMaxSize()) {
    // Term + week + Grid/List controls
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box {
        OutlinedButton(
          onClick = { showTermMenu = true },
          contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
          modifier = Modifier.height(32.dp),
        ) {
          Text(
            text = uiState.selectedTerm?.fullName ?: currentSession?.currentTermName.orEmpty().ifBlank { "学期" },
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(16.dp))
        }
        DropdownMenu(expanded = showTermMenu, onDismissRequest = { showTermMenu = false }) {
          uiState.dashboard?.terms.orEmpty().forEach { term ->
            DropdownMenuItem(
              text = { Text(term.fullName) },
              onClick = {
                showTermMenu = false
                viewModel.selectTerm(term)
              },
            )
          }
        }
      }

      Spacer(modifier = Modifier.width(4.dp))
      IconButton(onClick = viewModel::previousWeek, modifier = Modifier.size(30.dp)) {
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "上一周")
      }
      Text(
        text = if (isCurrentWeek) "第${uiState.selectedWeek}周(本周)" else "第${uiState.selectedWeek}周",
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clickable(onClick = viewModel::jumpToCurrentWeek),
      )
      IconButton(onClick = viewModel::nextWeek, modifier = Modifier.size(30.dp)) {
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "下一周")
      }

      Spacer(modifier = Modifier.weight(1f))

      SingleChoiceSegmentedButtonRow(modifier = Modifier.height(32.dp)) {
        TimetableViewStyle.entries.forEachIndexed { idx, style ->
          SegmentedButton(
            selected = uiState.timetableStyle == style,
            onClick = { viewModel.setTimetableStyle(style) },
            shape = SegmentedButtonDefaults.itemShape(index = idx, count = 2),
          ) {
            Text(style.label, fontSize = 11.sp)
          }
        }
      }
    }

    if (uiState.isSwitchingTerm) {
      LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(2.dp))
    }

    if (weekEntries.isEmpty()) {
      CenteredNotice("第 ${uiState.selectedWeek} 周暂无课程安排")
      return
    }

    when (uiState.timetableStyle) {
      TimetableViewStyle.GRID ->
        TimetableGrid(
          entries = weekEntries,
          periodTimes = uiState.activePeriods,
          onSlotClick = viewModel::openCourseSlot,
        )
      TimetableViewStyle.LIST ->
        TimetableDailyList(
          entries = weekEntries,
          periodTimes = uiState.activePeriods,
          onSlotClick = viewModel::openCourseSlot,
        )
    }
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
private fun TimetableDailyList(
  entries: List<TimetableEntry>,
  periodTimes: Map<Int, ClassPeriodTime>,
  onSlotClick: (TimetableEntry) -> Unit,
) {
  val grouped = entries.groupBy { it.dayOfWeek }.toSortedMap()
  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(12.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    grouped.forEach { (day, dayEntries) ->
      item(key = "header_$day") {
        Text(
          text = "周${DAY_LABELS.getOrElse(day - 1) { day.toString() }}",
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.primary,
        )
      }
      items(dayEntries) { entry ->
        val startTime = periodTimes[entry.timeStart]?.beginTime.orEmpty()
        val endTime = periodTimes[entry.timeEnd]?.endTime.orEmpty()
        val timeRange =
          if (startTime.isNotBlank() && endTime.isNotBlank()) {
            "第${entry.timeStart}-${entry.timeEnd}节 ($startTime-$endTime)"
          } else {
            "第${entry.timeStart}-${entry.timeEnd}节"
          }
        val colorIdx = abs(entry.courseName.hashCode()) % COURSE_BG_COLORS.size

        Surface(
          shape = RoundedCornerShape(12.dp),
          color = COURSE_BG_COLORS[colorIdx].copy(alpha = 0.65f),
          modifier = Modifier.fillMaxWidth().clickable { onSlotClick(entry) },
        ) {
          Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
          ) {
            Text(
              text = entry.courseName,
              style = MaterialTheme.typography.bodyLarge,
              fontWeight = FontWeight.SemiBold,
              color = COURSE_TEXT_COLORS[colorIdx],
            )
            Text(
              text = listOf(timeRange, entry.roomLabel, entry.teacherName).filter { it.isNotBlank() }.joinToString(" · "),
              style = MaterialTheme.typography.bodySmall,
              color = COURSE_TEXT_COLORS[colorIdx].copy(alpha = 0.85f),
            )
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
private fun PersistentAcademicWebView(
  uiState: AcademicUiState,
  viewModel: AcademicViewModel,
) {
  val context = LocalContext.current
  var webViewRef by remember { mutableStateOf<WebView?>(null) }
  var progress by remember { mutableIntStateOf(0) }
  var canGoBack by remember { mutableStateOf(false) }
  var canGoForward by remember { mutableStateOf(false) }
  var currentTitle by remember { mutableStateOf("同济教务 (1.tongji.edu.cn)") }
  var copiedHint by remember { mutableStateOf(false) }

  BackHandler(enabled = canGoBack) { webViewRef?.goBack() }

  DisposableEffect(Unit) {
    onDispose { CookieManager.getInstance().flush() }
  }

  Column(modifier = Modifier.fillMaxSize()) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      IconButton(onClick = { webViewRef?.goBack() }, enabled = canGoBack, modifier = Modifier.size(34.dp)) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "后退", modifier = Modifier.size(18.dp))
      }
      IconButton(onClick = { webViewRef?.goForward() }, enabled = canGoForward, modifier = Modifier.size(34.dp)) {
        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "前进", modifier = Modifier.size(18.dp))
      }
      IconButton(onClick = { webViewRef?.reload() }, modifier = Modifier.size(34.dp)) {
        Icon(Icons.Default.Refresh, contentDescription = "刷新", modifier = Modifier.size(18.dp))
      }
      Text(
        text = currentTitle,
        style = MaterialTheme.typography.bodySmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
      )
      FilledTonalButton(
        onClick = {
          copiedHint = false
          webViewRef?.evaluateJavascript(PROBE_DOM_JS) { rawResult ->
            viewModel.showProbeReport(AcademicProbeFormatter.formatProbeJson(rawResult))
          }
        },
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
        modifier = Modifier.height(30.dp),
      ) {
        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text("探测结构", fontSize = 11.sp)
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
                canGoForward = view?.canGoForward() == true
                view?.evaluateJavascript(INSTALL_HOOK_JS, null)
              }

              override fun onPageFinished(view: WebView?, url: String?) {
                canGoBack = view?.canGoBack() == true
                canGoForward = view?.canGoForward() == true
                currentTitle = view?.title?.ifBlank { "同济教务" } ?: "同济教务"
                view?.evaluateJavascript(INSTALL_HOOK_JS, null)
                CookieManager.getInstance().flush()
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

  val report = uiState.probeReport
  if (report != null) {
    AlertDialog(
      onDismissRequest = viewModel::dismissProbeReport,
      title = { Text("Schema 结构探测（已全量脱敏）") },
      text = {
        Column(
          modifier = Modifier.verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          SelectionContainer {
            Text(text = report, style = MaterialTheme.typography.bodySmall)
          }
          if (copiedHint) {
            Text(
              text = "已复制到剪贴板",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.primary,
            )
          }
        }
      },
      confirmButton = {
        Button(
          onClick = {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("tongji_schema_probe", report))
            copiedHint = true
          }
        ) {
          Text("复制结果")
        }
      },
      dismissButton = {
        Row {
          TextButton(
            onClick = {
              webViewRef?.evaluateJavascript("window.__campusApiMap = {};", null)
              viewModel.dismissProbeReport()
            }
          ) {
            Text("清空记录")
          }
          TextButton(onClick = viewModel::dismissProbeReport) { Text("关闭") }
        }
      },
    )
  }
}
