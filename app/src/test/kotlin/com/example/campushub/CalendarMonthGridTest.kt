package com.example.campushub.ui.calendar

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarMonthGridTest {

  @Test
  fun november2026_startsSunday_renders42CellsMondayFirst() {
    val cells = buildMonthGrid(YearMonth.of(2026, 11), today = LocalDate.of(2026, 11, 22))

    assertEquals(42, cells.size)
    assertEquals(DayOfWeek.MONDAY, cells.first().date.dayOfWeek)
    assertEquals(LocalDate.of(2026, 10, 26), cells.first().date)
    assertFalse(cells.first().isCurrentMonth)

    val nov1 = cells.first { it.date == LocalDate.of(2026, 11, 1) }
    assertTrue(nov1.isCurrentMonth)

    val todayCell = cells.first { it.date == LocalDate.of(2026, 11, 22) }
    assertTrue(todayCell.isToday)
  }

  @Test
  fun february2021_startsMonday28Days_renders35Cells() {
    val cells = buildMonthGrid(YearMonth.of(2021, 2), today = LocalDate.of(2021, 2, 1))
    assertEquals(35, cells.size)
    assertEquals(LocalDate.of(2021, 2, 1), cells.first().date)
    assertEquals(28, cells.count { it.isCurrentMonth })
  }

  @Test
  fun weekHelpers_startOnMondayAndBuild7Days() {
    val sunday = LocalDate.of(2026, 11, 22)
    val weekStart = startOfWeek(sunday)
    assertEquals(LocalDate.of(2026, 11, 16), weekStart)

    val weekCells = buildWeekDays(sunday, today = sunday)
    assertEquals(7, weekCells.size)
    assertEquals(DayOfWeek.MONDAY, weekCells.first().date.dayOfWeek)
    assertEquals(DayOfWeek.SUNDAY, weekCells.last().date.dayOfWeek)
    assertTrue(weekCells.last().isToday)
  }

  @Test
  fun pagerConversions_roundTripMonthsAndWeeks() {
    val anchorMonth = YearMonth.of(2026, 11)
    val nextMonth = YearMonth.of(2027, 2)
    val mPage = pageForMonth(anchorMonth, nextMonth)
    assertEquals(CALENDAR_PAGER_CENTER + 3, mPage)
    assertEquals(nextMonth, monthForPage(anchorMonth, mPage))

    val anchorWeek = LocalDate.of(2026, 11, 16)
    val prevWeek = LocalDate.of(2026, 11, 2)
    val wPage = pageForWeekStart(anchorWeek, prevWeek)
    assertEquals(CALENDAR_PAGER_CENTER - 2, wPage)
    assertEquals(prevWeek, weekStartForPage(anchorWeek, wPage))
  }
}
