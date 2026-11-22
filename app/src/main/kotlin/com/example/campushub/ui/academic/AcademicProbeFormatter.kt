package com.example.campushub.ui.academic

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object AcademicProbeFormatter {

  private val json = Json { ignoreUnknownKeys = true; isLenient = true }

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
      val rootElement = json.parseToJsonElement(rawJsReturn)
      val obj =
        if (rootElement is JsonPrimitive && rootElement.isString) {
          json.parseToJsonElement(rootElement.content).jsonObject
        } else {
          rootElement.jsonObject
        }

      val title = obj["title"]?.jsonPrimitive?.contentOrNull.orEmpty()
      val route = obj["route"]?.jsonPrimitive?.contentOrNull.orEmpty()
      val apis = obj["apis"] as? JsonArray ?: JsonArray(emptyList())

      val coreApis = mutableListOf<JsonObject>()
      val auxApis = mutableListOf<JsonObject>()
      for (el in apis) {
        val entry = el as? JsonObject ?: continue
        val path = entry["path"]?.jsonPrimitive?.contentOrNull.orEmpty()
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
            val m = it["method"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val p = it["path"]?.jsonPrimitive?.contentOrNull.orEmpty()
            appendLine("- [$m] $p")
          }
          appendLine()
        }

        val tables = obj["tableHeaders"] as? JsonArray
        if (tables != null && tables.isNotEmpty()) {
          appendLine("=== 表格表头 (${tables.size}) ===")
          for (t in tables) {
            appendLine("- $t")
          }
          appendLine()
        }

        val menus = obj["menus"] as? JsonArray
        if (menus != null && menus.isNotEmpty()) {
          appendLine("=== 可见入口/标签 (${menus.size}) ===")
          for (m in menus) {
            appendLine("- ${m.jsonPrimitive.contentOrNull.orEmpty()}")
          }
        }
      }.trim()
    }.getOrDefault(rawJsReturn)
  }

  private fun StringBuilder.appendApiEntry(entry: JsonObject) {
    val method = entry["method"]?.jsonPrimitive?.contentOrNull.orEmpty()
    val path = entry["path"]?.jsonPrimitive?.contentOrNull.orEmpty()
    appendLine("[$method] $path")
    val queryKeys = entry["queryKeys"] as? JsonArray
    if (queryKeys != null && queryKeys.isNotEmpty()) {
      appendLine("  QueryKeys: $queryKeys")
    }
    val req = entry["reqSchema"]
    if (req != null && req !is JsonNull) {
      appendLine("  Req: $req")
    }
    val resp = entry["respSchema"]
    if (resp != null && resp !is JsonNull) {
      appendLine("  Resp: $resp")
    }
    appendLine()
  }
}
