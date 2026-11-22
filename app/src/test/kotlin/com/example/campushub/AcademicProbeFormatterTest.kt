package com.example.campushub

import com.example.campushub.ui.academic.AcademicProbeFormatter
import org.junit.Assert.assertTrue
import org.junit.Test

class AcademicProbeFormatterTest {

  @Test
  fun formatProbeJson_separatesCoreAndAuxApisAndIncludesSchemas() {
    val raw =
      """
      {
        "title": "同济大学-教学管理信息系统",
        "route": "/workbench",
        "apis": [
          {
            "method": "POST",
            "path": "/api/commonservice/commonMsgPublish/findMyCommonMsgPublish",
            "queryKeys": ["pageNum", "pageSize"],
            "reqSchema": {"pageNum": "<number>"},
            "respSchema": {"code": "<number>", "data": {"list": [{"title": "<string>"}]}}
          },
          {
            "method": "PUT",
            "path": "/api/sessionservice/session/setLanguage",
            "queryKeys": [],
            "reqSchema": null,
            "respSchema": null
          }
        ],
        "tableHeaders": [["课程名称", "总评成绩"]],
        "menus": ["我的课表", "考试安排"]
      }
      """.trimIndent()

    val formatted = AcademicProbeFormatter.formatProbeJson(raw)
    assertTrue(formatted.contains("=== 核心接口 Schema (1) ==="))
    assertTrue(formatted.contains("[POST] /api/commonservice/commonMsgPublish/findMyCommonMsgPublish"))
    assertTrue(formatted.contains("Resp:"))
    assertTrue(formatted.contains("\"code\":\"<number>\""))
    assertTrue(formatted.contains("\"title\":\"<string>\""))
    assertTrue(formatted.contains("=== 辅助接口 (1) ==="))
    assertTrue(formatted.contains("=== 表格表头 (1) ==="))
    assertTrue(formatted.contains("=== 可见入口/标签 (2) ==="))
  }
}
