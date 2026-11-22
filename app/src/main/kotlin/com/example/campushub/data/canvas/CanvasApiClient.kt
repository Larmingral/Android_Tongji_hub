package com.example.campushub.data.canvas

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

class CanvasApiClient {

  private val client =
    OkHttpClient.Builder()
      .connectTimeout(15, TimeUnit.SECONDS)
      .readTimeout(20, TimeUnit.SECONDS)
      .build()

  suspend fun fetchDashboard(baseUrl: String, token: String): Result<CanvasDashboardData> =
    withContext(Dispatchers.IO) {
      val cleanBase = CanvasConfigStore.normalizeBaseUrl(baseUrl)
      val cleanToken = token.trim()
      if (cleanToken.isEmpty()) {
        return@withContext Result.failure(IllegalArgumentException("请先输入 Canvas Access Token"))
      }

      try {
        val userJson = get("$cleanBase/api/v1/users/self", cleanToken)
        val user = CanvasJsonParser.parseUser(userJson)

        val coursesJson =
          runCatching {
            get("$cleanBase/api/v1/courses?enrollment_state=active&per_page=50", cleanToken)
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
              )
            CanvasJsonParser.parsePlannerItems(plannerJson, cleanBase)
          }.getOrDefault(emptyList())

        val todoTasks =
          if (plannerTasks.isEmpty()) {
            runCatching {
              val todoJson = get("$cleanBase/api/v1/users/self/todo?per_page=50", cleanToken)
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
          CanvasDashboardData(user = user, courses = courses, tasks = mergedTasks)
        )
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
      runCatching {
        val body =
          get(
            "$cleanBase/api/v1/courses/$courseId/assignments/$assignmentId?include[]=submission",
            cleanToken,
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
      runCatching {
        coroutineScope {
          val announcementsDeferred = async {
            runCatching {
              val body =
                get(
                  "$cleanBase/api/v1/announcements?context_codes[]=course_${course.id}&per_page=30",
                  cleanToken,
                )
              CanvasJsonParser.parseAnnouncements(body, cleanBase)
            }.getOrDefault(emptyList())
          }

          val assignmentsDeferred = async {
            runCatching {
              val body =
                get(
                  "$cleanBase/api/v1/courses/${course.id}/assignments?include[]=submission&order_by=due_at&per_page=50",
                  cleanToken,
                )
              CanvasJsonParser.parseCourseAssignments(body, course, cleanBase)
            }.getOrDefault(emptyList())
          }

          val modulesDeferred = async {
            runCatching {
              val body =
                get(
                  "$cleanBase/api/v1/courses/${course.id}/modules?include[]=items&per_page=50",
                  cleanToken,
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
                )
              CanvasJsonParser.parseFiles(body)
            }.getOrDefault(emptyList())
          }

          CanvasCourseDetailData(
            course = course,
            announcements = announcementsDeferred.await(),
            assignments = assignmentsDeferred.await(),
            modules = modulesDeferred.await(),
            files = filesDeferred.await(),
          )
        }
      }
    }

  suspend fun resolveFileByApiUrl(apiUrl: String, token: String): Result<CanvasFileItem> =
    withContext(Dispatchers.IO) {
      runCatching {
        val body = get(apiUrl, token.trim())
        CanvasJsonParser.parseSingleFile(body)
      }
    }

  suspend fun downloadFile(downloadUrl: String, token: String, destFile: File): Result<File> =
    withContext(Dispatchers.IO) {
      runCatching {
        val request =
          Request.Builder()
            .url(downloadUrl)
            .header("Authorization", "Bearer ${token.trim()}")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
          if (!response.isSuccessful) {
            throw CanvasHttpException(response.code, "下载文件失败（HTTP ${response.code}）")
          }
          val body = response.body ?: throw IOException("响应内容为空")
          destFile.parentFile?.mkdirs()
          body.byteStream().use { input ->
            destFile.outputStream().use { output -> input.copyTo(output) }
          }
        }
        destFile
      }
    }

  private fun get(url: String, token: String): String {
    val request =
      Request.Builder()
        .url(url)
        .header("Authorization", "Bearer $token")
        .header("Accept", "application/json")
        .get()
        .build()

    client.newCall(request).execute().use { response ->
      val body = response.body?.string().orEmpty()
      if (!response.isSuccessful) {
        val message =
          when (response.code) {
            401 -> "Token 无效或已过期（HTTP 401），请重新生成后粘贴"
            403 -> "学校限制了该 API 接口访问（HTTP 403），可点击右上方网页按钮兜底查看"
            404 -> "接口不存在或当前 Token 无法访问（HTTP 404）"
            else -> "Canvas 接口请求失败（HTTP ${response.code}）"
          }
        throw CanvasHttpException(response.code, message)
      }
      return body
    }
  }
}

class CanvasHttpException(val statusCode: Int, message: String) : Exception(message)
