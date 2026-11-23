package com.example.campushub.data.canvas

import com.example.campushub.util.HtmlTextCleaner
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlinx.serialization.json.Json

data class CanvasGroupedTasks(
  val pendingWithDue: List<CanvasTaskItem>,
  val completedWithDue: List<CanvasTaskItem>,
  val noDueDate: List<CanvasTaskItem>,
)

object CanvasJsonParser {

  private val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
  }

  private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
  private val dateTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
  private val courseAssignUrlRegex = Regex("/courses/(\\d+)/assignments/(\\d+)")

  fun parseUser(body: String): CanvasUser = json.decodeFromString(CanvasUser.serializer(), body)

  fun parseCourses(body: String): List<CanvasCourse> =
    json
      .decodeFromString<List<CanvasCourse>>(body)
      .filter { !it.accessRestrictedByDate && it.name.isNotBlank() }

  fun parsePlannerItems(
    body: String,
    baseUrl: String,
    zoneId: ZoneId = ZoneId.systemDefault(),
  ): List<CanvasTaskItem> {
    val rawList = json.decodeFromString<List<CanvasPlannerRawItem>>(body)
    return rawList.mapNotNull { raw ->
      val title = raw.plannable?.title?.trim().orEmpty()
      if (title.isEmpty()) return@mapNotNull null

      val iso = raw.plannable?.dueAt ?: raw.plannableDate
      val localDateTime = parseIsoToLocal(iso, zoneId)
      val urlMatch = raw.htmlUrl?.let { courseAssignUrlRegex.find(it) }
      val resolvedCourseId = raw.courseId ?: urlMatch?.groupValues?.getOrNull(1)?.toLongOrNull()
      val resolvedAssignmentId =
        if (raw.plannableType == "assignment") {
          raw.plannable?.id ?: raw.plannableId.takeIf { it > 0L }
        } else {
          urlMatch?.groupValues?.getOrNull(2)?.toLongOrNull()
        }

      val isDone =
        raw.submissions?.submitted == true ||
          raw.submissions?.graded == true ||
          raw.plannerOverride?.markedComplete == true

      CanvasTaskItem(
        externalId = "canvas_${raw.plannableType}_${raw.plannableId}",
        title = title,
        courseId = resolvedCourseId,
        assignmentId = resolvedAssignmentId,
        courseName = raw.contextName?.trim().orEmpty().ifEmpty { "Canvas" },
        dueDate = localDateTime?.first,
        dueTimeText = localDateTime?.second.orEmpty(),
        htmlUrl = resolveHtmlUrl(baseUrl, raw.htmlUrl),
        isSubmitted = isDone,
        plannableType = raw.plannableType,
        pointsPossible = raw.plannable?.pointsPossible,
      )
    }
  }

  fun parseTodoItems(
    body: String,
    baseUrl: String,
    zoneId: ZoneId = ZoneId.systemDefault(),
  ): List<CanvasTaskItem> {
    val rawList = json.decodeFromString<List<CanvasTodoRawItem>>(body)
    return rawList.mapNotNull { raw ->
      val assignment = raw.assignment ?: return@mapNotNull null
      val title = assignment.name.trim()
      if (title.isEmpty()) return@mapNotNull null

      val localDateTime = parseIsoToLocal(assignment.dueAt, zoneId)
      val htmlUrl = resolveHtmlUrl(baseUrl, assignment.htmlUrl ?: raw.htmlUrl)
      val urlMatch = htmlUrl?.let { courseAssignUrlRegex.find(it) }
      val resolvedCourseId =
        raw.courseId ?: assignment.courseId ?: urlMatch?.groupValues?.getOrNull(1)?.toLongOrNull()

      CanvasTaskItem(
        externalId = "canvas_assignment_${assignment.id}",
        title = title,
        courseId = resolvedCourseId,
        assignmentId = assignment.id.takeIf { it > 0L },
        courseName = raw.contextName?.trim().orEmpty().ifEmpty { "Canvas" },
        dueDate = localDateTime?.first,
        dueTimeText = localDateTime?.second.orEmpty(),
        htmlUrl = htmlUrl,
        isSubmitted = false,
        plannableType = raw.type.ifBlank { "assignment" },
        pointsPossible = assignment.pointsPossible,
      )
    }
  }

  fun parseCourseAssignments(
    body: String,
    course: CanvasCourse,
    baseUrl: String,
    zoneId: ZoneId = ZoneId.systemDefault(),
  ): List<CanvasTaskItem> {
    val rawList = json.decodeFromString<List<CanvasAssignmentRaw>>(body)
    return rawList.mapNotNull { raw ->
      val title = raw.name.trim()
      if (title.isEmpty()) return@mapNotNull null
      val localDateTime = parseIsoToLocal(raw.dueAt, zoneId)
      val submission = raw.submission
      val submitted = isSubmissionDone(submission)

      CanvasTaskItem(
        externalId = "canvas_assignment_${raw.id}",
        title = title,
        courseId = raw.courseId ?: course.id,
        assignmentId = raw.id.takeIf { it > 0L },
        courseName = course.name,
        dueDate = localDateTime?.first,
        dueTimeText = localDateTime?.second.orEmpty(),
        htmlUrl = resolveHtmlUrl(baseUrl, raw.htmlUrl),
        isSubmitted = submitted,
        plannableType = "assignment",
        pointsPossible = raw.pointsPossible,
        score = submission?.score,
        grade = submission?.grade,
      )
    }
  }

  fun parseAssignmentDetail(
    body: String,
    courseId: Long,
    courseName: String,
    baseUrl: String,
    zoneId: ZoneId = ZoneId.systemDefault(),
  ): CanvasAssignmentDetail {
    val raw = json.decodeFromString(CanvasAssignmentRaw.serializer(), body)
    val submission = raw.submission
    val comments =
      submission?.submissionComments.orEmpty().mapNotNull { c ->
        val txt = c.comment?.trim().orEmpty()
        if (txt.isEmpty()) null
        else if (!c.authorName.isNullOrBlank()) "${c.authorName}: $txt" else txt
      }
    return CanvasAssignmentDetail(
      id = raw.id,
      courseId = raw.courseId ?: courseId,
      courseName = courseName,
      title = raw.name.trim().ifEmpty { "作业详情" },
      descriptionText = HtmlTextCleaner.toReadableText(raw.description).ifBlank { "暂无详细描述" },
      dueDateText = formatIsoDateTime(raw.dueAt, zoneId).ifBlank { "无截止时间" },
      pointsPossible = raw.pointsPossible,
      submissionTypes = raw.submissionTypes,
      workflowState = submission?.workflowState ?: "unsubmitted",
      isSubmitted = isSubmissionDone(submission),
      score = submission?.score,
      grade = submission?.grade,
      isLate = submission?.late == true,
      isMissing = submission?.missing == true,
      comments = comments,
      htmlUrl = resolveHtmlUrl(baseUrl, raw.htmlUrl),
    )
  }

  fun parseAnnouncements(
    body: String,
    baseUrl: String,
    zoneId: ZoneId = ZoneId.systemDefault(),
  ): List<CanvasAnnouncement> {
    val rawList = json.decodeFromString<List<CanvasAnnouncementRaw>>(body)
    return rawList.map { raw ->
      CanvasAnnouncement(
        id = raw.id,
        title = raw.title.trim().ifEmpty { "课程公告" },
        authorName = raw.author?.displayName?.trim().orEmpty(),
        postedAtText = formatIsoDateTime(raw.postedAt, zoneId),
        bodyText = HtmlTextCleaner.toReadableText(raw.message).ifBlank { "（无正文内容）" },
        htmlUrl = resolveHtmlUrl(baseUrl, raw.htmlUrl),
      )
    }
  }

  fun parseModules(body: String, baseUrl: String): List<CanvasModule> {
    val rawList = json.decodeFromString<List<CanvasModuleRaw>>(body)
    return rawList.map { mod ->
      CanvasModule(
        id = mod.id,
        name = mod.name.trim().ifEmpty { "课程章节" },
        items =
          mod.items.map { item ->
            CanvasModuleItem(
              id = item.id,
              title = item.title.trim(),
              type = item.type,
              contentId = item.contentId,
              htmlUrl = resolveHtmlUrl(baseUrl, item.htmlUrl),
              apiUrl = resolveHtmlUrl(baseUrl, item.url),
            )
          },
      )
    }
  }

  fun parseFiles(body: String): List<CanvasFileItem> {
    val rawList = json.decodeFromString<List<CanvasFileRaw>>(body)
    return rawList.map { file ->
      CanvasFileItem(
        id = file.id,
        displayName = file.displayName?.trim()?.ifEmpty { null } ?: file.filename ?: "未命名文件",
        sizeBytes = file.size,
        mimeType = file.contentType,
        downloadUrl = file.url,
      )
    }
  }

  fun parseSingleFile(body: String): CanvasFileItem = parseFiles("[$body]").first()

  /**
   * Merges planner tasks and course assignments, preferring richer submission/score info from
   * course assignments when the same externalId appears in both.
   */
  fun mergeDashboardTasks(
    plannerTasks: List<CanvasTaskItem>,
    courseAssignments: List<CanvasTaskItem>,
    today: LocalDate = LocalDate.now(),
  ): List<CanvasTaskItem> {
    val merged = LinkedHashMap<String, CanvasTaskItem>()
    for (item in plannerTasks) {
      merged[item.externalId] = item
    }
    for (assign in courseAssignments) {
      val prev = merged[assign.externalId]
      merged[assign.externalId] =
        if (prev == null) {
          assign
        } else {
          assign.copy(
            dueDate = assign.dueDate ?: prev.dueDate,
            dueTimeText = assign.dueTimeText.ifBlank { prev.dueTimeText },
            htmlUrl = assign.htmlUrl ?: prev.htmlUrl,
            isSubmitted = assign.isSubmitted || prev.isSubmitted,
          )
        }
    }
    val grouped = groupAndSortTasks(merged.values.toList(), today)
    return grouped.pendingWithDue + grouped.completedWithDue + grouped.noDueDate
  }

  fun groupAndSortTasks(
    tasks: List<CanvasTaskItem>,
    today: LocalDate = LocalDate.now(),
  ): CanvasGroupedTasks {
    val comparator =
      compareBy<CanvasTaskItem>(
        { it.dueDate?.let { due -> abs(ChronoUnit.DAYS.between(today, due)) } ?: Long.MAX_VALUE },
        { it.dueDate?.let { due -> if (due.isBefore(today)) 1 else 0 } ?: 2 },
        { it.dueTimeText },
        { it.title },
      )
    val pendingWithDue = tasks.filter { !it.isSubmitted && it.dueDate != null }.sortedWith(comparator)
    val completedWithDue = tasks.filter { it.isSubmitted && it.dueDate != null }.sortedWith(comparator)
    val noDueDate =
      tasks
        .filter { it.dueDate == null }
        .sortedWith(compareBy<CanvasTaskItem> { it.isSubmitted }.thenBy { it.courseName }.thenBy { it.title })
    return CanvasGroupedTasks(
      pendingWithDue = pendingWithDue,
      completedWithDue = completedWithDue,
      noDueDate = noDueDate,
    )
  }

  private fun isSubmissionDone(submission: CanvasSubmissionRaw?): Boolean {
    if (submission == null) return false
    return submission.workflowState in setOf("submitted", "graded", "pending_review") ||
      !submission.submittedAt.isNullOrBlank()
  }

  fun parseIsoToLocal(
    isoString: String?,
    zoneId: ZoneId = ZoneId.systemDefault(),
  ): Pair<LocalDate, String>? {
    if (isoString.isNullOrBlank()) return null
    val instant =
      runCatching { Instant.parse(isoString) }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(isoString).toInstant() }.getOrNull()
        ?: return null
    val zoned = instant.atZone(zoneId)
    return zoned.toLocalDate() to zoned.format(timeFormatter)
  }

  fun formatIsoDateTime(
    isoString: String?,
    zoneId: ZoneId = ZoneId.systemDefault(),
  ): String {
    if (isoString.isNullOrBlank()) return ""
    val instant =
      runCatching { Instant.parse(isoString) }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(isoString).toInstant() }.getOrNull()
        ?: return ""
    return instant.atZone(zoneId).format(dateTimeFormatter)
  }

  fun resolveHtmlUrl(baseUrl: String, pathOrUrl: String?): String? {
    val clean = pathOrUrl?.trim().orEmpty()
    if (clean.isEmpty()) return null
    return if (clean.startsWith("http://") || clean.startsWith("https://")) {
      clean
    } else {
      val root = baseUrl.trimEnd('/')
      val rel = if (clean.startsWith("/")) clean else "/$clean"
      "$root$rel"
    }
  }
}
