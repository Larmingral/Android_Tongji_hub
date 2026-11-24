package com.example.campushub.ui.auth

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.campushub.R
import com.example.campushub.data.update.GitHubReleaseInfo
import com.example.campushub.data.update.GitHubUpdateManager
import com.example.campushub.data.update.UpdateCheckOutcome
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

object AppAttributionInfo {
  const val APP_NAME = "TJ Hub"
  const val AUTHOR = "Larmingral"
  const val COPYRIGHT_LINE = "Copyright © 2026 Larmingral"
  const val LICENSE_NAME = "MIT License"
  const val REPO_URL = "https://github.com/Larmingral/Android_Tongji_hub"
}

private sealed interface UpdateUiState {
  data object Idle : UpdateUiState

  data class Checking(val stepMessage: String) : UpdateUiState

  data class Unreachable(val reason: String) : UpdateUiState

  data class UpToDate(val currentVersion: String, val latestRelease: GitHubReleaseInfo) :
    UpdateUiState

  data class Available(val currentVersion: String, val release: GitHubReleaseInfo) : UpdateUiState

  data class Downloading(
    val release: GitHubReleaseInfo,
    val downloadedBytes: Long,
    val totalBytes: Long,
  ) : UpdateUiState

  data class ReadyToInstall(
    val release: GitHubReleaseInfo,
    val apkFile: File,
    val statusHint: String? = null,
  ) : UpdateUiState

  data class Error(val message: String, val release: GitHubReleaseInfo? = null) : UpdateUiState
}

