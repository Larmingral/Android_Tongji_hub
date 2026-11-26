package com.example.campushub.ui.common

import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.campushub.util.FileOpener
import java.io.File
import java.io.IOException
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

data class ImagePreviewPayload(
  val title: String = "图片预览",
  val contentUri: Uri? = null,
  val localFilePath: String? = null,
  val base64Data: String? = null,
  val imageUrl: String? = null,
  val cookies: String? = null,
  val mimeType: String? = null,
  val alreadySavedPath: String? = null,
)

private val previewHttpClient by lazy { OkHttpClient() }

@Composable
fun ImagePreviewDialog(
  payload: ImagePreviewPayload,
  onDismiss: () -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()

  var isLoading by remember(payload) { mutableStateOf(true) }
  var loadError by remember(payload) { mutableStateOf<String?>(null) }
  var rawBytes by remember(payload) { mutableStateOf<ByteArray?>(null) }
  var resolvedMime by remember(payload) { mutableStateOf(payload.mimeType) }
  var bitmap by remember(payload) { mutableStateOf<ImageBitmap?>(null) }
  var savedStatus by remember(payload) { mutableStateOf(payload.alreadySavedPath) }
  var savedUri by remember(payload) { mutableStateOf(payload.contentUri) }

  var scale by remember(payload) { mutableFloatStateOf(1f) }
  var offset by remember(payload) { mutableStateOf(Offset.Zero) }
  var viewportSize by remember { mutableStateOf(IntSize.Zero) }

  LaunchedEffect(payload) {
    isLoading = true
    loadError = null
    rawBytes = null
    bitmap = null
    scale = 1f
    offset = Offset.Zero

    val result =
      withContext(Dispatchers.IO) {
        runCatching {
          val (bytes, inferredMime) = loadPayloadBytes(context, payload)
          val decoded = decodeSampledBitmap(bytes, maxDimension = 4096)
            ?: throw IOException("无法解码图片数据")
          Triple(bytes, decoded.asImageBitmap(), inferredMime)
        }
      }

    result.fold(
      onSuccess = { (bytes, img, mime) ->
        rawBytes = bytes
        bitmap = img
        if (!mime.isNullOrBlank()) resolvedMime = mime
        isLoading = false
      },
      onFailure = { err ->
        loadError = err.message ?: "加载图片失败"
        isLoading = false
      },
    )
  }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Box(
      modifier =
        Modifier.fillMaxSize()
          .background(Color.Black.copy(alpha = 0.94f)),
    ) {
      val img = bitmap
      Box(
        modifier =
          Modifier.fillMaxSize()
            .onSizeChanged { viewportSize = it }
            .pointerInput(img) {
              if (img == null) return@pointerInput
              detectTapGestures(
                onDoubleTap = {
                  if (scale > 1.2f) {
                    scale = 1f
                    offset = Offset.Zero
                  } else {
                    scale = 2.5f
                  }
                },
              )
            }
            .pointerInput(img) {
              if (img == null) return@pointerInput
              detectTransformGestures { _, pan, zoom, _ ->
                val newScale = (scale * zoom).coerceIn(1f, 5f)
                val maxX = (viewportSize.width * (newScale - 1f)) / 2f
                val maxY = (viewportSize.height * (newScale - 1f)) / 2f
                val rawOffset =
                  if (newScale <= 1.01f) Offset.Zero
                  else offset + pan
                scale = newScale
                offset =
                  Offset(
                    x = rawOffset.x.coerceIn(-maxX, maxX),
                    y = rawOffset.y.coerceIn(-maxY, maxY),
                  )
              }
            },
        contentAlignment = Alignment.Center,
      ) {
        when {
          isLoading -> {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              CircularProgressIndicator(color = Color.White)
              Text(
                text = "正在加载图片...",
                color = Color.White.copy(alpha = 0.8f),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 10.dp),
              )
            }
          }

          loadError != null -> {
            Text(
              text = loadError.orEmpty(),
              color = MaterialTheme.colorScheme.errorContainer,
              style = MaterialTheme.typography.bodyMedium,
              modifier = Modifier.padding(24.dp),
            )
          }

          img != null -> {
            androidx.compose.foundation.Image(
              bitmap = img,
              contentDescription = payload.title,
              contentScale = ContentScale.Fit,
              modifier =
                Modifier.fillMaxSize()
                  .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                  },
            )
          }
        }
      }

      // Top overlay bar
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .align(Alignment.TopCenter)
            .statusBarsPadding()
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        IconButton(onClick = onDismiss) {
          Icon(Icons.Default.Close, contentDescription = "关闭预览", tint = Color.White)
        }
        Column(modifier = Modifier.weight(1f).padding(horizontal = 4.dp)) {
          Text(
            text = payload.title.ifBlank { "图片预览" },
            color = Color.White,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text =
              if (!savedStatus.isNullOrBlank()) "已保存至 $savedStatus · 双击/双指缩放"
              else "双击或双指缩放 (${String.format(Locale.US, "%.1fx", scale)})",
            color = Color.White.copy(alpha = 0.78f),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }

        if (rawBytes != null && savedStatus.isNullOrBlank()) {
          TextButton(
            onClick = {
              val bytes = rawBytes ?: return@TextButton
              scope.launch {
                val fileName = buildDownloadImageName(payload.title, payload.imageUrl, resolvedMime)
                val saved =
                  withContext(Dispatchers.IO) {
                    FileOpener.saveBytesToPublicDownloads(
                      context = context,
                      bytes = bytes,
                      desiredFileName = fileName,
                      explicitMimeType = resolvedMime,
                    )
                  }
                saved.fold(
                  onSuccess = { info ->
                    savedUri = info.uri
                    savedStatus = info.displayPath
                  },
                  onFailure = { err ->
                    loadError = "保存失败：${err.message ?: "未知错误"}"
                  },
                )
              }
            },
          ) {
            Text("保存", color = Color.White)
          }
        }

        if (rawBytes != null || savedUri != null) {
          Spacer(modifier = Modifier.width(2.dp))
          TextButton(
            onClick = {
              scope.launch {
                val existingUri = savedUri
                if (existingUri != null) {
                  FileOpener.openUri(context, existingUri, resolvedMime ?: "image/*")
                  return@launch
                }
                val bytes = rawBytes ?: return@launch
                val fileName = buildDownloadImageName(payload.title, payload.imageUrl, resolvedMime)
                val cached =
                  withContext(Dispatchers.IO) {
                    File(FileOpener.downloadsDir(context), "preview_$fileName").apply {
                      writeBytes(bytes)
                    }
                  }
                FileOpener.openFile(context, cached, resolvedMime ?: "image/*")
              }
            },
          ) {
            Text("外部打开", color = Color.White)
          }
        }
      }

      if (!savedStatus.isNullOrBlank()) {
        Surface(
          shape = RoundedCornerShape(20.dp),
          color = Color.Black.copy(alpha = 0.72f),
          modifier =
            Modifier.align(Alignment.BottomCenter)
              .padding(bottom = 28.dp, start = 16.dp, end = 16.dp),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = "已保存：$savedStatus",
              color = Color.White,
              style = MaterialTheme.typography.bodySmall,
            )
          }
        }
      }
    }
  }
}

