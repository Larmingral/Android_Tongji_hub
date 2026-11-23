package com.example.campushub

import com.example.campushub.data.canvas.CanvasApiClient
import com.example.campushub.data.canvas.CanvasAuthRequiredException
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CanvasAuthFallbackTest {

  private val jsonType = "application/json; charset=utf-8".toMediaType()

  @Test
  fun fetchDashboard_fallsBackToCookieWhenBearerReturns401() = runTest {
    val bearerCalls = AtomicInteger(0)
    val cookieCalls = AtomicInteger(0)

    val interceptor = Interceptor { chain ->
      val req = chain.request()
      val auth = req.header("Authorization")
      val cookie = req.header("Cookie")
      val path = req.url.encodedPath

      if (auth == "Bearer expired_token") {
        bearerCalls.incrementAndGet()
        return@Interceptor jsonResponse(req, 401, """{"errors":[{"message":"Invalid access token."}]}""")
      }

      if (cookie == "canvas_session=valid_demo_cookie") {
        cookieCalls.incrementAndGet()
        return@Interceptor when {
          path.endsWith("/api/v1/users/self") ->
            jsonResponse(req, 200, """{"id":1001,"name":"测试同学"}""")
          path.endsWith("/api/v1/courses") ->
            jsonResponse(req, 200, """[{"id":201,"name":"高等数学","course_code":"MATH101"}]""")
          path.endsWith("/api/v1/planner/items") ->
            jsonResponse(req, 200, """[]""")
          path.endsWith("/api/v1/courses/201/assignments") ->
            jsonResponse(req, 200, """[]""")
          else -> jsonResponse(req, 404, """{}""")
        }
      }

      jsonResponse(req, 401, """{"errors":[{"message":"unauthorized"}]}""")
    }

    val apiClient =
      CanvasApiClient(
        baseClient = OkHttpClient.Builder().addInterceptor(interceptor).build(),
        cookieProvider = { "canvas_session=valid_demo_cookie" },
      )

    val result = apiClient.fetchDashboard("https://canvas.example.edu", "expired_token")
    assertTrue("Expected fallback dashboard fetch to succeed", result.isSuccess)
    val data = result.getOrThrow()
    assertEquals("测试同学", data.user.name)
    assertEquals(1, data.courses.size)
    assertTrue(data.usedCookieFallback)
    assertEquals("Only the initial probe should attempt the failing Bearer token", 1, bearerCalls.get())
    assertTrue(cookieCalls.get() >= 3)
  }

  @Test
  fun fetchDashboard_cookieOnlyModeWorksWithoutToken() = runTest {
    val interceptor = Interceptor { chain ->
      val req = chain.request()
      assertEquals(null, req.header("Authorization"))
      assertEquals("canvas_session=cookie_only", req.header("Cookie"))
      when {
        req.url.encodedPath.endsWith("/api/v1/users/self") ->
          jsonResponse(req, 200, """{"id":1002,"name":"免密用户"}""")
        else -> jsonResponse(req, 200, """[]""")
      }
    }

    val apiClient =
      CanvasApiClient(
        baseClient = OkHttpClient.Builder().addInterceptor(interceptor).build(),
        cookieProvider = { "canvas_session=cookie_only" },
      )

    val result = apiClient.fetchDashboard("https://canvas.example.edu", "")
    assertTrue(result.isSuccess)
    val data = result.getOrThrow()
    assertEquals("免密用户", data.user.name)
    assertFalse(data.usedCookieFallback)
  }

  @Test
  fun fetchDashboard_throwsCanvasAuthRequiredWhenBearer401AndCookieRedirectsToLogin() = runTest {
    val interceptor = Interceptor { chain ->
      val req = chain.request()
      if (req.header("Authorization") != null) {
        return@Interceptor jsonResponse(req, 401, """{"errors":[{"message":"Invalid access token."}]}""")
      }
      Response.Builder()
        .request(req)
        .protocol(Protocol.HTTP_1_1)
        .code(302)
        .message("Found")
        .header("Location", "https://iam.tongji.edu.cn/idp/oauth2/authorize")
        .body("".toResponseBody(jsonType))
        .build()
    }

    val apiClient =
      CanvasApiClient(
        baseClient = OkHttpClient.Builder().addInterceptor(interceptor).build(),
        cookieProvider = { "canvas_session=expired_cookie" },
      )

    val result = apiClient.fetchDashboard("https://canvas.example.edu", "expired_token")
    assertTrue(result.isFailure)
    assertTrue(result.exceptionOrNull() is CanvasAuthRequiredException)
  }

  private fun jsonResponse(request: okhttp3.Request, code: Int, body: String): Response =
    Response.Builder()
      .request(request)
      .protocol(Protocol.HTTP_1_1)
      .code(code)
      .message(if (code == 200) "OK" else "Error")
      .body(body.toResponseBody(jsonType))
      .build()
}