@Composable
fun AboutAppDialog(
  onDismiss: () -> Unit,
  autoCheckUpdates: Boolean = false,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val versionName =
    remember(context) {
      runCatching {
        val pm = context.packageManager
        val info =
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
          } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(context.packageName, 0)
          }
        info.versionName ?: "dev"
      }.getOrDefault("dev")
    }

  var updateState by remember { mutableStateOf<UpdateUiState>(UpdateUiState.Idle) }
  var activeJob by remember { mutableStateOf<Job?>(null) }
  var pendingInstallFile by remember { mutableStateOf<File?>(null) }

  val unknownSourcesLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
      val file = pendingInstallFile
      val readyState = updateState as? UpdateUiState.ReadyToInstall
      if (file != null && file.exists() && GitHubUpdateManager.canInstallPackages(context)) {
        GitHubUpdateManager.installApk(context, file).onFailure { err ->
          if (readyState != null) {
            updateState = readyState.copy(statusHint = "调起安装器失败：${err.message ?: "未知错误"}")
          }
        }
      } else if (readyState != null && !GitHubUpdateManager.canInstallPackages(context)) {
        updateState = readyState.copy(statusHint = "尚未开启“允许安装未知应用”权限，请授权后点击覆盖安装")
      }
    }

  fun launchUpdateCheck() {
    activeJob?.cancel()
    activeJob =
      scope.launch {
        updateState = UpdateUiState.Checking("正在检测 GitHub 连通性...")
        val outcome =
          GitHubUpdateManager.checkForUpdate(currentVersion = versionName) { step ->
            updateState = UpdateUiState.Checking(step)
          }
        updateState =
          when (outcome) {
            is UpdateCheckOutcome.GitHubUnreachable -> UpdateUiState.Unreachable(outcome.reason)
            is UpdateCheckOutcome.UpToDate ->
              UpdateUiState.UpToDate(outcome.currentVersion, outcome.latestRelease)
            is UpdateCheckOutcome.UpdateAvailable ->
              UpdateUiState.Available(outcome.currentVersion, outcome.release)
            is UpdateCheckOutcome.CheckFailed -> UpdateUiState.Error(outcome.message)
          }
      }
  }

  fun triggerInstall(release: GitHubReleaseInfo, apkFile: File) {
    if (!GitHubUpdateManager.canInstallPackages(context)) {
      pendingInstallFile = apkFile
      updateState =
        UpdateUiState.ReadyToInstall(
          release = release,
          apkFile = apkFile,
          statusHint = "系统需要“允许来自此来源的应用”权限才能覆盖安装，正在打开授权页...",
        )
      runCatching {
        unknownSourcesLauncher.launch(GitHubUpdateManager.buildInstallUnknownAppsIntent(context))
      }.onFailure { err ->
        updateState =
          UpdateUiState.ReadyToInstall(
            release = release,
            apkFile = apkFile,
            statusHint = "无法打开安装权限设置：${err.message ?: "请手动前往系统设置授权"}",
          )
      }
      return
    }
    GitHubUpdateManager.installApk(context, apkFile).onFailure { err ->
      updateState =
        UpdateUiState.ReadyToInstall(
          release = release,
          apkFile = apkFile,
          statusHint = "调起安装器失败：${err.message ?: "未知错误"}",
        )
    }
  }

  fun launchDownloadAndInstall(release: GitHubReleaseInfo) {
    activeJob?.cancel()
    activeJob =
      scope.launch {
        updateState = UpdateUiState.Downloading(release, 0L, release.apkSizeBytes)
        val result =
          GitHubUpdateManager.downloadApk(context, release) { downloaded, total ->
            updateState = UpdateUiState.Downloading(release, downloaded, total)
          }
        result.fold(
          onSuccess = { file ->
            updateState = UpdateUiState.ReadyToInstall(release, file)
            triggerInstall(release, file)
          },
          onFailure = { err ->
            updateState =
              UpdateUiState.Error(
                message = err.message ?: "下载 APK 失败",
                release = release,
              )
          },
        )
      }
  }

  LaunchedEffect(autoCheckUpdates) {
    if (autoCheckUpdates && updateState is UpdateUiState.Idle) {
      launchUpdateCheck()
    }
  }

  AlertDialog(
    onDismissRequest = {
      activeJob?.cancel()
      onDismiss()
    },
    title = {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
          modifier =
            Modifier.size(40.dp)
              .clip(RoundedCornerShape(10.dp))
              .background(
                Brush.linearGradient(
                  colors = listOf(Color(0xFF16233B), Color(0xFF243B61))
                )
              ),
          contentAlignment = Alignment.Center,
        ) {
          Image(
            painter = painterResource(id = R.drawable.ic_launcher_foreground),
            contentDescription = AppAttributionInfo.APP_NAME,
            modifier = Modifier.size(38.dp),
          )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
          Text(
            text = AppAttributionInfo.APP_NAME,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
          )
          Text(
            text = "版本 $versionName · ${AppAttributionInfo.LICENSE_NAME}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    },
    text = {
      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        AppUpdateSection(
          state = updateState,
          onCheck = ::launchUpdateCheck,
          onCancel = {
            activeJob?.cancel()
            updateState = UpdateUiState.Idle
          },
          onDownload = ::launchDownloadAndInstall,
          onInstall = ::triggerInstall,
          onOpenReleasePage = { url ->
            val target = url.ifBlank { "${AppAttributionInfo.REPO_URL}/releases" }
            runCatching {
              context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(target)).apply {
                  addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
              )
            }
          },
        )

        Surface(
          shape = RoundedCornerShape(10.dp),
          color = MaterialTheme.colorScheme.surfaceContainerLow,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Text(
              text = "原创作者 / 著作权归属：${AppAttributionInfo.AUTHOR}",
              style = MaterialTheme.typography.labelLarge,
              fontWeight = FontWeight.SemiBold,
            )
            Text(
              text = AppAttributionInfo.COPYRIGHT_LINE,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
              text = AppAttributionInfo.REPO_URL,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.primary,
              textDecoration = TextDecoration.Underline,
              modifier =
                Modifier.clickable {
                  runCatching {
                    val intent =
                      Intent(Intent.ACTION_VIEW, Uri.parse(AppAttributionInfo.REPO_URL)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                      }
                    context.startActivity(intent)
                  }
                },
            )
          }
        }

        HorizontalDivider()

        Text(
          text = "原创设计与知识产权声明",
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.SemiBold,
        )
        Text(
          text =
            "本应用由 ${AppAttributionInfo.AUTHOR} 原创构思与主导实现，整合全景日程月/周视图、" +
              "Android Keystore 本地硬件加密自动免登、Canvas 免 Token 会话看板与 DDL 距今排序、" +
              "同济教务加密课表与富文本通知附件下载及校园邮箱阅读体验。",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
          text = "源码依据 ${AppAttributionInfo.LICENSE_NAME} 开源发布；任何二次分发、修改或搬运须完整保留原作者署名与版权声明。",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.primary,
        )
      }
    },
    confirmButton = {
      TextButton(
        onClick = {
          activeJob?.cancel()
          onDismiss()
        }
      ) {
        Text("关闭")
      }
    },
  )
}

