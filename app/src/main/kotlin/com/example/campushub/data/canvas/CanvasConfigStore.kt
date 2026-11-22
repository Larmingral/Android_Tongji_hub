package com.example.campushub.data.canvas

import android.content.Context

enum class CanvasViewMode {
  NATIVE_API,
  WEB_VIEW;

  companion object {
    fun fromNameOrDefault(name: String?): CanvasViewMode =
      entries.firstOrNull { it.name == name } ?: NATIVE_API
  }
}

class CanvasConfigStore(context: Context) {
  private val prefs =
    context.applicationContext.getSharedPreferences("canvas_config", Context.MODE_PRIVATE)

  var baseUrl: String
    get() = prefs.getString(KEY_BASE_URL, DEFAULT_CANVAS_URL) ?: DEFAULT_CANVAS_URL
    set(value) {
      prefs.edit().putString(KEY_BASE_URL, normalizeBaseUrl(value)).apply()
    }

  var accessToken: String
    get() = prefs.getString(KEY_TOKEN, "") ?: ""
    set(value) {
      prefs.edit().putString(KEY_TOKEN, value.trim()).apply()
    }

  var preferredMode: CanvasViewMode
    get() = CanvasViewMode.fromNameOrDefault(prefs.getString(KEY_MODE, CanvasViewMode.NATIVE_API.name))
    set(value) {
      prefs.edit().putString(KEY_MODE, value.name).apply()
    }

  companion object {
    const val DEFAULT_CANVAS_URL = "https://canvas.tongji.edu.cn"
    private const val KEY_BASE_URL = "base_url"
    private const val KEY_TOKEN = "access_token"
    private const val KEY_MODE = "preferred_mode"

    fun normalizeBaseUrl(raw: String): String {
      val trimmed = raw.trim().trimEnd('/')
      if (trimmed.isEmpty()) return DEFAULT_CANVAS_URL
      return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
        trimmed
      } else {
        "https://$trimmed"
      }
    }
  }
}
