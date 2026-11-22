package com.example.campushub

import com.example.campushub.data.academic.TongjiJsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TongjiJsonParserTest {

  @Test
  fun parseSessionAndTermAndPeriods_extractsStructuredValues() {
    val sessionJson =
      """
      {
        "code": 200,
        "data": {
          "uid": "2300001",
          "user": {
            "uid": "2300001",
            "name": "同学甲",
            "facultyName": "电子与信息工程学院",
            "extend": { "deptOrMajor": "计算机科学与技术" }
          }
        }
      }
      """.trimIndent()
    val session = TongjiJsonParser.parseSessionUser(sessionJson)
    assertEquals("2300001", session.uid)
    assertEquals("同学甲", session.name)
    assertEquals("计算机科学与技术", session.majorName)

    val termJson =
      """
      {
        "code": 200,
        "data": {
          "schoolCalendar": { "id": 122, "weekNum": 18 },
          "week": 11,
          "simpleName": "2026-2027-1"
        }
      }
      """.trimIndent()
    val term = TongjiJsonParser.parseCurrentTerm(termJson)
    assertEquals(122L, term.calendarId)
    assertEquals(11, term.currentWeek)
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
  fun parseNoticesAndTimetable_handlesWeeksAndHtmlContent() {
    val noticeJson =
      """
      {
        "code": 200,
        "data": {
          "list": [
            {
              "id": 9,
              "title": "关于期中退课的通知",
              "content": "<p>请在周五前完成确认</p>",
              "createUser": "教务处",
              "publishTime": "2026-11-20 10:00",
              "topStatus": "1"
            }
          ]
        }
      }
      """.trimIndent()
    val notices = TongjiJsonParser.parseNotices(noticeJson)
    assertEquals(1, notices.size)
    assertTrue(notices[0].isPinned)
    assertEquals("请在周五前完成确认", notices[0].contentText)

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
            "timeTableList": [
              {
                "dayOfWeek": 2,
                "timeStart": 3,
                "timeEnd": 4,
                "timeAndRoom": "南楼302",
                "weekstr": "1-8,10-12周(单)"
              }
            ]
          }
        ]
      }
      """.trimIndent()
    val entries = TongjiJsonParser.parseTimetable(timetableJson)
    assertEquals(1, entries.size)
    assertEquals("编译原理", entries[0].courseName)
    assertEquals(2, entries[0].dayOfWeek)
    assertEquals(setOf(1, 3, 5, 7, 11), entries[0].weeks)
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
}
