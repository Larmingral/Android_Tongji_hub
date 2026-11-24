package com.example.campushub.ui.mail

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.campushub.data.mail.MailBodyBlock
import com.example.campushub.data.mail.MailDetail
import com.example.campushub.data.mail.MailHeader
import com.example.campushub.data.mail.MailImageSource
import com.example.campushub.ui.auth.AboutAppDialog
import com.example.campushub.util.FileOpener
import com.example.campushub.util.HtmlTextCleaner
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

@Composable
fun MailScreen(
  viewModel: MailViewModel,
  modifier: Modifier = Modifier,
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()

  BackHandler(enabled = uiState.isShowingDetail) {
    viewModel.closeMessageDetail()
  }

  Column(modifier = modifier.fillMaxSize()) {
    if (uiState.isOpeningAttachment) {
      LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(2.dp))
    }

    if (uiState.isShowingDetail) {
      MailDetailView(uiState = uiState, viewModel = viewModel)
    } else {
      MailInboxView(uiState = uiState, viewModel = viewModel)
    }
  }
}

@Composable
private fun MailInboxView(
  uiState: MailUiState,
  viewModel: MailViewModel,
) {
  val context = LocalContext.current
  var showPassword by remember { mutableStateOf(false) }
  var showAboutAndUpdate by remember { mutableStateOf(false) }

  if (showAboutAndUpdate) {
    AboutAppDialog(
      onDismiss = { showAboutAndUpdate = false },
      autoCheckUpdates = true,
    )
  }

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(12.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    item {
      Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(14.dp),
      ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text =
                  if (uiState.emailInput.isNotBlank() && uiState.messages.isNotEmpty())
                    "收件箱 · ${uiState.emailInput}"
                  else "同济大学邮箱 (IMAP 原生收件箱)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
              Text(
                text = "${uiState.imapHostInput}:${uiState.imapPortInput} (SSL)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            if (uiState.emailInput.isNotBlank() && uiState.passwordInput.isNotBlank()) {
              IconButton(onClick = viewModel::connectAndRefresh, enabled = !uiState.isLoadingList) {
                Icon(Icons.Default.Refresh, contentDescription = "刷新邮件")
              }
            }
            IconButton(onClick = viewModel::toggleConfigExpanded) {
              Icon(Icons.Default.Settings, contentDescription = "邮箱设置")
            }
          }

          AnimatedVisibility(visible = uiState.isConfigExpanded) {
            Column(
              modifier = Modifier.padding(top = 8.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              OutlinedTextField(
                value = uiState.emailInput,
                onValueChange = viewModel::updateEmail,
                label = { Text("同济邮箱地址") },
                placeholder = { Text("例如：xxxxxx@tongji.edu.cn") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
              )
              OutlinedTextField(
                value = uiState.passwordInput,
                onValueChange = viewModel::updatePassword,
                label = { Text("客户端专用密码") },
                placeholder = { Text("请填写生成的专用密码，非统一身份认证密码") },
                singleLine = true,
                visualTransformation =
                  if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                  TextButton(onClick = { showPassword = !showPassword }) {
                    Text(if (showPassword) "隐藏" else "显示", fontSize = 12.sp)
                  }
                },
                modifier = Modifier.fillMaxWidth(),
              )
              Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                  value = uiState.imapHostInput,
                  onValueChange = viewModel::updateHost,
                  label = { Text("IMAP 服务器") },
                  singleLine = true,
                  modifier = Modifier.weight(2f),
                )
                OutlinedTextField(
                  value = uiState.imapPortInput,
                  onValueChange = viewModel::updatePort,
                  label = { Text("端口") },
                  singleLine = true,
                  modifier = Modifier.weight(1f),
                )
              }
              Text(
                text = "获取专用密码：浏览器登录 mail.tongji.edu.cn → 设置 → 安全设置 → 客户端专用密码 → 生成专用密码",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Button(
                onClick = viewModel::connectAndRefresh,
                enabled = !uiState.isLoadingList,
                modifier = Modifier.fillMaxWidth(),
              ) {
                if (uiState.isLoadingList) {
                  CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                  )
                  Spacer(modifier = Modifier.width(6.dp))
                }
                Text("连接并拉取最近 30 封邮件")
              }
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
              ) {
                TextButton(onClick = { showAboutAndUpdate = true }) {
                  Text("检查更新与关于 TJ Hub", fontSize = 12.sp)
                }
              }
            }
          }
        }
      }
    }

    if (uiState.errorMessage != null) {
      item {
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = MaterialTheme.colorScheme.errorContainer,
        ) {
          Text(
            text = uiState.errorMessage,
            color = MaterialTheme.colorScheme.onErrorContainer,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.fillMaxWidth().padding(12.dp),
          )
        }
      }
    }

    if (uiState.isLoadingList && uiState.messages.isEmpty()) {
      item {
        Box(
          modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
          contentAlignment = Alignment.Center,
        ) {
          CircularProgressIndicator()
        }
      }
    } else {
      items(uiState.messages, key = { it.uid }) { header ->
        MailHeaderCard(
          header = header,
          onClick = { viewModel.openMessage(context, header) },
        )
      }
    }
  }
}

