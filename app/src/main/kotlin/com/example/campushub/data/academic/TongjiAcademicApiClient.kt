package com.example.campushub.data.academic

import android.webkit.CookieManager
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class AcademicAuthRequiredException(message: String) : Exception(message)

class TongjiAcademicApiClient {

  private val client =
    OkHttpClient.Builder()
      .connectTimeout(15, TimeUnit.SECONDS)
      .readTimeout(20, TimeUnit.SECONDS)
      .followRedirects(false)
      .followSslRedirects(false)
      .build()

  private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

  suspend fun fetchDashboard(): Result<AcademicDashboardData> =
    withContext(Dispatchers.IO) {
      runCatching {
        val ts = System.currentTimeMillis()
        val sessionBody = get("$BASE_URL/api/sessionservice/session/getSessionUser?_t=$ts")
        val sessionParsed = TongjiJsonParser.parseSessionUser(sessionBody)
        if (sessionParsed.uid.isBlank()) {
          throw AcademicAuthRequiredException("登录状态无效，请先在“网页登录”完成同济统一身份认证")
        }

        coroutineScope {
          val currentTermDeferred = async {
            val body =
              get("$BASE_URL/api/baseresservice/schoolCalendar/currentTermCalendar?flag=1&_t=$ts")
            TongjiJsonParser.parseCurrentTerm(body)
          }
          val termsDeferred = async {
            runCatching {
              val body = get("$BASE_URL/api/baseresservice/schoolCalendar/list?_t=$ts")
              TongjiJsonParser.parseCalendarTerms(body)
            }.getOrDefault(emptyList())
          }
          val noticesDeferred = async {
            runCatching {
              val body =
                postJson(
                  "$BASE_URL/api/commonservice/commonMsgPublish/findMyCommonMsgPublish",
                  """{"total":0,"pageNum_":1,"pageSize_":30}""",
                )
              TongjiJsonParser.parseNotices(body)
            }.getOrDefault(emptyList())
          }
          val gradesDeferred = async {
            runCatching {
              val body =
                get(
                  "$BASE_URL/api/scoremanagementservice/scoreGrades/getMyGrades?studentId=${sessionParsed.uid}&_t=$ts",
                )
              TongjiJsonParser.parseGrades(body)
            }.getOrNull()
          }
          val examCalendarDeferred = async {
            runCatching {
              val body =
                postJson(
                  "$BASE_URL/api/electionservice/underGraduateExamSwitch/getExamCalendar?examType=1&switchType=1",
                  "{}",
                )
              TongjiJsonParser.parseExamCalendar(body, 0L)
            }.getOrNull()
          }

          val currentTerm = currentTermDeferred.await()
          val terms = termsDeferred.await()
          val calendarId =
            currentTerm.calendarId.takeIf { it > 0L }
              ?: terms.firstOrNull { it.isCurrent }?.id
              ?: terms.firstOrNull()?.id
              ?: 0L

          val periodDeferred = async {
            if (calendarId <= 0L) emptyMap()
            else
              runCatching {
                val body =
                  get("$BASE_URL/api/baseresservice/schoolCalendar/detail?id=$calendarId&_t=$ts")
                TongjiJsonParser.parsePeriodTimes(body)
              }.getOrDefault(emptyMap())
          }

          val timetableDeferred = async {
            if (calendarId <= 0L) emptyList()
            else
              runCatching {
                val body =
                  get(
                    "$BASE_URL/api/electionservice/reportManagement/findStudentTimetab?calendarId=$calendarId&studentCode=${sessionParsed.uid}&_t=$ts",
                  )
                TongjiJsonParser.parseTimetable(body)
              }.getOrDefault(emptyList())
          }

          val examCal = examCalendarDeferred.await()
          val examCalId = examCal?.calendarId?.takeIf { it > 0L } ?: calendarId
          val examResultDeferred = async {
            if (examCalId <= 0L) {
              ExamQueryResult("", false, "暂无当前学期校历信息", emptyList())
            } else {
              runCatching {
                val examType = examCal?.examType ?: 1
                val body =
                  postJson(
                    "$BASE_URL/api/electionservice/undergraduateExamQuery/getStudentListPage",
                    """{"pageNum_":1,"pageSize_":50,"condition":{"calendarId":$examCalId,"examSituation":"","examType":$examType}}""",
                  )
                TongjiJsonParser.parseExamQueryResult(
                  body = body,
                  calendarName = examCal?.calendarName.orEmpty().ifBlank { currentTerm.termName },
                  isOpen = examCal?.isOpen ?: true,
                )
              }.getOrElse { err ->
                ExamQueryResult(
                  calendarName = currentTerm.termName,
                  isOpen = false,
                  statusMessage = err.message ?: "考试安排暂不可用",
                  exams = emptyList(),
                )
              }
            }
          }

          AcademicDashboardData(
            session =
              AcademicSessionInfo(
                uid = sessionParsed.uid,
                studentName = sessionParsed.name,
                facultyName = sessionParsed.facultyName,
                majorName = sessionParsed.majorName,
                currentCalendarId = calendarId,
                currentTermName = currentTerm.termName,
                currentWeek = currentTerm.currentWeek,
                totalWeeks = currentTerm.totalWeeks,
              ),
            terms = terms,
            periodTimes = periodDeferred.await(),
            notices = noticesDeferred.await(),
            timetable = timetableDeferred.await(),
            examResult = examResultDeferred.await(),
            grades = gradesDeferred.await(),
          )
        }
      }
    }

