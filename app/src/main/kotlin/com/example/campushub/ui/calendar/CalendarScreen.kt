package com.example.campushub.ui.calendar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.campushub.data.local.CalendarEvent
import com.example.campushub.data.local.EventCategory
import com.example.campushub.data.local.EventSource
import com.example.campushub.ui.auth.AboutAppDialog
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val WEEK_HEADERS = listOf("一", "二", "三", "四", "五", "六", "日")
private val DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd")
private const val MAX_INLINE_BARS_PER_CELL = 3

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CalendarScreen(
  viewModel: CalendarViewModel,
  modifier: Modifier = Modifier,
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  var showAboutDialog by remember { mutableStateOf(false) }

  val anchorMonth = remember { YearMonth.now() }
  val anchorWeekStart = remember { startOfWeek(LocalDate.now()) }

  val monthPagerState =
    rememberPagerState(
      initialPage = pageForMonth(anchorMonth, uiState.currentMonth),
      pageCount = { CALENDAR_PAGER_COUNT },
    )
  val weekPagerState =
    rememberPagerState(
      initialPage = pageForWeekStart(anchorWeekStart, uiState.currentWeekStart),
      pageCount = { CALENDAR_PAGER_COUNT },
    )

  // Pager -> ViewModel
  LaunchedEffect(monthPagerState.currentPage, uiState.displayMode) {
    if (uiState.displayMode == CalendarDisplayMode.MONTH) {
      val pagedMonth = monthForPage(anchorMonth, monthPagerState.currentPage)
      if (pagedMonth != uiState.currentMonth) {
        viewModel.setMonth(pagedMonth)
      }
    }
  }
  LaunchedEffect(weekPagerState.currentPage, uiState.displayMode) {
    if (uiState.displayMode == CalendarDisplayMode.WEEK) {
      val pagedWeek = weekStartForPage(anchorWeekStart, weekPagerState.currentPage)
      if (pagedWeek != uiState.currentWeekStart) {
        viewModel.setWeekStart(pagedWeek)
      }
    }
  }

  // ViewModel -> Pager (when user taps <, >, Today, or saves event on another date)
  LaunchedEffect(uiState.currentMonth) {
    val targetPage = pageForMonth(anchorMonth, uiState.currentMonth)
    if (monthPagerState.currentPage != targetPage) {
      monthPagerState.animateScrollToPage(targetPage)
    }
  }
  LaunchedEffect(uiState.currentWeekStart) {
    val targetPage = pageForWeekStart(anchorWeekStart, uiState.currentWeekStart)
    if (weekPagerState.currentPage != targetPage) {
      weekPagerState.animateScrollToPage(targetPage)
    }
  }

  val headerTitle =
    when (uiState.displayMode) {
      CalendarDisplayMode.MONTH -> "${uiState.currentMonth.year}年${uiState.currentMonth.monthValue}月"
      CalendarDisplayMode.WEEK -> {
        val end = uiState.currentWeekStart.plusDays(6)
        "${uiState.currentWeekStart.monthValue}/${uiState.currentWeekStart.dayOfMonth} - ${end.monthValue}/${end.dayOfMonth}"
      }
    }

  Column(
    modifier = modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 4.dp)
  ) {
    // Top navigation + Month/Week mode switcher
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      IconButton(onClick = viewModel::previousPeriod, modifier = Modifier.size(34.dp)) {
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "上一页")
      }
      Text(
        text = headerTitle,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        modifier =
          Modifier.combinedClickable(
            onClick = {},
            onLongClick = { showAboutDialog = true },
          ),
      )
      IconButton(onClick = viewModel::nextPeriod, modifier = Modifier.size(34.dp)) {
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "下一页")
      }
      Spacer(modifier = Modifier.weight(1f))

      SingleChoiceSegmentedButtonRow(modifier = Modifier.height(34.dp)) {
        CalendarDisplayMode.entries.forEachIndexed { index, mode ->
          SegmentedButton(
            selected = uiState.displayMode == mode,
            onClick = { viewModel.setDisplayMode(mode) },
            shape = SegmentedButtonDefaults.itemShape(index = index, count = 2),
          ) {
            Text(mode.label, fontSize = 12.sp)
          }
        }
      }

      Spacer(modifier = Modifier.width(6.dp))
      OutlinedButton(
        onClick = viewModel::jumpToToday,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
        modifier = Modifier.height(32.dp),
      ) {
        Text("今", fontSize = 12.sp)
      }
      IconButton(
        onClick = { viewModel.openNewEventEditor(LocalDate.now()) },
        modifier = Modifier.size(36.dp),
      ) {
        Icon(Icons.Default.Add, contentDescription = "新建日程")
      }
    }

    when (uiState.displayMode) {
      CalendarDisplayMode.MONTH -> {
        // Weekday headers (Mon..Sun)
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
          WEEK_HEADERS.forEachIndexed { index, label ->
            val isWeekend = index >= 5
            Text(
              text = label,
              modifier = Modifier.weight(1f),
              textAlign = TextAlign.Center,
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.Medium,
              color =
                if (isWeekend) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }

        HorizontalPager(
          state = monthPagerState,
          modifier = Modifier.weight(1f).fillMaxWidth(),
        ) { page ->
          val pageMonth = monthForPage(anchorMonth, page)
          val weeks = remember(pageMonth) { buildMonthGrid(pageMonth).chunked(7) }

          Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(3.dp),
          ) {
            weeks.forEach { week ->
              Row(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
              ) {
                week.forEach { cell ->
                  val dayEvents = uiState.eventsByDate[cell.date].orEmpty()
                  DayGridCell(
                    cell = cell,
                    isSelected = uiState.selectedDate == cell.date,
                    events = dayEvents,
                    onClick = { viewModel.selectDate(cell.date) },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                  )
                }
              }
            }
          }
        }
      }

      CalendarDisplayMode.WEEK -> {
        HorizontalPager(
          state = weekPagerState,
          modifier = Modifier.weight(1f).fillMaxWidth(),
        ) { page ->
          val weekStart = weekStartForPage(anchorWeekStart, page)
          val weekCells = remember(weekStart) { buildWeekDays(weekStart) }

          LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            items(weekCells, key = { it.date.toEpochDay() }) { cell ->
              val dayEvents = uiState.eventsByDate[cell.date].orEmpty()
              WeekDayCard(
                cell = cell,
                events = dayEvents,
                onCardClick = { viewModel.selectDate(cell.date) },
                onAddClick = { viewModel.openNewEventEditor(cell.date) },
              )
            }
          }
        }
      }
    }
  }

  // Day detail bottom sheet on cell/card tap
  val selectedDate = uiState.selectedDate
  if (selectedDate != null) {
    val dayEvents = uiState.eventsByDate[selectedDate].orEmpty()
    ModalBottomSheet(onDismissRequest = viewModel::dismissDayDetail) {
      DayDetailSheetContent(
        date = selectedDate,
        events = dayEvents,
        onAddClick = { viewModel.openNewEventEditor(selectedDate) },
        onToggleComplete = viewModel::toggleEventCompleted,
        onEditClick = viewModel::openEditEventEditor,
        onDeleteClick = viewModel::deleteEvent,
      )
    }
  }

  // Add / edit event dialog
  val editor = uiState.editorState
  if (editor != null) {
    EventEditorDialog(
      state = editor,
      onUpdate = viewModel::updateEditor,
      onDismiss = viewModel::dismissEditor,
      onSave = viewModel::saveEditorEvent,
    )
  }

  if (showAboutDialog) {
    AboutAppDialog(onDismiss = { showAboutDialog = false })
  }
}

