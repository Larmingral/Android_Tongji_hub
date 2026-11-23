package com.example.campushub.data.academic

import android.webkit.CookieManager
import java.io.File
import java.io.IOException
import java.net.URLEncoder
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

  private val downloadClient =
    client.newBuilder()
      .followRedirects(true)
      .followSslRedirects(true)
      .build()

  private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

  suspend fun hasValidSession(): Boolean =
    withContext(Dispatchers.IO) {
      runCatching {
        val ts = System.currentTimeMillis()
        val sessionBody = get("$BASE_URL/api/sessionservice/session/getSessionUser?_t=$ts")
        TongjiJsonParser.parseSessionUser(sessionBody).uid.isNotBlank()
      }.getOrDefault(false)
    }

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

          val timetableResultDeferred = async {
            if (calendarId <= 0L) {
              Result.failure(IllegalStateException("未能获取当前学期 calendarId"))
            } else {
              runCatching {
                activateMenuAuthIfMatched(sessionParsed.auths, "TimeTable", "课表")
                val encStudentCode =
                  URLEncoder.encode(TongjiCrypto.staticEncrypt(sessionParsed.uid), "UTF-8")
                val body =
                  get(
                    "$BASE_URL/api/electionservice/reportManagement/findStudentTimetab?calendarId=$calendarId&studentCode=$encStudentCode&_t=$ts",
                  )
                TongjiJsonParser.parseTimetable(body)
              }
            }
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

          val timetableResult = timetableResultDeferred.await()
          val timetableEntries = timetableResult.getOrDefault(emptyList())
          val timetableError =
            timetableResult.exceptionOrNull()?.let { err ->
              "加载课表失败 (calendarId=$calendarId)：${err.message ?: "未知错误"}"
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
                auths = sessionParsed.auths,
                aesKey = sessionParsed.aesKey,
                aesIv = sessionParsed.aesIv,
                sessionId = sessionParsed.sessionId,
              ),
            terms = terms,
            periodTimes = periodDeferred.await(),
            notices = noticesDeferred.await(),
            timetable = timetableEntries,
            timetableError = timetableError,
            examResult = examResultDeferred.await(),
            grades = gradesDeferred.await(),
          )
        }
      }
    }

  suspend fun fetchNoticeDetail(notice: AcademicNotice): Result<AcademicNotice> =
    withContext(Dispatchers.IO) {
      runCatching {
        val ts = System.currentTimeMillis()
        val body =
          get("$BASE_URL/api/commonservice/commonMsgPublish/findCommonMsgPublishById?id=${notice.id}&_t=$ts")
        val detailed = TongjiJsonParser.parseNoticeDetail(body, notice)
        runCatching {
          postJson(
            "$BASE_URL/api/commonservice/commonMsgPublish/markRead",
            """{"ids":[${notice.id}]}""",
          )
        }
        detailed
      }
    }

  suspend fun fetchTimetableForTerm(
    calendarId: Long,
    studentCode: String,
    auths: List<AcademicMenuAuth>,
  ): Result<Pair<Map<Int, ClassPeriodTime>, List<TimetableEntry>>> =
    withContext(Dispatchers.IO) {
      runCatching {
        val ts = System.currentTimeMillis()
        activateMenuAuthIfMatched(auths, "TimeTable", "课表")
        coroutineScope {
          val periodsDeferred = async {
            runCatching {
              val body =
                get("$BASE_URL/api/baseresservice/schoolCalendar/detail?id=$calendarId&_t=$ts")
              TongjiJsonParser.parsePeriodTimes(body)
            }.getOrDefault(emptyMap())
          }
          val timetableDeferred = async {
            val encStudentCode =
              URLEncoder.encode(TongjiCrypto.staticEncrypt(studentCode), "UTF-8")
            val body =
              get(
                "$BASE_URL/api/electionservice/reportManagement/findStudentTimetab?calendarId=$calendarId&studentCode=$encStudentCode&_t=$ts",
              )
            TongjiJsonParser.parseTimetable(body)
          }
          periodsDeferred.await() to timetableDeferred.await()
        }
      }
    }

  suspend fun downloadAttachment(
    attachment: AcademicAttachment,
    session: AcademicSessionInfo?,
    destFile: File,
  ): Result<File> =
    withContext(Dispatchers.IO) {
      runCatching {
        val rawLoc = attachment.fileLocation.trim()
        if (rawLoc.isEmpty()) {
          throw IOException("附件下载地址为空，无法下载")
        }
        if (rawLoc.startsWith("http://") || rawLoc.startsWith("https://")) {
          return@runCatching downloadBinaryToFile(rawLoc, session?.sessionId.orEmpty(), destFile)
        }

        val ts = System.currentTimeMillis()
        val candidates = buildList {
          if (session != null && session.aesKey.isNotBlank() && session.aesIv.isNotBlank()) {
            val enc = TongjiCrypto.sessionEncrypt(rawLoc, session.aesKey, session.aesIv)
            add("$BASE_URL/api/commonservice/obsfile/downloadfile?objectkey=${URLEncoder.encode(enc, "UTF-8")}&_t=$ts")
          }
          val staticEnc = TongjiCrypto.staticEncrypt(rawLoc)
          add("$BASE_URL/api/commonservice/obsfile/downloadfile?objectkey=${URLEncoder.encode(staticEnc, "UTF-8")}&_t=$ts")
          add("$BASE_URL/api/commonservice/obsfile/downloadfile?objectkey=${URLEncoder.encode(rawLoc, "UTF-8")}&_t=$ts")
        }.distinct()

        var lastError: Throwable? = null
        for (url in candidates) {
          val attempt = runCatching {
            downloadBinaryToFile(url, session?.sessionId.orEmpty(), destFile)
          }
          if (attempt.isSuccess) return@runCatching attempt.getOrThrow()
          val err = attempt.exceptionOrNull()
          if (err is AcademicAuthRequiredException) throw err
          lastError = err
        }
        throw lastError ?: IOException("下载附件失败")
      }
    }

  suspend fun downloadUrlWithSession(
    url: String,
    session: AcademicSessionInfo?,
    destFile: File,
  ): Result<File> =
    withContext(Dispatchers.IO) {
      runCatching {
        downloadBinaryToFile(url, session?.sessionId.orEmpty(), destFile)
      }
    }

  private fun downloadBinaryToFile(
    url: String,
    sessionId: String,
    destFile: File,
  ): File {
    val cookies = CookieManager.getInstance().getCookie(BASE_URL).orEmpty()
    if (cookies.isBlank()) {
      throw AcademicAuthRequiredException("尚未登录同济教务系统，请保存账密自动续期或网页验证")
    }
    val reqBuilder =
      Request.Builder()
        .url(url)
        .header("Cookie", cookies)
        .header("Origin", BASE_URL)
        .header("Referer", "$BASE_URL/workbench")
        .get()
    if (sessionId.isNotBlank()) {
      reqBuilder.header("X-Token", sessionId)
    }

    downloadClient.newCall(reqBuilder.build()).execute().use { response ->
      val finalUrl = response.request.url.toString().lowercase()
      if (finalUrl.contains("ids.tongji.edu.cn") ||
        finalUrl.contains("iam.tongji.edu.cn") ||
        finalUrl.contains("/login")
      ) {
        throw AcademicAuthRequiredException("教务登录态已过期，需要重新认证后下载")
      }
      if (response.code == 401 || response.code == 403) {
        throw AcademicAuthRequiredException("教务会话未授权（HTTP ${response.code}），需要重新认证")
      }
      if (!response.isSuccessful) {
        throw IOException("附件下载失败（HTTP ${response.code}）")
      }
      val contentType = response.header("Content-Type").orEmpty().lowercase()
      if (contentType.contains("text/html")) {
        throw AcademicAuthRequiredException("登录态已失效，附件接口返回了登录页面")
      }
      if (contentType.contains("application/json")) {
        val msg = response.body?.string().orEmpty()
        throw IOException("附件下载接口返回错误：${msg.take(120)}")
      }
      val body = response.body ?: throw IOException("附件内容为空")
      destFile.parentFile?.mkdirs()
      body.byteStream().use { input ->
        destFile.outputStream().use { output -> input.copyTo(output) }
      }
      return destFile
    }
  }

  private fun activateMenuAuthIfMatched(
    auths: List<AcademicMenuAuth>,
    urlKeyword: String,
    nameKeyword: String,
  ) {
    val matched =
      auths.firstOrNull {
        it.urlPath.contains(urlKeyword, ignoreCase = true) ||
          it.authNameCh.contains(nameKeyword)
      } ?: return
    runCatching {
      postJson(
        "$BASE_URL/api/sessionservice/session/currentAuthId",
        """{"authId":${matched.authId}}""",
      )
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
      throw AcademicAuthRequiredException("尚未登录同济教务系统，请保存账密自动续期或网页验证")
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
          location.contains("iam.tongji.edu.cn", ignoreCase = true) ||
          location.contains("login", ignoreCase = true)
        ) {
          throw AcademicAuthRequiredException("登录态已过期，请重新认证")
        }
      }
      if (response.code == 401 || response.code == 403) {
        throw AcademicAuthRequiredException("教务会话未授权（HTTP ${response.code}），请重新认证")
      }
      if (!response.isSuccessful) {
        throw IOException("教务接口异常（HTTP ${response.code}）")
      }
      val body = response.body?.string().orEmpty()
      val trimmed = body.trimStart()
      if (trimmed.startsWith("<")) {
        throw AcademicAuthRequiredException("登录态已失效，请重新验证")
      }
      return body
    }
  }

  companion object {
    const val BASE_URL = "https://1.tongji.edu.cn"
  }
}
