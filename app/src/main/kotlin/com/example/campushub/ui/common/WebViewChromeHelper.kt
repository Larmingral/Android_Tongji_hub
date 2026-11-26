package com.example.campushub.ui.common

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.MimeTypeMap
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.example.campushub.util.FileOpener
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class WebViewFileChooserHandler(
  private val launcher: ActivityResultLauncher<Intent>,
  private val getCallback: () -> ValueCallback<Array<Uri>>?,
  private val setCallback: (ValueCallback<Array<Uri>>?) -> Unit,
) {
  fun onShowFileChooser(
    filePathCallback: ValueCallback<Array<Uri>>?,
    fileChooserParams: WebChromeClient.FileChooserParams?,
  ): Boolean {
    if (filePathCallback == null) return false
    getCallback()?.onReceiveValue(null)
    setCallback(filePathCallback)

    val allowMultiple =
      fileChooserParams?.mode == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE
    val acceptMimes = normalizeAcceptMimeTypes(fileChooserParams?.acceptTypes)

    val getContentIntent =
      Intent(Intent.ACTION_GET_CONTENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        if (allowMultiple) {
          putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        if (acceptMimes.size == 1) {
          type = acceptMimes[0]
        } else {
          type = "*/*"
          if (acceptMimes.isNotEmpty()) {
            putExtra(Intent.EXTRA_MIME_TYPES, acceptMimes.toTypedArray())
          }
        }
      }

    val chooser = Intent.createChooser(getContentIntent, "选择要上传的文件")
    return try {
      launcher.launch(chooser)
      true
    } catch (_: ActivityNotFoundException) {
      getCallback()?.onReceiveValue(null)
      setCallback(null)
      false
    }
  }

  fun cancelPending() {
    getCallback()?.onReceiveValue(null)
    setCallback(null)
  }

  private fun normalizeAcceptMimeTypes(rawTypes: Array<String>?): List<String> {
    if (rawTypes.isNullOrEmpty()) return emptyList()
    val result = mutableListOf<String>()
    val mimeMap = MimeTypeMap.getSingleton()
    for (entry in rawTypes) {
      for (part in entry.split(',')) {
        val trimmed = part.trim()
        if (trimmed.isEmpty() || trimmed == "*/*") return emptyList()
        val mime =
          if (trimmed.startsWith(".")) {
            mimeMap.getMimeTypeFromExtension(trimmed.removePrefix(".").lowercase()) ?: return emptyList()
          } else if (trimmed.contains('/')) {
            trimmed
          } else {
            return emptyList()
          }
        if (!result.contains(mime)) result.add(mime)
      }
    }
    return result
  }
}

@Composable
fun rememberWebViewFileChooserHandler(): WebViewFileChooserHandler {
  var pendingCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }

  val launcher =
    rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
      val uris =
        if (result.resultCode == Activity.RESULT_OK) {
          val data = result.data
          val clip = data?.clipData
          when {
            clip != null && clip.itemCount > 0 ->
              Array(clip.itemCount) { idx -> clip.getItemAt(idx).uri }
            data?.data != null -> arrayOf(data.data!!)
            else -> WebChromeClient.FileChooserParams.parseResult(result.resultCode, data)
          }
        } else {
          null
        }
      pendingCallback?.onReceiveValue(uris)
      pendingCallback = null
    }

  val handler =
    remember(launcher) {
      WebViewFileChooserHandler(
        launcher = launcher,
        getCallback = { pendingCallback },
        setCallback = { pendingCallback = it },
      )
    }

  DisposableEffect(handler) {
    onDispose { handler.cancelPending() }
  }

  return handler
}

private val webDownloadHttpClient by lazy {
  OkHttpClient.Builder()
    .followRedirects(true)
    .followSslRedirects(true)
    .build()
}

@Composable
fun rememberWebViewDownloadTrigger(
  onStatusMessage: (String) -> Unit,
): (url: String, userAgent: String, contentDisposition: String, mimetype: String) -> Unit {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()

  return remember(context, scope, onStatusMessage) {
    { url, userAgent, contentDisposition, mimetype ->
      if (url.startsWith("http://") || url.startsWith("https://")) {
        scope.launch {
          val guessed =
            FileOpener.sanitizeFileName(
              URLUtil.guessFileName(url, contentDisposition, mimetype),
              "webview_download",
            )
          onStatusMessage("正在下载 $guessed...")
          val result =
            withContext(Dispatchers.IO) {
              downloadWebUrlToPublicFolder(context, url, userAgent, guessed, mimetype)
            }
          result.fold(
            onSuccess = { saved ->
              onStatusMessage("已保存至 ${saved.displayPath}")
              FileOpener.openUri(context, saved.uri, saved.mimeType)
            },
            onFailure = { err ->
              onStatusMessage("网页下载失败：${err.message ?: "未知错误"}")
            },
          )
        }
      } else {
        onStatusMessage("暂不支持该下载协议：$url")
      }
    }
  }
}

private fun downloadWebUrlToPublicFolder(
  context: Context,
  url: String,
  userAgent: String,
  fileName: String,
  mimeType: String,
): Result<com.example.campushub.util.SavedDownload> =
  runCatching {
    val cookies = CookieManager.getInstance().getCookie(url).orEmpty()
    val req =
      Request.Builder()
        .url(url)
        .apply {
          if (cookies.isNotBlank()) header("Cookie", cookies)
          if (userAgent.isNotBlank()) header("User-Agent", userAgent)
        }
        .get()
        .build()

    val tempFile = File(FileOpener.downloadsDir(context), "web_${System.nanoTime()}_$fileName")
    try {
      webDownloadHttpClient.newCall(req).execute().use { resp ->
        if (!resp.isSuccessful) {
          throw IOException("HTTP ${resp.code}")
        }
        val body = resp.body ?: throw IOException("响应内容为空")
        tempFile.outputStream().use { out ->
          body.byteStream().use { input -> input.copyTo(out) }
          out.flush()
        }
      }
      FileOpener.saveFileToPublicDownloads(
        context = context,
        sourceFile = tempFile,
        desiredFileName = fileName,
        explicitMimeType = mimeType,
      ).getOrThrow()
    } finally {
      tempFile.delete()
    }
  }

fun WebView.attachPublicDownloadListener(
  onDownload: (url: String, userAgent: String, contentDisposition: String, mimetype: String) -> Unit,
) {
  setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
    onDownload(url.orEmpty(), userAgent.orEmpty(), contentDisposition.orEmpty(), mimetype.orEmpty())
  }
}