@Composable
private fun WeekDayCard(
  cell: CalendarDayCell,
  events: List<CalendarEvent>,
  onCardClick: () -> Unit,
  onAddClick: () -> Unit,
) {
  val dayOfWeekName = cell.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.CHINA)
  val borderColor =
    if (cell.isToday) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
  val bgColor =
    if (cell.isToday) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.16f)
    else MaterialTheme.colorScheme.surfaceContainerLowest

  Surface(
    shape = RoundedCornerShape(12.dp),
    color = bgColor,
    border = BorderStroke(if (cell.isToday) 1.5.dp else 0.8.dp, borderColor),
    modifier = Modifier.fillMaxWidth().clickable(onClick = onCardClick),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "${cell.date.monthValue}月${cell.date.dayOfMonth}日 $dayOfWeekName",
          style = MaterialTheme.typography.titleSmall,
          fontWeight = if (cell.isToday) FontWeight.Bold else FontWeight.SemiBold,
          color =
            if (cell.isToday) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface,
        )
        if (cell.isToday) {
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = "今天",
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onPrimary,
            modifier =
              Modifier.clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.primary)
                .padding(horizontal = 5.dp, vertical = 1.dp),
          )
        }
        Spacer(modifier = Modifier.weight(1f))
        IconButton(onClick = onAddClick, modifier = Modifier.size(28.dp)) {
          Icon(
            Icons.Default.Add,
            contentDescription = "添加日程",
            modifier = Modifier.size(18.dp),
          )
        }
      }

      if (events.isEmpty()) {
        Text(
          text = "暂无安排",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
        )
      } else {
        events.forEach { event ->
          Row(
            modifier =
              Modifier.fillMaxWidth()
                .alpha(if (event.isCompleted) 0.55f else 1f)
                .clip(RoundedCornerShape(6.dp))
                .background(event.category.barBgColor)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Box(
              modifier =
                Modifier.width(3.5.dp)
                  .height(18.dp)
                  .clip(RoundedCornerShape(2.dp))
                  .background(event.category.accentColor)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = event.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = event.category.barTextColor,
                textDecoration =
                  if (event.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
              val subParts = buildList {
                if (event.timeRange.isNotBlank()) add(event.timeRange)
                if (event.note.isNotBlank()) add(event.note)
              }
              if (subParts.isNotEmpty()) {
                Text(
                  text = subParts.joinToString(" · "),
                  fontSize = 11.sp,
                  color = event.category.barTextColor.copy(alpha = 0.8f),
                  maxLines = 1,
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
private fun DayGridCell(
  cell: CalendarDayCell,
  isSelected: Boolean,
  events: List<CalendarEvent>,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val borderColor =
    when {
      isSelected -> MaterialTheme.colorScheme.primary
      cell.isToday -> MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
      else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
    }
  val borderWidth = if (isSelected || cell.isToday) 1.5.dp else 0.7.dp
  val bgColor =
    when {
      cell.isToday -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f)
      cell.isCurrentMonth -> MaterialTheme.colorScheme.surface
      else -> MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.55f)
    }

  Column(
    modifier =
      modifier
        .clip(RoundedCornerShape(6.dp))
        .background(bgColor)
        .border(borderWidth, borderColor, RoundedCornerShape(6.dp))
        .clickable(onClick = onClick)
        .padding(horizontal = 2.dp, vertical = 2.dp),
    verticalArrangement = Arrangement.spacedBy(2.dp),
  ) {
    Box(
      modifier =
        Modifier.size(18.dp)
          .clip(CircleShape)
          .background(if (cell.isToday) MaterialTheme.colorScheme.primary else Color.Transparent),
      contentAlignment = Alignment.Center,
    ) {
      Text(
        text = cell.date.dayOfMonth.toString(),
        fontSize = 11.sp,
        fontWeight = if (cell.isToday) FontWeight.Bold else FontWeight.Medium,
        color =
          when {
            cell.isToday -> MaterialTheme.colorScheme.onPrimary
            cell.isCurrentMonth -> MaterialTheme.colorScheme.onSurface
            else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
          },
      )
    }

    events.take(MAX_INLINE_BARS_PER_CELL).forEach { event ->
      InlineEventBar(event = event)
    }

    if (events.size > MAX_INLINE_BARS_PER_CELL) {
      Text(
        text = "+${events.size - MAX_INLINE_BARS_PER_CELL}",
        fontSize = 9.sp,
        lineHeight = 10.sp,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 2.dp),
      )
    }
  }
}

@Composable
private fun InlineEventBar(event: CalendarEvent) {
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .alpha(if (event.isCompleted) 0.52f else 1f)
        .clip(RoundedCornerShape(3.dp))
        .background(event.category.barBgColor)
        .padding(vertical = 1.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(modifier = Modifier.width(2.5.dp).height(11.dp).background(event.category.accentColor))
    Text(
      text = event.title,
      fontSize = 9.5.sp,
      lineHeight = 11.sp,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      textDecoration = if (event.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
      color = event.category.barTextColor,
      modifier = Modifier.padding(horizontal = 2.dp),
    )
  }
}

@Composable
private fun DayDetailSheetContent(
  date: LocalDate,
  events: List<CalendarEvent>,
  onAddClick: () -> Unit,
  onToggleComplete: (Long) -> Unit,
  onEditClick: (CalendarEvent) -> Unit,
  onDeleteClick: (Long) -> Unit,
) {
  val dayOfWeekLabel = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.CHINA)

  Column(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = "${date.monthValue}月${date.dayOfMonth}日 $dayOfWeekLabel",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
        )
        Text(
          text = if (events.isEmpty()) "当天暂无安排" else "共 ${events.size} 项安排",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      FilledTonalButton(onClick = onAddClick) {
        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text("添加事项")
      }
    }

    if (events.isNotEmpty()) {
      LazyColumn(
        modifier = Modifier.fillMaxWidth().height(280.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        items(events, key = { it.id }) { event ->
          Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Checkbox(
                checked = event.isCompleted,
                onCheckedChange = { onToggleComplete(event.id) },
              )
              Box(
                modifier =
                  Modifier.width(4.dp)
                    .height(36.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(event.category.accentColor)
              )
              Spacer(modifier = Modifier.width(10.dp))
              Column(modifier = Modifier.weight(1f)) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                  Text(
                    text = event.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    textDecoration =
                      if (event.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                  )
                  if (event.source == EventSource.CANVAS_DDL) {
                    Text(
                      text = "Canvas",
                      fontSize = 10.sp,
                      color = event.category.barTextColor,
                      modifier =
                        Modifier.clip(RoundedCornerShape(4.dp))
                          .background(event.category.barBgColor)
                          .padding(horizontal = 5.dp, vertical = 1.dp),
                    )
                  }
                }
                val metaParts = buildList {
                  add(event.category.label)
                  if (event.timeRange.isNotBlank()) add(event.timeRange)
                }
                Text(
                  text = metaParts.joinToString(" · "),
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (event.note.isNotBlank()) {
                  Text(
                    text = event.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                  )
                }
              }
              IconButton(onClick = { onEditClick(event) }) {
                Icon(Icons.Default.Edit, contentDescription = "编辑", modifier = Modifier.size(18.dp))
              }
              IconButton(onClick = { onDeleteClick(event.id) }) {
                Icon(
                  Icons.Default.Delete,
                  contentDescription = "删除",
                  modifier = Modifier.size(18.dp),
                )
              }
            }
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EventEditorDialog(
  state: EventEditorState,
  onUpdate: ((EventEditorState) -> EventEditorState) -> Unit,
  onDismiss: () -> Unit,
  onSave: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(if (state.editingId == null) "新建日程" else "编辑日程") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
          value = state.title,
          onValueChange = { value -> onUpdate { it.copy(title = value) } },
          label = { Text("标题 *") },
          placeholder = { Text("例如：高数作业 / 组会") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )

        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text("日期", style = MaterialTheme.typography.labelLarge)
          Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onUpdate { it.copy(date = it.date.minusDays(1)) } }) {
              Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "前一天")
            }
            Text(
              text = state.date.format(DATE_FORMATTER),
              style = MaterialTheme.typography.bodyMedium,
              fontWeight = FontWeight.Medium,
            )
            IconButton(onClick = { onUpdate { it.copy(date = it.date.plusDays(1)) } }) {
              Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "后一天")
            }
          }
        }

        OutlinedTextField(
          value = state.timeRange,
          onValueChange = { value -> onUpdate { it.copy(timeRange = value) } },
          label = { Text("时间段（可选）") },
          placeholder = { Text("例如：14:00-15:30 或留空") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text("分类颜色", style = MaterialTheme.typography.labelLarge)
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            EventCategory.entries.forEach { cat ->
              FilterChip(
                selected = state.category == cat,
                onClick = { onUpdate { it.copy(category = cat) } },
                leadingIcon = {
                  Box(
                    modifier = Modifier.size(10.dp).clip(CircleShape).background(cat.accentColor)
                  )
                },
                label = { Text(cat.label, fontSize = 12.sp) },
              )
            }
          }
        }

        OutlinedTextField(
          value = state.note,
          onValueChange = { value -> onUpdate { it.copy(note = value) } },
          label = { Text("备注（可选）") },
          maxLines = 3,
          modifier = Modifier.fillMaxWidth(),
        )
      }
    },
    confirmButton = {
      Button(onClick = onSave, enabled = state.title.isNotBlank()) { Text("保存") }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("取消") }
    },
  )
}