@Composable
private fun AppUpdateSection(
  state: UpdateUiState,
  onCheck: () -> Unit,
  onCancel: () -> Unit,
  onDownload: (GitHubReleaseInfo) -> Unit,
  onInstall: (GitHubReleaseInfo, File) -> Unit,
  onOpenReleasePage: (String) -> Unit,
) {
  Surface(
    shape = RoundedCornerShape(10.dp),
    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier.padding(10.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text(
          text = "检查更新（GitHub Release）",
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.SemiBold,
        )
        if (state !is UpdateUiState.Checking && state !is UpdateUiState.Downloading) {
          OutlinedButton(
            onClick = onCheck,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
            modifier = Modifier.height(30.dp),
          ) {
            Text(
              text = if (state is UpdateUiState.Idle) "检查更新" else "重新检查",
              style = MaterialTheme.typography.labelMedium,
            )
          }
        }
      }

      when (state) {
        is UpdateUiState.Idle -> {
          Text(
            text = "点击“检查更新”将先检测 GitHub 连通性，再比对最新 Release。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }

        is UpdateUiState.Checking -> {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            Text(
              text = state.stepMessage,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }

        is UpdateUiState.Unreachable -> {
          Text(
            text = "无法连接到 GitHub：${state.reason}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
          )
        }

        is UpdateUiState.UpToDate -> {
          Text(
            text = "当前已是最新版本（本机 ${state.currentVersion}，GitHub 最新 ${state.latestRelease.tagName}）",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
          )
        }

        is UpdateUiState.Available -> {
          val rel = state.release
          val sizeLabel = formatBytes(rel.apkSizeBytes)
          Text(
            text = "发现新版本 ${rel.tagName}（当前 ${state.currentVersion}${if (sizeLabel.isNotBlank()) " · $sizeLabel" else ""}）",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
          )
          if (rel.releaseNotes.isNotBlank()) {
            Text(
              text = rel.releaseNotes.trim(),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 5,
            )
          }
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            if (rel.apkDownloadUrl.isNotBlank()) {
              Button(
                onClick = { onDownload(rel) },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
              ) {
                Text("下载 APK 并覆盖安装", style = MaterialTheme.typography.labelMedium)
              }
            } else {
              Text(
                text = "该 Release 未附带 APK",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
              )
            }
            TextButton(onClick = { onOpenReleasePage(rel.htmlUrl) }) {
              Text("网页查看", style = MaterialTheme.typography.labelMedium)
            }
          }
        }

        is UpdateUiState.Downloading -> {
          val total = state.totalBytes
          val progress =
            if (total > 0L) (state.downloadedBytes.toFloat() / total.toFloat()).coerceIn(0f, 1f)
            else null
          val progressText =
            if (total > 0L) {
              val pct = (progress!! * 100f).toInt()
              "正在下载 ${state.release.apkName}：$pct% (${formatBytes(state.downloadedBytes)} / ${formatBytes(total)})"
            } else {
              "正在下载 ${state.release.apkName}：已下载 ${formatBytes(state.downloadedBytes)}"
            }
          Text(
            text = progressText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          if (progress != null) {
            LinearProgressIndicator(
              progress = { progress },
              modifier = Modifier.fillMaxWidth(),
            )
          } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
          }
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
          ) {
            TextButton(onClick = onCancel) {
              Text("取消下载", style = MaterialTheme.typography.labelMedium)
            }
          }
        }

        is UpdateUiState.ReadyToInstall -> {
          Text(
            text = "安装包 ${state.apkFile.name} 已就绪（${formatBytes(state.apkFile.length())}）",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
          )
          if (!state.statusHint.isNullOrBlank()) {
            Text(
              text = state.statusHint,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.error,
            )
          }
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Button(
              onClick = { onInstall(state.release, state.apkFile) },
              contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            ) {
              Text("立即覆盖安装", style = MaterialTheme.typography.labelMedium)
            }
            TextButton(onClick = { onOpenReleasePage(state.release.htmlUrl) }) {
              Text("网页查看", style = MaterialTheme.typography.labelMedium)
            }
          }
        }

        is UpdateUiState.Error -> {
          Text(
            text = state.message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
          )
          val rel = state.release
          if (rel != null) {
            Row(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              if (rel.apkDownloadUrl.isNotBlank()) {
                OutlinedButton(
                  onClick = { onDownload(rel) },
                  contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                ) {
                  Text("重试下载", style = MaterialTheme.typography.labelMedium)
                }
              }
              TextButton(onClick = { onOpenReleasePage(rel.htmlUrl) }) {
                Text("浏览器下载", style = MaterialTheme.typography.labelMedium)
              }
            }
          }
        }
      }
    }
  }
}

private fun formatBytes(bytes: Long): String {
  if (bytes <= 0L) return ""
  val mb = bytes / (1024.0 * 1024.0)
  return if (mb >= 1.0) {
    String.format(Locale.US, "%.1f MB", mb)
  } else {
    val kb = bytes / 1024.0
    String.format(Locale.US, "%.0f KB", kb)
  }
}
