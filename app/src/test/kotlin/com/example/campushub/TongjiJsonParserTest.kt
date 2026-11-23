package com.example.campushub

import com.example.campushub.data.academic.AcademicNotice
import com.example.campushub.data.academic.TongjiJsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TongjiJsonParserTest {

  @Test
  fun parseSessionAndTermAndPeriods_extractsStructuredValuesAndAuths() {
    val sessionJson =
      """
      {
        "code": 200,
        "data": {
          "uid": "2300001",
          "aesKey": "1234567890abcdef",
          "aesIv": "fedcba0987654321",
          "sessionid": "demo-session-token",
          "user": {
            "uid": "2300001",
            "name": "同学甲",
            "facultyName": "电子与信息工程学院",
            "extend": { "deptOrMajor": "计算机科学与技术" }
          },
          "auths": [
            {"authId": 1001, "authNameCh": "工作台", "urlPath": "/workbench"},
            {"authId": 2002, "authNameCh": "个人课表", "urlPath": "/GraduateStudentTimeTable"}
          ]
        }
      }
      """.trimIndent()
    val session = TongjiJsonParser.parseSessionUser(sessionJson)
    assertEquals("2300001", session.uid)
    assertEquals("同学甲", session.name)
    assertEquals("计算机科学与技术", session.majorName)
    assertEquals(2, session.auths.size)
    assertEquals(2002L, session.auths[1].authId)
    assertEquals("1234567890abcdef", session.aesKey)
    assertEquals("fedcba0987654321", session.aesIv)
    assertEquals("demo-session-token", session.sessionId)

    val termJson =
      """
      {
        "code": 200,
        "data": {
          "schoolCalendar": { "id": 122, "weekNum": 18 },
          "week": 10,
          "simpleName": "2026-2027-1"
        }
      }
      """.trimIndent()
    val term = TongjiJsonParser.parseCurrentTerm(termJson)
    assertEquals(122L, term.calendarId)
    assertEquals(10, term.currentWeek)
    assertEquals("2026-2027-1", term.termName)

    val detailJson =
      """
      {
        "code": 200,
        "data": {
          "noWeekendWorkTimes": [
            {"nodeId": 1, "classNode": "1", "beginTime": "08:00", "endTime": "08:45"},
            {"nodeId": 2, "classNode": "2", "beginTime": "08:50", "endTime": "09:35"}
          ]
        }
      }
      """.trimIndent()
    val periods = TongjiJsonParser.parsePeriodTimes(detailJson)
    assertEquals("08:00", periods[1]?.beginTime)
    assertEquals("09:35", periods[2]?.endTime)
  }

  @Test
  fun parseNoticeListAndDetail_handlesNullListContentAndDetailAttachments() {
    val listJson =
      """
      {
        "code": 200,
        "data": {
          "list": [
            {
              "id": 9,
              "title": "关于期中退课的通知",
              "content": null,
              "createUser": "教务处",
              "publishTime": "2026-11-20 10:00",
              "topStatus": "1"
            }
          ]
        }
      }
      """.trimIndent()
    val notices = TongjiJsonParser.parseNotices(listJson)
    assertEquals(1, notices.size)
    assertFalse(notices[0].isDetailLoaded)

    val detailJson =
      """
      {
        "code": 200,
        "data": {
          "id": 9,
          "title": "关于期中退课的通知",
          "content": "<p><strong><span style=\"color:#ff0000\">请在周五前完成确认</span></strong> <a href=\"https://1.tongji.edu.cn/guide\">查看说明</a></p><p>详情见附件</p>",
          "commonAttachmentList": [
            {"id": 1, "fileName": "退课操作指南.pdf", "fileLacation": "obs/guide.pdf"}
          ]
        }
      }
      """.trimIndent()
    val detailed = TongjiJsonParser.parseNoticeDetail(detailJson, notices[0])
    assertTrue(detailed.isDetailLoaded)
    assertTrue(detailed.contentText.contains("请在周五前完成确认"))
    assertTrue(detailed.contentHtml.contains("color:#ff0000"))
    assertTrue(detailed.contentHtml.contains("<a href=\"https://1.tongji.edu.cn/guide\">"))
    assertEquals(listOf("退课操作指南.pdf"), detailed.attachmentNames)
    assertEquals("obs/guide.pdf", detailed.attachments.first().fileLocation)
  }

  @Test
  fun parseTimetable_handlesI18nFieldsWeeksArrayAndErrorCode() {
    val timetableJson =
      """
      {
        "code": 200,
        "data": [
          {
            "teachingClassId": 3001,
            "courseCode": "CS101",
            "courseName": "编译原理",
            "credits": 3.5,
            "teacherName": "王老师",
            "compulsory": "必修",
            "campusI18n": "嘉定校区",
            "timeTableList": [
              {
                "dayOfWeek": 2,
                "timeStart": 3,
                "timeEnd": 4,
                "roomIdI18n": "安楼A404",
                "weeks": [1, 3, 5, 7],
                "weekstr": "[1, 3, 5, 7]"
              }
            ]
          }
        ]
      }
      """.trimIndent()
    val entries = TongjiJsonParser.parseTimetable(timetableJson)
    assertEquals(1, entries.size)
    assertEquals("编译原理", entries[0].courseName)
    assertEquals("安楼A404", entries[0].roomLabel)
    assertEquals("嘉定校区", entries[0].campus)
    assertEquals(setOf(1, 3, 5, 7), entries[0].weeks)

    val errorResult =
      runCatching {
        TongjiJsonParser.parseTimetable("""{"code":403,"msg":"无权访问当前菜单"}""")
      }
    assertTrue(errorResult.isFailure)
    assertTrue(errorResult.exceptionOrNull()?.message?.contains("403") == true)
  }

  @Test
  fun parseExamsAndGrades_handlesEmptyExamMsgAndCreditInfoAliases() {
    val emptyExam = TongjiJsonParser.parseExamQueryResult("""{"message":"暂未查询到考试信息"}""", "2026-1", true)
    assertTrue(emptyExam.exams.isEmpty())
    assertEquals("暂未查询到考试信息", emptyExam.statusMessage)

    val gradesJson =
      """
      {
        "code": 200,
        "data": {
          "totalGradePoint": "4.65",
          "actualCredit": "88.5",
          "failingCredits": "0",
          "failingCourseCount": "0",
          "term": [
            {
              "termcode": 120,
              "termName": "2025-2026学年第一学期",
              "averagePoint": "4.72",
              "creditInfo": [
                {"courseName": "数据结构", "courseCode": "CS201", "credit": "4.0", "score": "优", "gradePoint": "5.0", "isPass": "1"},
                {"kcmc": "大学物理", "xf": "3.0", "cj": "55", "jd": "0", "isPass": "0"}
              ]
            }
          ]
        }
      }
      """.trimIndent()
    val grades = TongjiJsonParser.parseGrades(gradesJson)
    assertEquals("4.65", grades.totalGradePoint)
    assertEquals(1, grades.terms.size)
    assertEquals(2, grades.terms[0].courses.size)
    assertTrue(grades.terms[0].courses[0].isPassed == true)
    assertFalse(grades.terms[0].courses[1].isPassed == true)
  }

  @Test
  fun parseCalendarTerms_splitsYearAndTermCleanly() {
    val calendarJson =
      """
      {
        "code": 200,
        "data": [
          {"id": 121, "fullName": "2025-2026学年 第2学期", "weekNum": 18, "currentTermFlag": false},
          {"id": 122, "fullName": "2026-2027学年第一学期", "weekNum": 19, "currentTermFlag": true},
          {"id": 123, "fullName": "2026-2027-2", "weekNum": 18, "currentTermFlag": false}
        ]
      }
      """.trimIndent()

    val terms = TongjiJsonParser.parseCalendarTerms(calendarJson)
    assertEquals(3, terms.size)
    assertEquals("2025-2026学年", terms[0].yearLabel)
    assertEquals("第2学期", terms[0].termLabel)
    assertEquals("2026-2027学年", terms[1].yearLabel)
    assertEquals("第一学期", terms[1].termLabel)
    assertEquals("2026-2027学年", terms[2].yearLabel)
    assertEquals("第2学期", terms[2].termLabel)
  }

  @Test
  fun parsePeriodTimes_preventsEveningSlotFromOverwritingPeriod2() {
    val detailJson =
      """
      {
        "code": 200,
        "data": {
          "noWeekendWorkTimes": [
            {"nodeId": 1, "classNode": "1", "beginTime": "08:00", "endTime": "08:45"},
            {"nodeId": 2, "classNode": "2", "beginTime": "08:50", "endTime": "09:35"},
            {"nodeId": 2, "classNode": "11", "beginTime": "20:10", "endTime": "20:55"}
          ]
        }
      }
      """.trimIndent()
    val periods = TongjiJsonParser.parsePeriodTimes(detailJson)
    assertEquals("08:50", periods[2]?.beginTime)
    assertEquals("20:10", periods[11]?.beginTime)

    val duplicateClassNodeJson =
      """
      {
        "code": 200,
        "data": {
          "noWeekendWorkTimes": [
            {"nodeId": 1, "classNode": "1", "beginTime": "08:00", "endTime": "08:45"},
            {"nodeId": 2, "classNode": "2", "beginTime": "08:50", "endTime": "09:35"},
            {"nodeId": 2, "classNode": "晚2", "beginTime": "20:10", "endTime": "20:55"}
          ]
        }
      }
      """.trimIndent()
    val chronologicalPeriods = TongjiJsonParser.parsePeriodTimes(duplicateClassNodeJson)
    assertEquals("08:50", chronologicalPeriods[2]?.beginTime)
    assertEquals("20:10", chronologicalPeriods[3]?.beginTime)
  }
}
