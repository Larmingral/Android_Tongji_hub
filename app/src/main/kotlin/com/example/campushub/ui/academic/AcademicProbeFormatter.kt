package com.example.campushub.ui.academic

import org.json.JSONArray
import org.json.JSONObject

object AcademicProbeFormatter {

  private val noisePathKeywords =
    listOf(
      "setLanguage",
      "operationGuide",
      "helpCenter",
      "dictionary/query",
      "questionnaireStudent/force",
    )

  fun formatProbeJson(rawJsReturn: String?): String {
    if (rawJsReturn.isNullOrBlank() || rawJsReturn == "null") {
      return "未能读取当前页面结构，请等待页面加载完成后重试。"
    }
    return runCatching {
      val unescaped =
        if (rawJsReturn.startsWith("\"") && rawJsReturn.endsWith("\"")) {
          JSONObject("{\"v\":$rawJsReturn}").getString("v")
        } else {
          rawJsReturn
        }
      val obj = JSONObject(unescaped)
      val title = obj.optString("title")
      val route = obj.optString("route")
      val apis = obj.optJSONArray("apis") ?: JSONArray()

      val coreApis = mutableListOf<JSONObject>()
      val auxApis = mutableListOf<JSONObject>()
      for (i in 0 until apis.length()) {
        val entry = apis.optJSONObject(i) ?: continue
        val path = entry.optString("path")
        if (noisePathKeywords.any { path.contains(it, ignoreCase = true) }) {
          auxApis.add(entry)
        } else {
          coreApis.add(entry)
        }
      }

      buildString {
        appendLine("标题: $title")
        appendLine("路由: $route")
        appendLine()
        appendLine("=== 核心接口 Schema (${coreApis.size}) ===")
        if (coreApis.isEmpty()) {
          appendLine("（暂无，请尝试刷新当前页面或切进子菜单后再探测）")
        } else {
          coreApis.forEach { appendApiEntry(it) }
        }

        if (auxApis.isNotEmpty()) {
          appendLine("=== 辅助接口 (${auxApis.size}) ===")
          auxApis.forEach {
            appendLine("- [${it.optString("method")}] ${it.optString("path")}")
          }
          appendLine()
        }

        val tables = obj.optJSONArray("tableHeaders")
        if (tables != null && tables.length() > 0) {
          appendLine("=== 表格表头 (${tables.length()}) ===")
          for (i in 0 until tables.length()) {
            appendLine("- ${tables.optJSONArray(i)}")
          }
          appendLine()
        }

        val menus = obj.optJSONArray("menus")
        if (menus != null && menus.length() > 0) {
          appendLine("=== 可见入口/标签 (${menus.length()}) ===")
          for (i in 0 until menus.length()) {
            appendLine("- ${menus.optString(i)}")
          }
        }
      }.trim()
    }.getOrDefault(rawJsReturn)
  }

  private fun StringBuilder.appendApiEntry(entry: JSONObject) {
    val method = entry.optString("method")
    val path = entry.optString("path")
    appendLine("[$method] $path")
    val queryKeys = entry.optJSONArray("queryKeys")
    if (queryKeys != null && queryKeys.length() > 0) {
      appendLine("  QueryKeys: $queryKeys")
    }
    val req = entry.opt("reqSchema")
    if (req != null && req != JSONObject.NULL) {
      appendLine("  Req: $req")
    }
    val resp = entry.opt("respSchema")
    if (resp != null && resp != JSONObject.NULL) {
      appendLine("  Resp: $resp")
    }
    appendLine()
  }
}
