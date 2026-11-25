package com.example.campushub.data.local

import androidx.compose.ui.graphics.Color
import java.time.LocalDate

enum class EventCategory(
  val label: String,
  val accentColor: Color,
  val barBgColor: Color,
  val barTextColor: Color,
) {
  STUDY(
    label = "学习/课程",
    accentColor = Color(0xFF1A73E8),
    barBgColor = Color(0xFFD6E6FF),
    barTextColor = Color(0xFF0A3678),
  ),
  DDL(
    label = "作业DDL",
    accentColor = Color(0xFFD93025),
    barBgColor = Color(0xFFFFDAD6),
    barTextColor = Color(0xFF7A140F),
  ),
  EXAM(
    label = "考试/重要",
    accentColor = Color(0xFF7B1FA2),
    barBgColor = Color(0xFFEDE0FF),
    barTextColor = Color(0xFF420D5E),
  ),
  LIFE(
    label = "生活/个人",
    accentColor = Color(0xFF1E8E3E),
    barBgColor = Color(0xFFD3F2DC),
    barTextColor = Color(0xFF0B4E20),
  ),
  OTHER(
    label = "其他",
    accentColor = Color(0xFF5F6368),
    barBgColor = Color(0xFFE6E8EC),
    barTextColor = Color(0xFF2B2E33),
  );

  companion object {
    fun fromNameOrDefault(name: String?): EventCategory =
      entries.firstOrNull { it.name == name } ?: OTHER
  }
}

enum class EventSource {
  LOCAL,
  CANVAS_DDL,
  TONGJI_EXAM;

  companion object {
    fun fromNameOrDefault(name: String?): EventSource =
      entries.firstOrNull { it.name == name } ?: LOCAL
  }
}

data class CalendarEvent(
  val id: Long = 0,
  val title: String,
  val dateEpochDay: Long,
  val timeRange: String = "",
  val note: String = "",
  val category: EventCategory = EventCategory.STUDY,
  val isCompleted: Boolean = false,
  val source: EventSource = EventSource.LOCAL,
  val externalId: String? = null,
) {
  val date: LocalDate
    get() = LocalDate.ofEpochDay(dateEpochDay)
}
