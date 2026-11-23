package com.example.campushub.data.canvas

import android.webkit.CookieManager
import java.io.File
import java.io.IOException
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

class CanvasAuthRequiredException(message: String) : Exception(message)

class CanvasHttpException(val statusCode: Int, message: String) : Exception(message)

class CanvasApiClient(
  baseClient: OkHttpClient =
    OkHttpClient.Builder()
      .connectTimeout(15, TimeUnit.SECONDS)
      .readTimeout(20, TimeUnit.SECONDS)
      .build(),
  private val cookieProvider: (String) -> String? = { url ->
    runCatching { CookieManager.getInstance().getCookie(url) }.getOrNull()
  },
) {

  private val apiClient: OkHttpClient =
    baseClient.newBuilder()
      .followRedirects(false)
      .followSslRedirects(false)
      .build()

  private val downloadClient: OkHttpClient = baseClient

  private class AuthState(cleanToken: String) {
    @Volatile var useCookieOnly: Boolean = cleanToken.isEmpty()
    @Volatile var switchedFromFailedToken: Boolean = false
  }

  suspend fun hasValidCookieSession(baseUrl: String): Boolean =
    withContext(Dispatchers.IO) {
      val cleanBase = CanvasConfigStore.normalizeBaseUrl(baseUrl)
      runCatching {
        val cookies = cookieProvider(cleanBase).orEmpty()
        if (cookies.isBlank()) return@runCatching false
        val body = getWithCookie("$cleanBase/api/v1/users/self", cookies, hadToken = false)
        CanvasJsonParser.parseUser(body).id > 0L
      }.getOrDefault(false)
    }

  suspend fun fetchDashboard(baseUrl: String, token: String): Result<CanvasDashboardData> =
    withContext(Dispatchers.IO) {
      val cleanBase = CanvasConfigStore.normalizeBaseUrl(baseUrl)
      val cleanToken = token.trim()
      val authState = AuthState(cleanToken)

      try {
        val userJson =
          get("$cleanBase/api/v1/users/self", cleanToken, cleanBase, authState)
        val user = CanvasJsonParser.parseUser(userJson)

        val coursesJson =
          runCatching {
            get("$cleanBase/api/v1/courses?enrollment_state=active&per_page=50", cleanToken, cleanBase, authState)
          }.getOrDefault("[]")
        val courses = CanvasJsonParser.parseCourses(coursesJson)

        val startDate = LocalDate.now().minusDays(30).format(DateTimeFormatter.ISO_LOCAL_DATE)
        val endDate = LocalDate.now().plusDays(60).format(DateTimeFormatter.ISO_LOCAL_DATE)

        val plannerTasks =
          runCatching {
            val plannerJson =
              get(
                "$cleanBase/api/v1/planner/items?start_date=$startDate&end_date=$endDate&per_page=100",
                cleanToken,
                cleanBase,
                authState,
              )
            CanvasJsonParser.parsePlannerItems(plannerJson, cleanBase)
          }.getOrDefault(emptyList())

        val todoTasks =
          if (plannerTasks.isEmpty()) {
            runCatching {
              val todoJson =
                get("$cleanBase/api/v1/users/self/todo?per_page=50", cleanToken, cleanBase, authState)
              CanvasJsonParser.parseTodoItems(todoJson, cleanBase)
            }.getOrDefault(emptyList())
          } else {
            emptyList()
          }

        // Fetch per-course assignments concurrently so completed/submitted tasks are always included.
        val courseAssignments =
          coroutineScope {
            courses.take(12).map { course ->
              async {
                runCatching {
                  val body =
                    get(
                      "$cleanBase/api/v1/courses/${course.id}/assignments?include[]=submission&per_page=50",
                      cleanToken,
                      cleanBase,
                      authState,
                    )
                  CanvasJsonParser.parseCourseAssignments(body, course, cleanBase)
                }.getOrDefault(emptyList())
              }
            }.awaitAll().flatten()
          }

        val mergedTasks =
          CanvasJsonParser.mergeDashboardTasks(
            plannerTasks = plannerTasks.ifEmpty { todoTasks },
            courseAssignments = courseAssignments,
          )

        Result.success(
          CanvasDashboardData(
            user = user,
            courses = courses,
            tasks = mergedTasks,
            usedCookieFallback = authState.switchedFromFailedToken,
          )
        )
      } catch (e: CanvasAuthRequiredException) {
        Result.failure(e)
      } catch (e: CanvasHttpException) {
        Result.failure(e)
      } catch (e: IOException) {
        Result.failure(IOException("网络请求失败：${e.message ?: "请检查网络连接"}", e))
      } catch (e: Exception) {
        Result.failure(Exception("解析 Canvas 数据失败：${e.message ?: "未知错误"}", e))
      }
    }

  suspend fun fetchAssignmentDetail(
    baseUrl: String,
    token: String,
    courseId: Long,
    assignmentId: Long,
    courseName: String,
  ): Result<CanvasAssignmentDetail> =
    withContext(Dispatchers.IO) {
      val cleanBase = CanvasConfigStore.normalizeBaseUrl(baseUrl)
      val cleanToken = token.trim()
      val authState = AuthState(cleanToken)
      runCatching {
        val body =
          get(
            "$cleanBase/api/v1/courses/$courseId/assignments/$assignmentId?include[]=submission",
            cleanToken,
            cleanBase,
            authState,
          )
        CanvasJsonParser.parseAssignmentDetail(body, courseId, courseName, cleanBase)
      }
    }

  suspend fun fetchCourseDetail(
    baseUrl: String,
    token: String,
    course: CanvasCourse,
  ): Result<CanvasCourseDetailData> =
    withContext(Dispatchers.IO) {
      val cleanBase = CanvasConfigStore.normalizeBaseUrl(baseUrl)
      val cleanToken = token.trim()
      val authState = AuthState(cleanToken)
      runCatching {
        // Probe once on assignments so sub-calls reuse authState and 403/404 remains non-fatal.
        val parsedAssignments =
          runCatching {
            val body =
              get(
                "$cleanBase/api/v1/courses/${course.id}/assignments?include[]=submission&order_by=due_at&per_page=50",
                cleanToken,
                cleanBase,
                authState,
              )
            CanvasJsonParser.parseCourseAssignments(body, course, cleanBase)
          }.onFailure { err ->
            if (err is CanvasAuthRequiredException) throw err
          }.getOrDefault(emptyList())

        coroutineScope {
          val announcementsDeferred = async {
            runCatching {
              val body =
                get(
                  "$cleanBase/api/v1/announcements?context_codes[]=course_${course.id}&per_page=30",
                  cleanToken,
                  cleanBase,
                  authState,
                )
              CanvasJsonParser.parseAnnouncements(body, cleanBase)
            }.getOrDefault(emptyList())
          }

          val modulesDeferred = async {
            runCatching {
              val body =
                get(
                  "$cleanBase/api/v1/courses/${course.id}/modules?include[]=items&per_page=50",
                  cleanToken,
                  cleanBase,
                  authState,
                )
              CanvasJsonParser.parseModules(body, cleanBase)
            }.getOrDefault(emptyList())
          }

          val filesDeferred = async {
            runCatching {
              val body =
                get(
                  "$cleanBase/api/v1/courses/${course.id}/files?sort=updated_at&order=desc&per_page=50",
                  cleanToken,
                  cleanBase,
                  authState,
                )
              CanvasJsonParser.parseFiles(body)
            }.getOrDefault(emptyList())
          }

          CanvasCourseDetailData(
            course = course,
            announcements = announcementsDeferred.await(),
            assignments = parsedAssignments,
            modules = modulesDeferred.await(),
            files = filesDeferred.await(),
          )
        }
      }
    }

  suspend fun resolveFileByApiUrl(
    apiUrl: String,
    token: String,
    baseUrl: String = CanvasConfigStore.DEFAULT_CANVAS_URL,
  ): Result<CanvasFileItem> =
    withContext(Dispatchers.IO) {
      val cleanBase = CanvasConfigStore.normalizeBaseUrl(baseUrl)
      val cleanToken = token.trim()
      val authState = AuthState(cleanToken)
      runCatching {
        val body = get(apiUrl, cleanToken, cleanBase, authState)
        CanvasJsonParser.parseSingleFile(body)
      }
    }

  suspend fun downloadFile(
    downloadUrl: String,
    token: String,
    destFile: File,
    baseUrl: String = CanvasConfigStore.DEFAULT_CANVAS_URL,
  ): Result<File> =
    withContext(Dispatchers.IO) {
      val cleanBase = CanvasConfigStore.normalizeBaseUrl(baseUrl)
      val cleanToken = token.trim()
      runCatching {
        if (cleanToken.isNotEmpty()) {
          val bearerReq =
            Request.Builder()
              .url(downloadUrl)
              .header("Authorization", "Bearer $cleanToken")
              .get()
              .build()
          downloadClient.newCall(bearerReq).execute().use { response ->
            if (response.isSuccessful && !isLoginLanding(response)) {
              writeResponseToFile(response, destFile)
              return@runCatching destFile
            }
            if (response.code != 401 && !isLoginLanding(response)) {
              throw CanvasHttpException(response.code, "下载文件失败（HTTP ${response.code}）")
            }
          }
        }

        val cookies = resolveCookies(downloadUrl, cleanBase)
        if (cookies.isBlank()) {
          throw CanvasAuthRequiredException("下载课件需要登录 Canvas，请先保存统一认证账密或在网页端登录")
        }
        val cookieReq =
          Request.Builder()
            .url(downloadUrl)
            .header("Cookie", cookies)
            .get()
            .build()
        downloadClient.newCall(cookieReq).execute().use { response ->
          if (response.code == 401 || isLoginLanding(response)) {
            throw CanvasAuthRequiredException("Canvas 会话已失效，需要重新认证后下载")
          }
          if (!response.isSuccessful) {
            throw CanvasHttpException(response.code, "下载文件失败（HTTP ${response.code}）")
          }
          writeResponseToFile(response, destFile)
        }
        destFile
      }
    }

  private fun writeResponseToFile(response: Response, destFile: File) {
    val body = response.body ?: throw IOException("响应内容为空")
    destFile.parentFile?.mkdirs()
    body.byteStream().use { input ->
      destFile.outputStream().use { output -> input.copyTo(output) }
    }
  }

  private fun isLoginLanding(response: Response): Boolean {
    val finalUrl = response.request.url.toString().lowercase()
    if (finalUrl.contains("iam.tongji.edu.cn") ||
      finalUrl.contains("ids.tongji.edu.cn") ||
      finalUrl.contains("/login")
    ) {
      return true
    }
    val contentType = response.header("Content-Type").orEmpty().lowercase()
    return contentType.contains("text/html")
  }

  private fun get(
    url: String,
    token: String,
    baseUrl: String,
    authState: AuthState,
  ): String {
    if (!authState.useCookieOnly && token.isNotEmpty()) {
      val bearerRequest =
        Request.Builder()
          .url(url)
          .header("Authorization", "Bearer $token")
          .header("Accept", "application/json")
          .get()
          .build()

      apiClient.newCall(bearerRequest).execute().use { response ->
        val body = response.body?.string().orEmpty()
        if (response.isSuccessful && !body.trimStart().startsWith("<")) {
          return body
        }
        if (response.code == 401 || isAuthRedirect(response) || body.trimStart().startsWith("<")) {
          authState.useCookieOnly = true
          authState.switchedFromFailedToken = true
        } else {
          throw toHttpException(response.code)
        }
      }
    }

    val cookies = resolveCookies(url, baseUrl)
    if (cookies.isBlank()) {
      val reason =
        if (token.isNotEmpty()) {
          "Token 返回未授权（HTTP 401）且本机尚无 Canvas 网页会话，请配置统一认证自动续期或先到网页端登录一次"
        } else {
          "尚未登录 Canvas，请配置统一认证自动登录或先到“内置免登网页”登录一次"
        }
      throw CanvasAuthRequiredException(reason)
    }

    return getWithCookie(url, cookies, hadToken = token.isNotEmpty())
  }

  private fun getWithCookie(url: String, cookies: String, hadToken: Boolean): String {
    val cookieRequest =
      Request.Builder()
        .url(url)
        .header("Cookie", cookies)
        .header("Accept", "application/json")
        .get()
        .build()

    apiClient.newCall(cookieRequest).execute().use { response ->
      if (isAuthRedirect(response) || response.code == 401) {
        val prefix = if (hadToken) "Token 已失效且网页会话已过期" else "Canvas 网页会话已过期"
        throw CanvasAuthRequiredException("$prefix（HTTP ${response.code}），需要重新认证")
      }
      val body = response.body?.string().orEmpty()
      if (!response.isSuccessful) {
        throw toHttpException(response.code)
      }
      if (body.trimStart().startsWith("<")) {
        throw CanvasAuthRequiredException("Canvas 返回登录页面，需要重新认证")
      }
      return body
    }
  }

  private fun resolveCookies(url: String, baseUrl: String): String =
    cookieProvider(url)?.takeIf { it.isNotBlank() }
      ?: cookieProvider(baseUrl)?.takeIf { it.isNotBlank() }
      ?: ""

  private fun isAuthRedirect(response: Response): Boolean {
    if (response.code !in 300..399) return false
    val location = response.header("Location").orEmpty().lowercase()
    return location.contains("/login") ||
      location.contains("iam.tongji.edu.cn") ||
      location.contains("ids.tongji.edu.cn")
  }

  private fun toHttpException(statusCode: Int): CanvasHttpException {
    val message =
      when (statusCode) {
        401 -> "未授权（HTTP 401）"
        403 -> "学校限制了该 API 接口访问（HTTP 403），可切到“内置免登网页”查看"
        404 -> "接口不存在或无访问权限（HTTP 404）"
        else -> "Canvas 接口请求失败（HTTP $statusCode）"
      }
    return CanvasHttpException(statusCode, message)
  }
}
