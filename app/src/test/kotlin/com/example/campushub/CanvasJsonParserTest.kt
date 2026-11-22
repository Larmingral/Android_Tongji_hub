package com.example.campushub

import com.example.campushub.data.canvas.CanvasCourse
import com.example.campushub.data.canvas.CanvasJsonParser
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CanvasJsonParserTest {

  private val shanghai = ZoneId.of("Asia/Shanghai")

  @Test
  fun parseUserAndCourses_extractsFieldsAndFiltersRestricted() {
    val user = CanvasJsonParser.parseUser("""{"id":101,"name":"张三","short_name":"张三"}""")
    assertEquals(101L, user.id)
    assertEquals("张三", user.name)

    val coursesJson =
      """
      [
        {"id":1,"name":"高等数学","course_code":"MATH101","access_restricted_by_date":false},
        {"id":2,"name":"旧课程","access_restricted_by_date":true}
      ]
      """.trimIndent()
    val courses = CanvasJsonParser.parseCourses(coursesJson)
    assertEquals(1, courses.size)
    assertEquals("高等数学", courses[0].name)
  }

  @Test
  fun parsePlannerAndMergeWithCourseAssignments_tracksCompletedAndIds() {
    val plannerJson =
      """
      [
        {
          "plannable_type": "assignment",
          "plannable_id": 555,
          "course_id": 1,
          "plannable_date": "2026-11-25T15:59:00Z",
          "context_name": "软件工程",
          "html_url": "/courses/1/assignments/555",
          "plannable": {
            "id": 555,
            "title": "实验三报告",
            "due_at": "2026-11-25T15:59:00Z"
          },
          "submissions": { "submitted": false, "graded": false }
        }
      ]
      """.trimIndent()

    val courseAssignJson =
      """
      [
        {
          "id": 555,
          "course_id": 1,
          "name": "实验三报告",
          "due_at": "2026-11-25T15:59:00Z",
          "points_possible": 100.0,
          "html_url": "https://canvas.tongji.edu.cn/courses/1/assignments/555",
          "submission": { "workflow_state": "graded", "score": 95.0, "grade": "95" }
        }
      ]
      """.trimIndent()

    val plannerTasks =
      CanvasJsonParser.parsePlannerItems(plannerJson, "https://canvas.tongji.edu.cn", shanghai)
    val courseTasks =
      CanvasJsonParser.parseCourseAssignments(
        courseAssignJson,
        CanvasCourse(id = 1, name = "软件工程"),
        "https://canvas.tongji.edu.cn",
        shanghai,
      )

    val merged = CanvasJsonParser.mergeDashboardTasks(plannerTasks, courseTasks)
    assertEquals(1, merged.size)
    val item = merged.first()
    assertEquals(1L, item.courseId)
    assertEquals(555L, item.assignmentId)
    assertTrue(item.isSubmitted)
    assertEquals(95.0, item.score ?: 0.0, 0.001)
    assertEquals(LocalDate.of(2026, 11, 25), item.dueDate)
  }

  @Test
  fun parseAssignmentDetailAndCourseContent_cleansHtmlAndParsesModulesFiles() {
    val detailJson =
      """
      {
        "id": 777,
        "course_id": 2,
        "name": "期中大作业",
        "description": "<p>请提交<strong>PDF</strong>报告</p><ul><li>第1部分</li><li>第2部分</li></ul>",
        "due_at": "2026-11-30T15:59:00Z",
        "points_possible": 50.0,
        "submission_types": ["online_upload"],
        "submission": {
          "workflow_state": "submitted",
          "submitted_at": "2026-11-22T10:00:00Z",
          "submission_comments": [{"author_name": "李老师", "comment": "收到，排版很好"}]
        }
      }
      """.trimIndent()

    val detail =
      CanvasJsonParser.parseAssignmentDetail(
        detailJson,
        courseId = 2,
        courseName = "人工智能",
        baseUrl = "https://canvas.tongji.edu.cn",
        zoneId = shanghai,
      )
    assertTrue(detail.isSubmitted)
    assertTrue(detail.descriptionText.contains("请提交PDF报告"))
    assertTrue(detail.descriptionText.contains("• 第1部分"))
    assertEquals("李老师: 收到，排版很好", detail.comments.first())

    val modulesJson =
      """
      [
        {
          "id": 10,
          "name": "第1章 绪论",
          "items": [
            {"id": 11, "title": "Lecture01.pdf", "type": "File", "content_id": 99, "url": "/api/v1/courses/2/files/99"}
          ]
        }
      ]
      """.trimIndent()
    val modules = CanvasJsonParser.parseModules(modulesJson, "https://canvas.tongji.edu.cn")
    assertEquals(1, modules.size)
    assertEquals("https://canvas.tongji.edu.cn/api/v1/courses/2/files/99", modules[0].items[0].apiUrl)

    val filesJson =
      """[{"id": 99, "display_name": "Lecture01.pdf", "size": 204800, "content-type": "application/pdf", "url": "https://canvas.tongji.edu.cn/files/99/download"}]"""
    val files = CanvasJsonParser.parseFiles(filesJson)
    assertEquals("Lecture01.pdf", files[0].displayName)
    assertFalse(files[0].downloadUrl.isNullOrBlank())
  }
}