  suspend fun fetchTimetableForTerm(
    calendarId: Long,
    studentCode: String,
  ): Result<Pair<Map<Int, ClassPeriodTime>, List<TimetableEntry>>> =
    withContext(Dispatchers.IO) {
      runCatching {
        val ts = System.currentTimeMillis()
        coroutineScope {
          val periodsDeferred = async {
            runCatching {
              val body =
                get("$BASE_URL/api/baseresservice/schoolCalendar/detail?id=$calendarId&_t=$ts")
              TongjiJsonParser.parsePeriodTimes(body)
            }.getOrDefault(emptyMap())
          }
          val timetableDeferred = async {
            val body =
              get(
                "$BASE_URL/api/electionservice/reportManagement/findStudentTimetab?calendarId=$calendarId&studentCode=$studentCode&_t=$ts",
              )
            TongjiJsonParser.parseTimetable(body)
          }
          periodsDeferred.await() to timetableDeferred.await()
        }
      }
    }

  private fun get(url: String): String {
    val request = baseRequestBuilder(url).get().build()
    return executeJson(request)
  }

  private fun postJson(url: String, jsonBody: String): String {
    val request = baseRequestBuilder(url).post(jsonBody.toRequestBody(jsonMediaType)).build()
    return executeJson(request)
  }

  private fun baseRequestBuilder(url: String): Request.Builder {
    val cookies = CookieManager.getInstance().getCookie(BASE_URL).orEmpty()
    if (cookies.isBlank()) {
      throw AcademicAuthRequiredException("尚未登录同济教务系统，请先切到“网页登录”完成登录")
    }
    return Request.Builder()
      .url(url)
      .header("Cookie", cookies)
      .header("Accept", "application/json, text/plain, */*")
      .header("Origin", BASE_URL)
      .header("Referer", "$BASE_URL/workbench")
  }

  private fun executeJson(request: Request): String {
    client.newCall(request).execute().use { response ->
      if (response.code in 300..399) {
        val location = response.header("Location").orEmpty()
        if (location.contains("ids.tongji.edu.cn", ignoreCase = true) ||
          location.contains("login", ignoreCase = true)
        ) {
          throw AcademicAuthRequiredException("登录态已过期，请切到“网页登录”重新认证")
        }
      }
      if (response.code == 401 || response.code == 403) {
        throw AcademicAuthRequiredException("教务会话未授权（HTTP ${response.code}），请在“网页登录”重新登录")
      }
      if (!response.isSuccessful) {
        throw IOException("教务接口异常（HTTP ${response.code}）")
      }
      val body = response.body?.string().orEmpty()
      val trimmed = body.trimStart()
      if (trimmed.startsWith("<")) {
        throw AcademicAuthRequiredException("登录态已失效，请切到“网页登录”完成验证")
      }
      return body
    }
  }

  companion object {
    const val BASE_URL = "https://1.tongji.edu.cn"
  }
}
