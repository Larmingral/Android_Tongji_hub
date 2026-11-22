package com.example.campushub.data.academic

import com.example.campushub.util.HtmlTextCleaner
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

object TongjiJsonParser {

  private val json = Json { ignoreUnknownKeys = true; isLenient = true }
  private val rangeRegex = Regex("(\\d+)\\s*-\\s*(\\d+)")
  private val singleNumRegex = Regex("\\d+")

  data class SessionUserParsed(
    val uid: String,
    val name: String,
    val facultyName: String,
    val majorName: String,
    val auths: List<AcademicMenuAuth> = emptyList(),
  )

  data class CurrentTermParsed(
    val calendarId: Long,
    val currentWeek: Int,
    val totalWeeks: Int,
    val termName: String,
  )

  data class ExamCalendarParsed(
    val calendarId: Long,
    val examType: Int,
    val isOpen: Boolean,
    val calendarName: String,
    val remark: String,
  )

  fun parseSessionUser(body: String): SessionUserParsed {
    val data = unwrapDataObj(body)
    val user = data["user"].asObj() ?: JsonObject(emptyMap())
    val extend = user["extend"].asObj() ?: JsonObject(emptyMap())
    val uid = data.str("uid").ifBlank { user.str("uid") }.ifBlank { extend.str("userId") }
    val name = user.str("name").ifBlank { extend.str("userName") }

    val authsArray = data["auths"] as? JsonArray ?: JsonArray(emptyList())
    val auths =
      authsArray.mapNotNull { el ->
        val obj = el.asObj() ?: return@mapNotNull null
        val authId = obj.longVal("authId") ?: return@mapNotNull null
        AcademicMenuAuth(
          authId = authId,
          authNameCh = obj.str("authNameCh"),
          urlPath = obj.str("urlPath"),
        )
      }

    return SessionUserParsed(
      uid = uid,
      name = name,
      facultyName = user.str("facultyName"),
      majorName = extend.str("deptOrMajor"),
      auths = auths,
    )
  }

  fun parseCurrentTerm(body: String): CurrentTermParsed {
    val data = unwrapDataObj(body)
    val cal = data["schoolCalendar"].asObj() ?: JsonObject(emptyMap())
    return CurrentTermParsed(
      calendarId = cal.longVal("id") ?: 0L,
      currentWeek = (data.intVal("week") ?: 1).coerceAtLeast(1),
      totalWeeks = (cal.intVal("weekNum") ?: 20).coerceAtLeast(1),
      termName = data.str("simpleName").ifBlank { data.str("name") },
    )
  }

  fun parseCalendarTerms(body: String): List<AcademicCalendarTerm> {
    val arr = unwrapDataArray(body)
    return arr.mapNotNull { el ->
      val obj = el.asObj() ?: return@mapNotNull null
      val id = obj.longVal("id") ?: return@mapNotNull null
      AcademicCalendarTerm(
        id = id,
        fullName = obj.str("fullName").ifBlank { "${obj.str("year")} 学期" },
        weekNum = (obj.intVal("weekNum") ?: 20).coerceAtLeast(1),
        isCurrent = obj.boolVal("currentTermFlag") == true,
      )
    }
  }

  fun parsePeriodTimes(body: String): Map<Int, ClassPeriodTime> {
    val data = unwrapDataObj(body)
    val arr =
      (data["noWeekendWorkTimes"] as? JsonArray)?.takeIf { it.isNotEmpty() }
        ?: (data["weekendWorkTimes"] as? JsonArray)
        ?: return emptyMap()

    val map = LinkedHashMap<Int, ClassPeriodTime>()
    for (el in arr) {
      val obj = el.asObj() ?: continue
      val nodeId = obj.intVal("nodeId") ?: obj.str("classNode").toIntOrNull() ?: continue
      map[nodeId] =
        ClassPeriodTime(
          nodeId = nodeId,
          classNode = obj.str("classNode").ifBlank { nodeId.toString() },
          beginTime = obj.str("beginTime"),
          endTime = obj.str("endTime"),
        )
    }
    return map
  }

  fun parseNotices(body: String): List<AcademicNotice> {
    val data = unwrapDataObj(body)
    val list = data["list"] as? JsonArray ?: return emptyList()
    return list.mapNotNull { el ->
      val obj = el.asObj() ?: return@mapNotNull null
      val title = obj.str("title")
      if (title.isBlank()) return@mapNotNull null
      val rawContent = obj.str("content")
      val cleaned = HtmlTextCleaner.toReadableText(rawContent)
      AcademicNotice(
        id = obj.longVal("id") ?: title.hashCode().toLong(),
        title = title,
        contentText = cleaned.ifBlank { "正在加载正文..." },
        publisher = obj.str("createUser").ifBlank { obj.str("faceUserName") },
        publishTime = obj.str("publishTime").ifBlank { obj.str("startTime") },
        isPinned = obj.str("topStatus") == "1",
        isDetailLoaded = cleaned.isNotBlank(),
      )
    }
  }

