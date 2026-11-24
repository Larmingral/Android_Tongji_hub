package com.example.campushub.ui.auth

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun TongjiSsoStatusRow(
  hasSavedCredentials: Boolean,
  maskedUsername: String?,
  onOpenDialog: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    color =
      if (hasSavedCredentials) {
        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
      } else {
        MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.65f)
      },
    shape = RoundedCornerShape(10.dp),
    modifier = modifier.fillMaxWidth(),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text =
            if (hasSavedCredentials) {
              "自动免登已开启（${maskedUsername ?: "已加密保存"}）"
            } else {
              "未设置统一身份认证自动免登"
            },
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.Medium,
        )
        Text(
          text = "教务与 Canvas 共用本地 Keystore 加密凭证，会话过期自动续期",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      TextButton(
        onClick = onOpenDialog,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
      ) {
        Text(if (hasSavedCredentials) "管理" else "去设置")
      }
    }
  }
}

@Composable
fun TongjiCredentialDialog(
  hasSavedCredentials: Boolean,
  maskedUsername: String?,
  onDismiss: () -> Unit,
  onSave: (username: String, password: String) -> Unit,
  onClear: () -> Unit,
) {
  var username by remember { mutableStateOf("") }
  var password by remember { mutableStateOf("") }
  var showPassword by remember { mutableStateOf(false) }
  var localError by remember { mutableStateOf<String?>(null) }
  var showAbout by remember { mutableStateOf(false) }
  var autoCheckUpdate by remember { mutableStateOf(false) }

  if (showAbout) {
    AboutAppDialog(
      onDismiss = {
        showAbout = false
        autoCheckUpdate = false
      },
      autoCheckUpdates = autoCheckUpdate,
    )
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("同济统一身份认证自动登录", style = MaterialTheme.typography.titleMedium) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
          text =
            if (hasSavedCredentials) {
              "当前已加密保存账号：${maskedUsername ?: "****"}。如需更新请重新输入；教务系统与 Canvas 将共用此凭证自动续期。"
            } else {
              "只需输入一次同济学号与统一身份认证密码，之后教务或 Canvas 会话过期时将在后台自动续期。"
            },
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
          value = username,
          onValueChange = {
            username = it
            localError = null
          },
          label = { Text("学号 / 工号") },
          singleLine = true,
          keyboardOptions =
            KeyboardOptions(
              keyboardType = KeyboardType.Ascii,
              autoCorrectEnabled = false,
              capitalization = KeyboardCapitalization.None,
            ),
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = password,
          onValueChange = {
            password = it
            localError = null
          },
          label = { Text("统一身份认证密码") },
          singleLine = true,
          keyboardOptions =
            KeyboardOptions(
              keyboardType = KeyboardType.Password,
              autoCorrectEnabled = false,
              capitalization = KeyboardCapitalization.None,
            ),
          visualTransformation =
            if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
          trailingIcon = {
            TextButton(onClick = { showPassword = !showPassword }) {
              Text(if (showPassword) "隐藏" else "显示", fontSize = 12.sp)
            }
          },
          modifier = Modifier.fillMaxWidth(),
        )
        if (localError != null) {
          Text(
            text = localError.orEmpty(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
          )
        }
        Text(
          text = "隐私说明：密码仅通过 Android Keystore（AES-GCM 硬件密钥）加密存于本机私有存储，不上传任何第三方服务器。",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.primary,
        )
        Row(
          modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = "${AppAttributionInfo.APP_NAME} · 关于与版权声明",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier =
              Modifier.clickable {
                autoCheckUpdate = false
                showAbout = true
              },
          )
          TextButton(
            onClick = {
              autoCheckUpdate = true
              showAbout = true
            },
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
          ) {
            Text("检查更新", fontSize = 12.sp)
          }
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          val u = username.trim()
          if (u.isEmpty() || password.isEmpty()) {
            localError = "请输入完整的学号和密码"
            return@Button
          }
          onSave(u, password)
        }
      ) {
        Text("加密保存并续期")
      }
    },
    dismissButton = {
      Row {
        if (hasSavedCredentials) {
          TextButton(onClick = onClear) {
            Text("清除凭证", color = MaterialTheme.colorScheme.error)
          }
        }
        TextButton(onClick = onDismiss) { Text("取消") }
      }
    },
  )
}
