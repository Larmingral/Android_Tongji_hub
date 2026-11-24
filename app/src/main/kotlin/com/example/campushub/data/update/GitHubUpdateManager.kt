package com.example.campushub.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import com.example.campushub.util.FileOpener
import java.io.File
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

data class GitHubReleaseInfo(
  val tagName: String,
  val normalizedVersion: String,
  val releaseName: String,
  val releaseNotes: String,
  val htmlUrl: String,
  val apkName: String,
  val apkDownloadUrl: String,
  val apkSizeBytes: Long,
  val sha256DownloadUrl: String? = null,
)

sealed interface UpdateCheckOutcome {
  data class GitHubUnreachable(val reason: String) : UpdateCheckOutcome

  data class UpToDate(
    val currentVersion: String,
    val latestRelease: GitHubReleaseInfo,
  ) : UpdateCheckOutcome

  data class UpdateAvailable(
    val currentVersion: String,
    val release: GitHubReleaseInfo,
  ) : UpdateCheckOutcome

  data class CheckFailed(val message: String) : UpdateCheckOutcome
}

object GitHubUpdateManager {

  private const val OWNER = "Larmingral"
  private const val REPO = "Android_Tongji_hub"
  private const val GITHUB_PING_URL = "https://api.github.com/zen"
  private const val LATEST_RELEASE_URL =
    "https://api.github.com/repos/$OWNER/$REPO/releases/latest"
  private const val RELEASES_LIST_URL =
    "https://api.github.com/repos/$OWNER/$REPO/releases?per_page=5"

  private val json = Json { ignoreUnknownKeys = true; isLenient = true }
  private val sha256Regex = Regex("\\b([0-9a-fA-F]{64})\\b")

  private val apiClient =
    OkHttpClient.Builder()
      .connectTimeout(8, TimeUnit.SECONDS)
      .readTimeout(12, TimeUnit.SECONDS)
      .followRedirects(true)
      .followSslRedirects(true)
      .build()

  private val downloadClient =
    OkHttpClient.Builder()
      .connectTimeout(15, TimeUnit.SECONDS)
      .readTimeout(60, TimeUnit.SECONDS)
      .followRedirects(true)
      .followSslRedirects(true)
      .build()

  suspend fun checkForUpdate(
    currentVersion: String,
    onProgress: (String) -> Unit = {},
  ): UpdateCheckOutcome =
    withContext(Dispatchers.IO) {
      onProgress("正在检测 GitHub 连通性...")
      val connectivity = checkGitHubConnectivity()
      if (connectivity.isFailure) {
        val reason = connectivity.exceptionOrNull()?.message ?: "网络连接失败"
        return@withContext UpdateCheckOutcome.GitHubUnreachable(reason)
      }

      onProgress("已连接 GitHub，正在检查最新 Release...")
      val releaseResult = fetchLatestRelease()
      val release =
        releaseResult.getOrElse { err ->
          return@withContext UpdateCheckOutcome.CheckFailed(err.message ?: "获取版本信息失败")
        }

      if (isNewerVersion(candidateTag = release.tagName, currentVersion = currentVersion)) {
        UpdateCheckOutcome.UpdateAvailable(currentVersion = currentVersion, release = release)
      } else {
        UpdateCheckOutcome.UpToDate(currentVersion = currentVersion, latestRelease = release)
      }
    }