  fun parseNoticeDetail(body: String, fallback: AcademicNotice): AcademicNotice {
    val root = json.parseToJsonElement(body).asObj() ?: return fallback
    val code = root.intVal("code")
    if (code != null && code != 200) {
      error("获取通知正文失败 (code=$code): ${root.str("msg")}")
    }
    val data = root["data"].asObj() ?: return fallback
    val cleaned =
      HtmlTextCleaner.toReadableText(data.str("content"))
        .ifBlank { fallback.contentText.takeIf { fallback.isDetailLoaded }.orEmpty() }
        .ifBlank { "（该通知暂无文字正文）" }

    val attArray = data["commonAttachmentList"] as? JsonArray ?: JsonArray(emptyList())
    val attachmentNames =
      attArray.mapNotNull { el ->
        el.asObj()?.str("fileName")?.takeIf { it.isNotBlank() }
      }

    return fallback.copy(
      title = data.str("title").ifBlank { fallback.title },
      contentText = cleaned,
      publisher = data.str("createUser").ifBlank { data.str("faceUserName") }.ifBlank { fallback.publisher },
      publishTime = data.str("publishTime").ifBlank { fallback.publishTime },
      attachmentNames = attachmentNames,
      isDetailLoaded = true,
    )
  }

  fun parseTimetable(body: String): List<TimetableEntry> {
    val rootElem = json.parseToJsonElement(body)
    if (rootElem is JsonObject) {
      val code = rootElem.intVal("code")
      if (code != null && code != 200) {
        val msg = rootElem.str("msg").ifBlank { rootElem.str("message") }
        error("课表接口返回错误 (code=$code): $msg")
      }
    }

    val arr = unwrapDataArray(body)
    val result = mutableListOf<TimetableEntry>()

    for (el in arr) {
      val courseObj = el.asObj() ?: continue
      val teachingClassId = courseObj.longVal("teachingClassId") ?: 0L
      val courseCode = courseObj.str("newCourseCode").ifBlank { courseObj.str("courseCode") }
      val courseName = courseObj.str("courseName").ifBlank { courseObj.str("className") }
      if (courseName.isBlank()) continue
      val defaultTeacher = courseObj.str("teacherName")
      val defaultCampus = courseObj.str("campusI18n").ifBlank { courseObj.str("campus") }
      val credits = courseObj.doubleVal("credits")
      val compulsory = courseObj.str("compulsory")
      val assessmentMode =
        courseObj.str("assessmentModeI18n").ifBlank { courseObj.str("assessmentMode") }
      val defaultRoom =
        courseObj.firstStr("classRoomI18n", "classRoom", "classRoomName")

      val slots = courseObj["timeTableList"] as? JsonArray
      if (slots == null || slots.isEmpty()) continue

      for (slotEl in slots) {
        val slot = slotEl.asObj() ?: continue
        val day = parseDayOfWeek(slot["dayOfWeek"]) ?: continue
        val start = parsePeriodInt(slot["timeStart"]) ?: continue
        val end = (parsePeriodInt(slot["timeEnd"]) ?: start).coerceAtLeast(start)
        val weekStr = slot.str("weekstr").ifBlank { slot.str("weekNum") }
        val weeks = parseWeekSet(slot["weeks"], weekStr)
        val room =
          slot.firstStr("roomIdI18n", "timeAndRoom", "roomId", "roomLable").ifBlank { defaultRoom }

        result.add(
          TimetableEntry(
            teachingClassId = teachingClassId,
            courseCode = slot.firstStr("newCourseCode", "courseCode").ifBlank { courseCode },
            courseName = slot.str("courseName").ifBlank { courseName },
            teacherName = slot.str("teacherName").ifBlank { defaultTeacher },
            campus = slot.firstStr("campusI18n", "campus").ifBlank { defaultCampus },
            credits = credits,
            compulsory = compulsory,
            assessmentMode = assessmentMode,
            dayOfWeek = day,
            timeStart = start,
            timeEnd = end,
            roomLabel = room,
            weeks = weeks,
            weekStr = weekStr,
          )
        )
      }
    }

    return result.sortedWith(compareBy<TimetableEntry> { it.dayOfWeek }.thenBy { it.timeStart })
  }

