package com.example.campushub.ui.auth

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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

object AppAttributionInfo {
  const val APP_NAME = "TJ Hub"
  const val AUTHOR = "Larmingral"
  const val COPYRIGHT_LINE = "Copyright © 2026 Larmingral"
  const val LICENSE_NAME = "MIT License"
  const val REPO_URL = "https://github.com/Larmingral/Android_Tongji_hub"
}

@Composable
fun AboutAppDialog(
  onDismiss: () -> Unit,
) {
  val context = LocalContext.current
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

  AlertDialog(
    onDismissRequest = onDismiss,
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
      TextButton(onClick = onDismiss) { Text("确定") }
    },
  )
}