private fun loadPayloadBytes(
  context: android.content.Context,
  payload: ImagePreviewPayload,
): Pair<ByteArray, String?> {
  payload.contentUri?.let { uri ->
    val mime = context.contentResolver.getType(uri) ?: payload.mimeType
    val bytes =
      context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        ?: throw IOException("无法读取图片文件")
    return bytes to mime
  }

  payload.localFilePath?.let { path ->
    val f = File(path)
    if (f.exists() && f.length() > 0L) {
      return f.readBytes() to (payload.mimeType ?: FileOpener.resolveMimeType(f.name))
    }
  }

  payload.base64Data?.takeIf { it.isNotBlank() }?.let { raw ->
    val clean = raw.substringAfter("base64,", raw)
    val bytes = Base64.decode(clean, Base64.DEFAULT)
    return bytes to payload.mimeType
  }

  val url = payload.imageUrl?.trim().orEmpty()
  if (url.startsWith("data:image/", ignoreCase = true) && url.contains(";base64,", ignoreCase = true)) {
    val mime = url.substringAfter("data:").substringBefore(';')
    val b64 = url.substringAfter(";base64,")
    return Base64.decode(b64, Base64.DEFAULT) to mime
  }

  if (url.startsWith("http://") || url.startsWith("https://")) {
    val req =
      Request.Builder()
        .url(url)
        .apply {
          if (!payload.cookies.isNullOrBlank()) {
            header("Cookie", payload.cookies)
          }
        }
        .get()
        .build()
    previewHttpClient.newCall(req).execute().use { resp ->
      if (!resp.isSuccessful) {
        throw IOException("图片请求失败（HTTP ${resp.code}）")
      }
      val mime = resp.header("Content-Type")?.substringBefore(';')?.trim() ?: payload.mimeType
      val bytes = resp.body?.bytes() ?: throw IOException("图片内容为空")
      return bytes to mime
    }
  }

  throw IOException("未找到可用的图片来源")
}

private fun decodeSampledBitmap(bytes: ByteArray, maxDimension: Int): android.graphics.Bitmap? {
  val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
  BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
  var sampleSize = 1
  val maxSide = maxOf(bounds.outWidth, bounds.outHeight)
  while (maxSide / sampleSize > maxDimension) {
    sampleSize *= 2
  }
  val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
  return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
}

private fun buildDownloadImageName(title: String, imageUrl: String?, mimeType: String?): String {
  val extFromMime =
    when (mimeType?.lowercase()) {
      "image/png" -> "png"
      "image/webp" -> "webp"
      "image/gif" -> "gif"
      "image/bmp" -> "bmp"
      else -> "jpg"
    }
  val candidate =
    imageUrl
      ?.takeIf { it.startsWith("http") }
      ?.let { runCatching { Uri.parse(it).lastPathSegment }.getOrNull() }
      ?.takeIf { it.isNotBlank() && it.contains('.') }
      ?: title.takeIf { it.isNotBlank() && it != "图片预览" }
      ?: "image_${System.currentTimeMillis()}"

  val safe = FileOpener.sanitizeFileName(candidate, "image")
  return if (FileOpener.isImageFile(safe)) safe else "$safe.$extFromMime"
}
