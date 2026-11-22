package com.example.campushub.data.mail

import android.content.Context

data class MailConfig(
  val email: String = "",
  val appPassword: String = "",
  val imapHost: String = DEFAULT_IMAP_HOST,
  val imapPort: Int = DEFAULT_IMAP_PORT,
) {
  companion object {
    const val DEFAULT_IMAP_HOST = "imap.tongji.edu.cn"
    const val DEFAULT_IMAP_PORT = 993
  }
}

class MailConfigStore(context: Context) {
  private val prefs =
    context.applicationContext.getSharedPreferences("mail_config", Context.MODE_PRIVATE)

  fun load(): MailConfig =
    MailConfig(
      email = prefs.getString(KEY_EMAIL, "") ?: "",
      appPassword = prefs.getString(KEY_APP_PASSWORD, "") ?: "",
      imapHost = prefs.getString(KEY_IMAP_HOST, MailConfig.DEFAULT_IMAP_HOST)
        ?: MailConfig.DEFAULT_IMAP_HOST,
      imapPort = prefs.getInt(KEY_IMAP_PORT, MailConfig.DEFAULT_IMAP_PORT),
    )

  fun save(config: MailConfig) {
    prefs
      .edit()
      .putString(KEY_EMAIL, config.email.trim())
      .putString(KEY_APP_PASSWORD, config.appPassword.trim())
      .putString(KEY_IMAP_HOST, config.imapHost.trim().ifEmpty { MailConfig.DEFAULT_IMAP_HOST })
      .putInt(KEY_IMAP_PORT, config.imapPort)
      .apply()
  }

  private companion object {
    const val KEY_EMAIL = "email"
    const val KEY_APP_PASSWORD = "app_password"
    const val KEY_IMAP_HOST = "imap_host"
    const val KEY_IMAP_PORT = "imap_port"
  }
}
