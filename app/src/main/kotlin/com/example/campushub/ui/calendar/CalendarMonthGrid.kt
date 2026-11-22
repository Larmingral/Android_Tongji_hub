package com.example.campushub.ui.calendar

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

data class CalendarDayCell(
  val date: LocalDate,
  val isCurrentMonth: Boolean,
  val isToday: Boolean,
)

const val CALENDAR_PAGER_COUNT = 2401
const val CALENDAR_PAGER_CENTER = 1200

/**
 * Builds a Monday-first month grid of 35 or 42 cells covering the entire [month].
 */
fun buildMonthGrid(
  month: YearMonth,
  today: LocalDate = LocalDate.now(),
): List<CalendarDayCell> {
  val firstDayOfMonth = month.atDay(1)
  val leadingDays = (firstDayOfMonth.dayOfWeek.value - DayOfWeek.MONDAY.value + 7) % 7
  val gridStart = firstDayOfMonth.minusDays(leadingDays.toLong())

  val totalDaysNeeded = leadingDays + month.lengthOfMonth()
  val totalCells = if (totalDaysNeeded <= 35) 35 else 42

  return List(totalCells) { index ->
    val date = gridStart.plusDays(index.toLong())
    CalendarDayCell(
      date = date,
      isCurrentMonth = YearMonth.from(date) == month,
      isToday = date == today,
    )
  }
}

fun startOfWeek(date: LocalDate): LocalDate {
  val offset = (date.dayOfWeek.value - DayOfWeek.MONDAY.value + 7) % 7
  return date.minusDays(offset.toLong())
}

fun buildWeekDays(
  weekStart: LocalDate,
  today: LocalDate = LocalDate.now(),
): List<CalendarDayCell> {
  val normalizedStart = startOfWeek(weekStart)
  return List(7) { index ->
    val date = normalizedStart.plusDays(index.toLong())
    CalendarDayCell(
      date = date,
      isCurrentMonth = true,
      isToday = date == today,
    )
  }
}

fun monthForPage(
  anchorMonth: YearMonth,
  page: Int,
  centerPage: Int = CALENDAR_PAGER_CENTER,
): YearMonth = anchorMonth.plusMonths((page - centerPage).toLong())

fun pageForMonth(
  anchorMonth: YearMonth,
  targetMonth: YearMonth,
  centerPage: Int = CALENDAR_PAGER_CENTER,
): Int {
  val delta = ChronoUnit.MONTHS.between(anchorMonth, targetMonth).toInt()
  return (centerPage + delta).coerceIn(0, CALENDAR_PAGER_COUNT - 1)
}

fun weekStartForPage(
  anchorWeekStart: LocalDate,
  page: Int,
  centerPage: Int = CALENDAR_PAGER_CENTER,
): LocalDate = anchorWeekStart.plusWeeks((page - centerPage).toLong())

fun pageForWeekStart(
  anchorWeekStart: LocalDate,
  targetWeekStart: LocalDate,
  centerPage: Int = CALENDAR_PAGER_CENTER,
): Int {
  val delta = ChronoUnit.WEEKS.between(anchorWeekStart, startOfWeek(targetWeekStart)).toInt()
  return (centerPage + delta).coerceIn(0, CALENDAR_PAGER_COUNT - 1)
}
