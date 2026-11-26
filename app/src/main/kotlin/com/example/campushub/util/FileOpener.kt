package com.example.campushub.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException

data class SavedDownload(
  val uri: Uri,
  val displayPath: String,
  val fileName: String,
  val mimeType: String,
)

object FileOpener {

  const val PUBLIC_SUBFOLDER = "TJ Hub"
  const val PUBLIC_DISPLAY_DIR = "Download/$PUBLIC_SUBFOLDER"

  private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "gif", "webp", "bmp", "heic", "heif")

  fun downloadsDir(context: Context): File =
    File(context.cacheDir, "downloads").apply { mkdirs() }

  fun sanitizeFileName(raw: String, fallback: String = "attachment"): String {
    val cleaned = raw.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_")
    return cleaned.ifEmpty { fallback }
  }

  fun isImageFile(fileName: String, explicitMimeType: String? = null): Boolean {
    if (explicitMimeType?.startsWith("image/", ignoreCase = true) == true) return true
    val ext = fileName.substringAfterLast('.', "").lowercase()
    return ext in IMAGE_EXTENSIONS
  }

  fun resolveMimeType(fileName: String, explicitMimeType: String? = null): String {
    val cleanExplicit =
      explicitMimeType?.substringBefore(';')?.trim()?.takeIf {
        it.isNotBlank() && it != "application/octet-stream"
      }
    if (cleanExplicit != null) return cleanExplicit
    val ext = fileName.substringAfterLast('.', "").lowercase()
    return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
  }

  fun saveFileToPublicDownloads(
    context: Context,
    sourceFile: File,
    desiredFileName: String = sourceFile.name,
    explicitMimeType: String? = null,
  ): Result<SavedDownload> =
    runCatching {
      if (!sourceFile.exists() || sourceFile.length() <= 0L) {
        throw IOException("文件不存在或为空")
      }
      val safeName = sanitizeFileName(desiredFileName, "download_file")
      val resolvedMime = resolveMimeType(safeName, explicitMimeType)

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        saveViaMediaStoreDownloads(context, sourceFile, safeName, resolvedMime)
      } else {
        saveLegacyPublicDownload(context, sourceFile, safeName, resolvedMime)
      }
    }

  fun saveBytesToPublicDownloads(
    context: Context,
    bytes: ByteArray,
    desiredFileName: String,
    explicitMimeType: String? = null,
  ): Result<SavedDownload> =
    runCatching {
      if (bytes.isEmpty()) throw IOException("文件内容为空")
      val safeName = sanitizeFileName(desiredFileName, "download_file")
      val temp = File(downloadsDir(context), "tmp_${System.nanoTime()}_$safeName")
      try {
        temp.writeBytes(bytes)
        saveFileToPublicDownloads(context, temp, safeName, explicitMimeType).getOrThrow()
      } finally {
        temp.delete()
      }
    }

  private fun saveViaMediaStoreDownloads(
    context: Context,
    sourceFile: File,
    safeName: String,
    mimeType: String,
  ): SavedDownload {
    val resolver = context.contentResolver
    val values =
      ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, safeName)
        put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
        put(
          MediaStore.MediaColumns.RELATIVE_PATH,
          "${Environment.DIRECTORY_DOWNLOADS}/$PUBLIC_SUBFOLDER",
        )
        put(MediaStore.MediaColumns.IS_PENDING, 1)
      }

    val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
    val itemUri =
      resolver.insert(collection, values)
        ?: throw IOException("无法在系统 Download 目录创建文件")

    try {
      val outStream =
        resolver.openOutputStream(itemUri) ?: throw IOException("无法写入系统 Download 目录")
      outStream.use { output ->
        sourceFile.inputStream().use { input -> input.copyTo(output) }
        output.flush()
      }

      val doneValues = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
      resolver.update(itemUri, doneValues, null, null)

      val actualName =
        resolver.query(itemUri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)
          ?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getString(0) else null
          }
          ?.takeIf { it.isNotBlank() }
          ?: safeName

      return SavedDownload(
        uri = itemUri,
        displayPath = "$PUBLIC_DISPLAY_DIR/$actualName",
        fileName = actualName,
        mimeType = mimeType,
      )
    } catch (t: Throwable) {
      runCatching { resolver.delete(itemUri, null, null) }
      throw t
    }
  }

  private fun saveLegacyPublicDownload(
    context: Context,
    sourceFile: File,
    safeName: String,
    mimeType: String,
  ): SavedDownload {
    @Suppress("DEPRECATION")
    val publicRoot = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    val publicSubDir = File(publicRoot, PUBLIC_SUBFOLDER)
    val targetDir =
      if ((publicSubDir.exists() || publicSubDir.mkdirs()) && publicSubDir.canWrite()) {
        publicSubDir
      } else {
        File(
          context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: downloadsDir(context),
          PUBLIC_SUBFOLDER,
        ).apply { mkdirs() }
      }

    val destFile = resolveUniqueFile(targetDir, safeName)
    sourceFile.copyTo(destFile, overwrite = true)

    val uri =
      runCatching {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", destFile)
      }.getOrElse { Uri.fromFile(destFile) }

    val displayPath =
      if (targetDir == publicSubDir) "$PUBLIC_DISPLAY_DIR/${destFile.name}"
      else destFile.absolutePath

    return SavedDownload(
      uri = uri,
      displayPath = displayPath,
      fileName = destFile.name,
      mimeType = mimeType,
    )
  }

  private fun resolveUniqueFile(dir: File, fileName: String): File {
    val first = File(dir, fileName)
    if (!first.exists()) return first
    val dotIdx = fileName.lastIndexOf('.')
    val base = if (dotIdx > 0) fileName.substring(0, dotIdx) else fileName
    val ext = if (dotIdx > 0) fileName.substring(dotIdx) else ""
    var idx = 1
    while (idx < 1000) {
      val candidate = File(dir, "$base ($idx)$ext")
      if (!candidate.exists()) return candidate
      idx++
    }
    return File(dir, "${base}_${System.currentTimeMillis()}$ext")
  }

  fun openUri(context: Context, uri: Uri, explicitMimeType: String? = null): Result<Unit> =
    runCatching {
      val resolvedMime =
        explicitMimeType?.substringBefore(';')?.trim()?.takeIf {
          it.isNotBlank() && it != "application/octet-stream"
        } ?: "*/*"

      val intent =
        Intent(Intent.ACTION_VIEW).apply {
          setDataAndType(uri, resolvedMime)
          addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
      context.startActivity(Intent.createChooser(intent, "选择打开方式"))
    }

  fun openFile(context: Context, file: File, explicitMimeType: String? = null): Result<Unit> {
    return runCatching {
      val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
      val resolvedMime =
        resolveMimeType(file.name, explicitMimeType).let {
          if (it == "application/octet-stream") "*/*" else it
        }
      openUri(context, uri, resolvedMime).getOrThrow()
    }
  }
}