  private fun parseDayOfWeek(el: JsonElement?): Int? {
    val prim = el.asPrimitive() ?: return null
    prim.intOrNull?.let { if (it in 1..7) return it }
    prim.doubleOrNull?.toInt()?.let { if (it in 1..7) return it }
    val s = prim.contentOrNull?.trim().orEmpty()
    s.toIntOrNull()?.let { if (it in 1..7) return it }
    return when {
      s.contains("一") -> 1
      s.contains("二") -> 2
      s.contains("三") -> 3
      s.contains("四") -> 4
      s.contains("五") -> 5
      s.contains("六") -> 6
      s.contains("日") || s.contains("天") -> 7
      else -> null
    }
  }

  private fun parsePeriodInt(el: JsonElement?): Int? {
    val prim = el.asPrimitive() ?: return null
    prim.intOrNull?.let { return it }
    prim.doubleOrNull?.let { return it.toInt() }
    val s = prim.contentOrNull?.trim().orEmpty()
    return s.toIntOrNull() ?: singleNumRegex.find(s)?.value?.toIntOrNull()
  }

  fun parseWeekSet(weeksElem: JsonElement?, weekStr: String): Set<Int> {
    if (weeksElem is JsonArray && weeksElem.isNotEmpty()) {
      val parsed =
        weeksElem.mapNotNull { it.asPrimitive()?.contentOrNull?.toIntOrNull() ?: it.asPrimitive()?.intOrNull }.toSet()
      if (parsed.isNotEmpty()) return parsed
    } else if (weeksElem is JsonPrimitive && weeksElem.isString) {
      val fromPrimitive = parseWeekString(weeksElem.content)
      if (fromPrimitive.isNotEmpty()) return fromPrimitive
    }
    return parseWeekString(weekStr)
  }

  fun parseWeekString(raw: String): Set<Int> {
    if (raw.isBlank()) return emptySet()
    val oddOnly = raw.contains("单")
    val evenOnly = raw.contains("双")
    val result = sortedSetOf<Int>()

    val segments = raw.split(',', '，', '、', ' ')
    for (seg in segments) {
      val trimmed = seg.trim()
      if (trimmed.isEmpty()) continue
      val rangeMatch = rangeRegex.find(trimmed)
      if (rangeMatch != null) {
        val s = rangeMatch.groupValues[1].toIntOrNull() ?: continue
        val e = rangeMatch.groupValues[2].toIntOrNull() ?: continue
        for (w in s..e) {
          if (oddOnly && w % 2 == 0) continue
          if (evenOnly && w % 2 != 0) continue
          result.add(w)
        }
      } else {
        for (m in singleNumRegex.findAll(trimmed)) {
          m.value.toIntOrNull()?.let { result.add(it) }
        }
      }
    }
    return result
  }

  fun parseExamCalendar(body: String, fallbackCalendarId: Long): ExamCalendarParsed {
    val root = json.parseToJsonElement(body).asObj() ?: JsonObject(emptyMap())
    val data = root["data"].asObj() ?: JsonObject(emptyMap())
    return ExamCalendarParsed(
      calendarId = data.longVal("calendarId") ?: fallbackCalendarId,
      examType = data.intVal("examType") ?: data.intVal("defaultType") ?: 1,
      isOpen = (data.intVal("isOpen") ?: 1) == 1,
      calendarName = data.str("calendarIdI18n"),
      remark = data.str("remark"),
    )
  }

  fun parseExamQueryResult(
    body: String,
    calendarName: String,
    isOpen: Boolean,
  ): ExamQueryResult {
    val root = json.parseToJsonElement(body).asObj() ?: JsonObject(emptyMap())
    val dataElem = root["data"]
    val listArray =
      when (dataElem) {
        is JsonArray -> dataElem
        is JsonObject -> (dataElem["list"] as? JsonArray) ?: (dataElem["rows"] as? JsonArray)
        else -> null
      }

    val exams =
      listArray?.mapNotNull { el ->
        val obj = el.asObj() ?: return@mapNotNull null
        val course = obj.firstStr("courseName", "kcmc", "courseTitle", "name")
        if (course.isBlank()) return@mapNotNull null
        ExamEntry(
          courseName = course,
          courseCode = obj.firstStr("courseCode", "kcdm", "newCourseCode"),
          examTime =
            obj.firstStr("examTime", "kssj", "examDateTime", "timeStr", "examDate").ifBlank {
              listOf(obj.str("date"), obj.str("startTime"), obj.str("endTime"))
                .filter { it.isNotBlank() }
                .joinToString(" ")
            },
          examRoom = obj.firstStr("examRoom", "ksdd", "roomName", "roomId", "address"),
          seatNo = obj.firstStr("seatNo", "zwh", "seatNumber"),
          campus = obj.firstStr("campusName", "campus", "xqName"),
          remark = obj.firstStr("remark", "examSituationName", "examTypeName"),
        )
      }.orEmpty()

    val serverMsg = root.str("message").ifBlank { root.str("msg") }
    val statusMsg =
      when {
        exams.isNotEmpty() -> ""
        serverMsg.isNotBlank() && !serverMsg.equals("ok", ignoreCase = true) -> serverMsg
        !isOpen -> "当前学期考试安排查询暂未开放"
        else -> "当前学期暂无考试安排"
      }

    return ExamQueryResult(
      calendarName = calendarName,
      isOpen = isOpen,
      statusMessage = statusMsg,
      exams = exams,
    )
  }

