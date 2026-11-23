package com.example.campushub.data.academic

data class AcademicMenuAuth(
  val authId: Long,
  val authNameCh: String,
  val urlPath: String,
)

data class AcademicSessionInfo(
  val uid: String,
  val studentName: String,
  val facultyName: String,
  val majorName: String,
  val currentCalendarId: Long,
  val currentTermName: String,
  val currentWeek: Int,
  val totalWeeks: Int,
  val auths: List<AcademicMenuAuth> = emptyList(),
  val aesKey: String = "",
  val aesIv: String = "",
  val sessionId: String = "",
)

data class ClassPeriodTime(
  val nodeId: Int,
  val classNode: String,
  val beginTime: String,
  val endTime: String,
)

data class AcademicCalendarTerm(
  val id: Long,
  val fullName: String,
  val weekNum: Int,
  val isCurrent: Boolean,
  val yearLabel: String = "",
  val termLabel: String = "",
)

data class AcademicAttachment(
  val fileName: String,
  val fileLocation: String,
)

data class AcademicNotice(
  val id: Long,
  val title: String,
  val contentText: String,
  val contentHtml: String = "",
  val publisher: String,
  val publishTime: String,
  val isPinned: Boolean,
  val attachments: List<AcademicAttachment> = emptyList(),
  val isDetailLoaded: Boolean = false,
) {
  val attachmentNames: List<String>
    get() = attachments.map { it.fileName }
}

data class TimetableEntry(
  val teachingClassId: Long,
  val courseCode: String,
  val courseName: String,
  val teacherName: String,
  val campus: String,
  val credits: Double?,
  val compulsory: String,
  val assessmentMode: String,
  val dayOfWeek: Int,
  val timeStart: Int,
  val timeEnd: Int,
  val roomLabel: String,
  val weeks: Set<Int>,
  val weekStr: String,
)

data class ExamEntry(
  val courseName: String,
  val courseCode: String,
  val examTime: String,
  val examRoom: String,
  val seatNo: String,
  val campus: String,
  val remark: String,
)

data class ExamQueryResult(
  val calendarName: String,
  val isOpen: Boolean,
  val statusMessage: String,
  val exams: List<ExamEntry>,
)

data class GradeCourseItem(
  val courseName: String,
  val courseCode: String,
  val credits: String,
  val score: String,
  val gradePoint: String,
  val courseType: String,
  val isPassed: Boolean?,
)

data class GradeTermSummary(
  val termCode: Long,
  val termName: String,
  val averagePoint: String,
  val courses: List<GradeCourseItem>,
)

data class GradeOverview(
  val totalGradePoint: String,
  val actualCredit: String,
  val failingCredits: String,
  val failingCourseCount: String,
  val terms: List<GradeTermSummary>,
)

data class AcademicDashboardData(
  val session: AcademicSessionInfo,
  val terms: List<AcademicCalendarTerm>,
  val periodTimes: Map<Int, ClassPeriodTime>,
  val notices: List<AcademicNotice>,
  val timetable: List<TimetableEntry>,
  val timetableError: String? = null,
  val examResult: ExamQueryResult,
  val grades: GradeOverview?,
)
