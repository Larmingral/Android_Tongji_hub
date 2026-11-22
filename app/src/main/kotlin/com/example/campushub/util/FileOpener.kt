package com.example.campushub.util

import android.content.Context
import android.content.Intent
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File

object FileOpener {

  fun downloadsDir(context: Context): File =
    File(context.cacheDir, "downloads").apply { mkdirs() }

  fun sanitizeFileName(raw: String, fallback: String = "attachment"): String {
    val cleaned = raw.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_")
    return cleaned.ifEmpty { fallback }
  }

  fun openFile(context: Context, file: File, explicitMimeType: String? = null): Result<Unit> {
    return runCatching {
      val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
      val ext = file.extension.lowercase()
      val resolvedMime =
        explicitMimeType?.takeIf { it.isNotBlank() && it != "application/octet-stream" }
          ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
          ?: "*/*"

      val intent =
        Intent(Intent.ACTION_VIEW).apply {
          setDataAndType(uri, resolvedMime)
          addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
      context.startActivity(Intent.createChooser(intent, "选择打开方式"))
    }
  }
}