@Composable
private fun MailHeaderCard(
  header: MailHeader,
  onClick: () -> Unit,
) {
  Surface(
    shape = RoundedCornerShape(12.dp),
    color = MaterialTheme.colorScheme.surface,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(12.dp),
      verticalAlignment = Alignment.Top,
    ) {
      Box(
        modifier =
          Modifier.padding(top = 6.dp, end = 8.dp)
            .size(8.dp)
            .clip(CircleShape)
            .background(
              if (header.isUnread) MaterialTheme.colorScheme.primary
              else MaterialTheme.colorScheme.surface
            )
      )
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = header.fromDisplay,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (header.isUnread) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = header.sentDateText,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Text(
          text = header.subject,
          style = MaterialTheme.typography.bodyLarge,
          fontWeight = if (header.isUnread) FontWeight.SemiBold else FontWeight.Normal,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
        if (header.hasAttachments) {
          Text(
            text = "包含附件",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.primary,
          )
        }
      }
    }
  }
}

@Composable
private fun MailDetailView(
  uiState: MailUiState,
  viewModel: MailViewModel,
) {
  val context = LocalContext.current
  val detail: MailDetail? = uiState.selectedDetail

  Column(modifier = Modifier.fillMaxSize()) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      IconButton(onClick = viewModel::closeMessageDetail) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回收件箱")
      }
      Text(
        text = detail?.subject ?: "正在读取邮件...",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f),
      )
      if (detail != null) {
        FilledTonalButton(
          onClick = viewModel::addSelectedMailToCalendar,
          contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
          modifier = Modifier.height(32.dp),
        ) {
          Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text("加入日历", fontSize = 12.sp)
        }
      }
    }

    if (uiState.statusMessage != null) {
      Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = uiState.statusMessage,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.weight(1f),
          )
          TextButton(onClick = viewModel::clearStatusMessage) { Text("知道了") }
        }
      }
    }

    if (uiState.isLoadingDetail || detail == null) {
      Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
      }
    } else {
      LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        item {
          Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
          ) {
            Column(
              modifier = Modifier.fillMaxWidth().padding(14.dp),
              verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              SelectionContainer {
                Text(
                  text = detail.subject,
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold,
                )
              }
              Text(
                text = "发件人：${detail.fromDisplay}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              if (detail.toDisplay.isNotBlank()) {
                Text(
                  text = "收件人：${detail.toDisplay}",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              Text(
                text = "时间：${detail.sentDateText}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }

        item {
          Surface(
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
          ) {
            Column(
              modifier = Modifier.fillMaxWidth().padding(14.dp),
              verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              detail.bodyBlocks.forEach { block ->
                when (block) {
                  is MailBodyBlock.TextBlock -> {
                    SelectionContainer {
                      Text(text = block.text, style = MaterialTheme.typography.bodyMedium)
                    }
                  }

                  is MailBodyBlock.ImageBlock -> {
                    InlineMailImage(
                      source = block.source,
                      altText = block.altText,
                      inlineImageFiles = detail.inlineImageFiles,
                      onOpenLocalFile = { path -> viewModel.openInlineImageFile(context, path) },
                      onOpenBase64 = { mime, b64 -> viewModel.openBase64Image(context, mime, b64) },
                    )
                  }
                }
              }
            }
          }
        }

        if (detail.attachments.isNotEmpty()) {
          item {
            Text(
              text = "附件 (${detail.attachments.size})",
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.SemiBold,
            )
          }
          items(detail.attachments, key = { it.partIndex }) { att ->
            Surface(
              shape = RoundedCornerShape(10.dp),
              color = MaterialTheme.colorScheme.surfaceContainerLow,
              modifier =
                Modifier.fillMaxWidth().clickable {
                  viewModel.openAttachment(context, detail.uid, att)
                },
            ) {
              Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = att.fileName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                  )
                  Text(
                    text = HtmlTextCleaner.formatFileSize(att.sizeBytes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
                Text(
                  text = "下载打开",
                  style = MaterialTheme.typography.labelMedium,
                  color = MaterialTheme.colorScheme.primary,
                )
              }
            }
          }
        }
      }
    }
  }
}

private val remoteImageClient by lazy { OkHttpClient() }

@Composable
private fun InlineMailImage(
  source: MailImageSource,
  altText: String,
  inlineImageFiles: Map<String, String>,
  onOpenLocalFile: (String) -> Unit,
  onOpenBase64: (String, String) -> Unit,
) {
  val context = LocalContext.current
  var loadedBitmap by remember(source, inlineImageFiles) { mutableStateOf<ImageBitmap?>(null) }
  var cachedPath by remember(source, inlineImageFiles) { mutableStateOf<String?>(null) }

  LaunchedEffect(source, inlineImageFiles) {
    withContext(Dispatchers.IO) {
      when (source) {
        is MailImageSource.Cid -> {
          val path = inlineImageFiles[source.contentId]
          if (!path.isNullOrBlank()) {
            cachedPath = path
            loadedBitmap =
              runCatching { BitmapFactory.decodeFile(path)?.asImageBitmap() }.getOrNull()
          }
        }
        is MailImageSource.Base64Data -> {
          loadedBitmap =
            runCatching {
              val bytes = Base64.decode(source.base64, Base64.DEFAULT)
              BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }.getOrNull()
        }
        is MailImageSource.RemoteUrl -> {
          runCatching {
            val dir = File(FileOpener.downloadsDir(context), "mail_inline").apply { mkdirs() }
            val file = File(dir, "remote_${source.url.hashCode()}.img")
            if (!file.exists() || file.length() == 0L) {
              val req = Request.Builder().url(source.url).get().build()
              remoteImageClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                  resp.body?.byteStream()?.use { input ->
                    file.outputStream().use { output -> input.copyTo(output) }
                  }
                }
              }
            }
            if (file.exists() && file.length() > 0L) {
              cachedPath = file.absolutePath
              loadedBitmap = BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
            }
          }
        }
      }
    }
  }

  val bmp = loadedBitmap
  if (bmp != null) {
    Image(
      bitmap = bmp,
      contentDescription = altText.ifBlank { "邮件内嵌图片" },
      contentScale = ContentScale.FillWidth,
      modifier =
        Modifier.fillMaxWidth()
          .clip(RoundedCornerShape(8.dp))
          .clickable {
            val path = cachedPath
            if (path != null) {
              onOpenLocalFile(path)
            } else if (source is MailImageSource.Base64Data) {
              onOpenBase64(source.mimeType, source.base64)
            }
          },
    )
  } else if (altText.isNotBlank()) {
    Text(
      text = "[图片: $altText]",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}