  fun parseGrades(body: String): GradeOverview {
    val data = unwrapDataObj(body)
    val termArray = data["term"] as? JsonArray ?: JsonArray(emptyList())

    val terms =
      termArray.mapNotNull { el ->
        val tObj = el.asObj() ?: return@mapNotNull null
        val creditArray = tObj["creditInfo"] as? JsonArray ?: JsonArray(emptyList())
        val courses =
          creditArray.mapNotNull { cEl ->
            val cObj = cEl.asObj() ?: return@mapNotNull null
            val name = cObj.firstStr("courseName", "kcmc", "courseTitle", "name")
            if (name.isBlank()) return@mapNotNull null
            val gp = cObj.firstStr("gradePoint", "point", "jd", "gpa")
            val passedRaw = cObj.firstStr("isPass", "passed", "passFlag", "isPassed")
            val isPassed =
              when {
                passedRaw.equals("true", true) ||
                  passedRaw == "1" ||
                  passedRaw == "是" ||
                  passedRaw.equals("Y", true) -> true
                passedRaw.equals("false", true) ||
                  passedRaw == "0" ||
                  passedRaw == "否" ||
                  passedRaw.equals("N", true) -> false
                else -> gp.toDoubleOrNull()?.let { it > 0.0 }
              }

            GradeCourseItem(
              courseName = name,
              courseCode = cObj.firstStr("courseCode", "kcdm", "code"),
              credits = cObj.firstStr("credit", "credits", "xf"),
              score = cObj.firstStr("score", "cj", "grade", "scoreName", "result"),
              gradePoint = gp,
              courseType = cObj.firstStr("courseLabelName", "courseType", "kcxz", "compulsory"),
              isPassed = isPassed,
            )
          }

        GradeTermSummary(
          termCode = tObj.longVal("termcode") ?: 0L,
          termName = tObj.str("termName").ifBlank { tObj.str("calName") },
          averagePoint = tObj.str("averagePoint"),
          courses = courses,
        )
      }

    return GradeOverview(
      totalGradePoint = data.str("totalGradePoint").ifBlank { "-" },
      actualCredit = data.str("actualCredit").ifBlank { "-" },
      failingCredits = data.str("failingCredits").ifBlank { "0" },
      failingCourseCount = data.str("failingCourseCount").ifBlank { "0" },
      terms = terms,
    )
  }

  private fun unwrapDataObj(body: String): JsonObject {
    val root = json.parseToJsonElement(body).asObj() ?: return JsonObject(emptyMap())
    return root["data"].asObj() ?: root
  }

  private fun unwrapDataArray(body: String): JsonArray {
    val elem = json.parseToJsonElement(body)
    if (elem is JsonArray) return elem
    val root = elem.asObj() ?: return JsonArray(emptyList())
    return (root["data"] as? JsonArray) ?: JsonArray(emptyList())
  }

  private fun JsonElement?.asObj(): JsonObject? = this as? JsonObject

  private fun JsonElement?.asPrimitive(): JsonPrimitive? = this as? JsonPrimitive

  private fun JsonObject.str(key: String): String {
    val el = this[key] ?: return ""
    if (el is JsonNull) return ""
    return el.asPrimitive()?.contentOrNull?.trim().orEmpty()
  }

  private fun JsonObject.firstStr(vararg keys: String): String {
    for (k in keys) {
      val v = str(k)
      if (v.isNotBlank()) return v
    }
    return ""
  }

  private fun JsonObject.intVal(key: String): Int? =
    this[key]?.asPrimitive()?.let { it.intOrNull ?: it.contentOrNull?.toIntOrNull() }

  private fun JsonObject.longVal(key: String): Long? =
    this[key]?.asPrimitive()?.let { it.longOrNull ?: it.contentOrNull?.toLongOrNull() }

  private fun JsonObject.doubleVal(key: String): Double? =
    this[key]?.asPrimitive()?.let { it.doubleOrNull ?: it.contentOrNull?.toDoubleOrNull() }

  private fun JsonObject.boolVal(key: String): Boolean? =
    this[key]?.asPrimitive()?.booleanOrNull
}