  suspend fun checkGitHubConnectivity(): Result<Unit> =
    withContext(Dispatchers.IO) {
      runCatching {
        val req =
          Request.Builder()
            .url(GITHUB_PING_URL)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "TJ-Hub-Android")
            .get()
            .build()
        apiClient.newCall(req).execute().use { resp ->
          if (resp.code >= 500) {
            throw IOException("GitHub 服务异常（HTTP ${resp.code}）")
          }
        }
      }.recoverCatching { err ->
        if (err is CancellationException) throw err
        throw IOException(formatNetworkError(err), err)
      }
    }

  suspend fun fetchLatestRelease(): Result<GitHubReleaseInfo> =
    withContext(Dispatchers.IO) {
      runCatching {
        val latestReq =
          Request.Builder()
            .url(LATEST_RELEASE_URL)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "TJ-Hub-Android")
            .get()
            .build()
        apiClient.newCall(latestReq).execute().use { resp ->
          when {
            resp.isSuccessful -> {
              val body = resp.body?.string().orEmpty()
              return@runCatching parseLatestRelease(body)
                ?: throw IOException("无法解析最新 Release 信息")
            }
            resp.code == 404 -> {
              // Fallback to recent releases list in case latest is prerelease or empty.
            }
            resp.code == 403 -> {
              throw IOException("GitHub API 请求受限（HTTP 403），请稍后再试")
            }
            else -> {
              throw IOException("获取 Release 失败（HTTP ${resp.code}）")
            }
          }
        }

        val listReq =
          Request.Builder()
            .url(RELEASES_LIST_URL)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "TJ-Hub-Android")
            .get()
            .build()
        apiClient.newCall(listReq).execute().use { resp ->
          if (resp.code == 403) {
            throw IOException("GitHub API 请求受限（HTTP 403），请稍后再试")
          }
          if (!resp.isSuccessful) {
            throw IOException("获取 Release 列表失败（HTTP ${resp.code}）")
          }
          val body = resp.body?.string().orEmpty()
          parseReleaseFromList(body) ?: throw IOException("仓库暂未发布可用的 Release")
        }
      }.onFailure { err ->
        if (err is CancellationException) throw err
      }
    }

  suspend fun downloadApk(
    context: Context,
    release: GitHubReleaseInfo,
    onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit,
  ): Result<File> =
    withContext(Dispatchers.IO) {
      runCatching {
        if (release.apkDownloadUrl.isBlank()) {
          throw IOException("该 Release 未附带 APK 安装包")
        }

        val downloadsDir = FileOpener.downloadsDir(context)
        val safeName =
          FileOpener.sanitizeFileName(
            release.apkName.ifBlank { "TJ-Hub-${release.tagName}.apk" },
            fallback = "TJ-Hub-update.apk",
          )
        val targetFile = File(downloadsDir, safeName)
        val tempFile = File(downloadsDir, "$safeName.part")
        if (tempFile.exists()) {
          tempFile.delete()
        }

        val req =
          Request.Builder()
            .url(release.apkDownloadUrl)
            .header("Accept", "application/octet-stream")
            .header("User-Agent", "TJ-Hub-Android")
            .get()
            .build()

        downloadClient.newCall(req).execute().use { resp ->
          if (!resp.isSuccessful) {
            throw IOException("APK 下载失败（HTTP ${resp.code}）")
          }
          val body = resp.body ?: throw IOException("APK 响应内容为空")
          val totalBytes =
            body.contentLength().takeIf { it > 0L } ?: release.apkSizeBytes.takeIf { it > 0L } ?: -1L
          var downloaded = 0L
          var lastNotifyBytes = 0L
          val buffer = ByteArray(16 * 1024)

          try {
            body.byteStream().use { input ->
              tempFile.outputStream().use { output ->
                while (true) {
                  currentCoroutineContext().ensureActive()
                  val read = input.read(buffer)
                  if (read < 0) break
                  output.write(buffer, 0, read)
                  downloaded += read
                  if (downloaded - lastNotifyBytes >= 64 * 1024 || (totalBytes > 0L && downloaded == totalBytes)) {
                    lastNotifyBytes = downloaded
                    onProgress(downloaded, totalBytes)
                  }
                }
                output.flush()
              }
            }
          } catch (t: Throwable) {
            tempFile.delete()
            throw t
          }

          onProgress(downloaded, totalBytes)
        }

        if (!hasZipMagicHeader(tempFile)) {
          tempFile.delete()
          throw IOException("下载的文件不是有效的 APK 安装包，请检查网络或代理")
        }

        val shaUrl = release.sha256DownloadUrl
        if (!shaUrl.isNullOrBlank()) {
          val expectedHash = fetchExpectedSha256(shaUrl)
          if (expectedHash != null) {
            val actualHash = computeSha256(tempFile)
            if (!expectedHash.equals(actualHash, ignoreCase = true)) {
              tempFile.delete()
              throw IOException("APK SHA-256 校验失败，文件可能已损坏")
            }
          }
        }

        if (targetFile.exists()) {
          targetFile.delete()
        }
        if (!tempFile.renameTo(targetFile)) {
          tempFile.copyTo(targetFile, overwrite = true)
          tempFile.delete()
        }
        targetFile
      }.onFailure { err ->
        if (err is CancellationException) throw err
      }
    }

  fun canInstallPackages(context: Context): Boolean =
    context.packageManager.canRequestPackageInstalls()

  fun buildInstallUnknownAppsIntent(context: Context): Intent =
    Intent(
      Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
      Uri.parse("package:${context.packageName}"),
    )

  fun installApk(context: Context, apkFile: File): Result<Unit> =
    runCatching {
      val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
      val intent =
        Intent(Intent.ACTION_VIEW).apply {
          setDataAndType(uri, "application/vnd.android.package-archive")
          addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
      context.startActivity(intent)
    }

  fun normalizeVersion(raw: String): String =
    raw.trim().removePrefix("v").removePrefix("V")

  fun isNewerVersion(candidateTag: String, currentVersion: String): Boolean {
    val currentParsed = parseSemVer(currentVersion) ?: return parseSemVer(candidateTag) != null
    val candidateParsed = parseSemVer(candidateTag) ?: return false
    return compareParsedSemVer(candidateParsed, currentParsed) > 0
  }

  fun compareSemVer(left: String, right: String): Int {
    val l = parseSemVer(left)
    val r = parseSemVer(right)
    if (l != null && r != null) return compareParsedSemVer(l, r)
    return normalizeVersion(left).compareTo(normalizeVersion(right), ignoreCase = true)
  }

  fun parseLatestRelease(body: String): GitHubReleaseInfo? {
    val obj = json.parseToJsonElement(body) as? JsonObject ?: return null
    return parseReleaseObject(obj)
  }

  fun parseReleaseFromList(body: String): GitHubReleaseInfo? {
    val arr = json.parseToJsonElement(body) as? JsonArray ?: return null
    for (el in arr) {
      val obj = el as? JsonObject ?: continue
      val parsed = parseReleaseObject(obj)
      if (parsed != null) return parsed
    }
    return null
  }

  fun parseSha256Text(text: String): String? =
    sha256Regex.find(text)?.groupValues?.get(1)?.lowercase()

  private fun parseReleaseObject(obj: JsonObject): GitHubReleaseInfo? {
    if (obj.boolVal("draft") == true) return null
    val tagName = obj.str("tag_name")
    if (tagName.isBlank()) return null

    val assets = (obj["assets"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
    val apkAssets =
      assets.filter { it.str("name").endsWith(".apk", ignoreCase = true) }
    val apkAsset =
      apkAssets.firstOrNull { it.str("name").contains("TJ-Hub", ignoreCase = true) }
        ?: apkAssets.firstOrNull()

    val apkName = apkAsset?.str("name").orEmpty()
    val shaAsset =
      assets.firstOrNull { asset ->
        val name = asset.str("name")
        (apkName.isNotBlank() && name.equals("$apkName.sha256", ignoreCase = true)) ||
          name.endsWith(".sha256", ignoreCase = true)
      }

    return GitHubReleaseInfo(
      tagName = tagName,
      normalizedVersion = normalizeVersion(tagName),
      releaseName = obj.str("name").ifBlank { tagName },
      releaseNotes = obj.str("body"),
      htmlUrl = obj.str("html_url"),
      apkName = apkName,
      apkDownloadUrl = apkAsset?.str("browser_download_url").orEmpty(),
      apkSizeBytes = apkAsset?.longVal("size") ?: 0L,
      sha256DownloadUrl = shaAsset?.str("browser_download_url")?.takeIf { it.isNotBlank() },
    )
  }

  private fun fetchExpectedSha256(url: String): String? =
    runCatching {
      val req =
        Request.Builder()
          .url(url)
          .header("User-Agent", "TJ-Hub-Android")
          .get()
          .build()
      apiClient.newCall(req).execute().use { resp ->
        if (!resp.isSuccessful) return@runCatching null
        parseSha256Text(resp.body?.string().orEmpty())
      }
    }.getOrNull()

  private fun hasZipMagicHeader(file: File): Boolean {
    if (!file.exists() || file.length() < 4L) return false
    val magic = ByteArray(4)
    file.inputStream().use { input ->
      if (input.read(magic) != 4) return false
    }
    return magic[0] == 0x50.toByte() &&
      magic[1] == 0x4B.toByte() &&
      magic[2] == 0x03.toByte() &&
      magic[3] == 0x04.toByte()
  }

  private fun computeSha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val buf = ByteArray(16 * 1024)
    file.inputStream().use { input ->
      while (true) {
        val read = input.read(buf)
        if (read < 0) break
        digest.update(buf, 0, read)
      }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
  }

  private fun formatNetworkError(err: Throwable): String =
    when (err) {
      is UnknownHostException -> "无法解析 GitHub 域名（DNS 失败或未连网）"
      is SocketTimeoutException -> "连接 GitHub 超时，可能需要检查网络或代理"
      is ConnectException -> "无法连接到 GitHub 服务器"
      is SSLException -> "GitHub TLS 握手失败，当前网络可能受阻或存在拦截"
      else -> err.message?.takeIf { it.isNotBlank() } ?: "无法连接到 GitHub"
    }

  private data class ParsedSemVer(
    val numbers: List<Int>,
    val preRelease: List<String>,
  )

  private fun parseSemVer(raw: String): ParsedSemVer? {
    val clean = normalizeVersion(raw).substringBefore('+').trim()
    if (clean.isBlank()) return null
    val corePart = clean.substringBefore('-')
    val prePart = clean.substringAfter('-', "")
    val rawParts = corePart.split('.')
    if (rawParts.isEmpty()) return null
    val numbers = rawParts.map { it.toIntOrNull() ?: return null }
    val preRelease =
      if (prePart.isBlank()) emptyList()
      else prePart.split('.').filter { it.isNotBlank() }
    return ParsedSemVer(numbers = numbers, preRelease = preRelease)
  }

  private fun compareParsedSemVer(a: ParsedSemVer, b: ParsedSemVer): Int {
    val maxLen = maxOf(a.numbers.size, b.numbers.size)
    for (i in 0 until maxLen) {
      val av = a.numbers.getOrElse(i) { 0 }
      val bv = b.numbers.getOrElse(i) { 0 }
      if (av != bv) return av.compareTo(bv)
    }
    if (a.preRelease.isEmpty() && b.preRelease.isEmpty()) return 0
    if (a.preRelease.isEmpty()) return 1
    if (b.preRelease.isEmpty()) return -1
    val preMax = maxOf(a.preRelease.size, b.preRelease.size)
    for (i in 0 until preMax) {
      val ap = a.preRelease.getOrNull(i) ?: return -1
      val bp = b.preRelease.getOrNull(i) ?: return 1
      val ai = ap.toIntOrNull()
      val bi = bp.toIntOrNull()
      val cmp =
        when {
          ai != null && bi != null -> ai.compareTo(bi)
          ai != null -> -1
          bi != null -> 1
          else -> ap.compareTo(bp)
        }
      if (cmp != 0) return cmp
    }
    return 0
  }

  private fun JsonObject.str(key: String): String {
    val el: JsonElement = this[key] ?: return ""
    if (el is JsonNull) return ""
    return (el as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
  }

  private fun JsonObject.longVal(key: String): Long? =
    (this[key] as? JsonPrimitive)?.let { it.longOrNull ?: it.contentOrNull?.toLongOrNull() }

  private fun JsonObject.boolVal(key: String): Boolean? =
    (this[key] as? JsonPrimitive)?.booleanOrNull
}
