package com.example.campushub

import com.example.campushub.data.academic.AcademicNotice
import com.example.campushub.data.academic.ExamEntry
import com.example.campushub.data.academic.TongjiJsonParser
import com.example.campushub.data.local.EventCategory
import com.example.campushub.data.local.EventSource
import java.time.LocalDate
import java.time.LocalDateTime
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

  @Test
  fun parseExamQueryResult_unwrapsNestedDataListUsesExamSiteAndFiltersIncompleteEntries() {
    assertEquals(1, TongjiJsonParser.parseExamDefaultType("""{"code":200,"data":{"defaultType":1}}"""))

    val examCal =
      TongjiJsonParser.parseExamCalendar(
        """{"code":200,"data":{"calendarId":122,"calendarIdI18n":"2026-2027学年第一学期","examType":1,"isOpen":1}}""",
        0L,
      )
    assertEquals(122L, examCal.calendarId)
    assertEquals("2026-2027学年第一学期", examCal.calendarName)
    assertTrue(examCal.isOpen)

    val examJson =
      """
      {
        "code": 200,
        "msg": "ok",
        "data": {
          "switchRrmark": "",
          "data": {
            "total_": 3,
            "list": [
              {
                "courseName": "模拟集成电路分析与设计",
                "newTeachingClassCode": "EIE340501",
                "examTime": "2027-01-11 第18周 星期一 10:30-12:30",
                "examSite": "博楼B116",
                "examSituation": 1,
                "remark": null
              },
              {
                "courseName": "课程设计实验",
                "newTeachingClassCode": "EIE399901",
                "examTime": "",
                "examSite": "博楼B201",
                "examSituation": 1,
                "remark": null
              },
              {
                "courseName": "专题研讨",
                "newTeachingClassCode": "EIE388801",
                "examTime": "2027-01-12 第18周 星期二 10:30-12:30",
                "examSite": "",
                "examSituation": 2,
                "remark": "论文考核"
              }
            ]
          }
        }
      }
      """.trimIndent()

    val parsed = TongjiJsonParser.parseExamQueryResult(examJson, examCal.calendarName, examCal.isOpen)
    assertEquals(1, parsed.exams.size)
    assertEquals("模拟集成电路分析与设计", parsed.exams[0].courseName)
    assertEquals("EIE340501", parsed.exams[0].courseCode)
    assertEquals("2027-01-11 第18周 星期一 10:30-12:30", parsed.exams[0].examTime)
    assertEquals("博楼B116", parsed.exams[0].examRoom)
    assertEquals("", parsed.exams[0].remark)
  }

  @Test
  fun partitionExamsByStartTime_sortsChronologicallyAndSplitsByStartTimeVsNow() {
    val exams =
      listOf(
        ExamEntry("微波工程与天线设计", "EIE340401", "2027-01-14 第18周 星期四 10:30-12:30", "博楼B116", "", "", ""),
        ExamEntry("量子力学", "PSE340102", "2027-01-08 第17周 星期五 10:30-12:30", "博楼B103", "", "", ""),
        ExamEntry("半导体物理", "EIE330601", "2027-01-04 第17周 星期一 08:30-10:30", "博楼B111", "", "", ""),
        ExamEntry("习近平新时代中国特色社会主义思想概论", "CMA111007", "2027-01-04 第17周 星期一 14:00-16:00", "复楼F213", "", "", ""),
      )

    assertEquals(
      LocalDateTime.of(2027, 1, 14, 10, 30),
      TongjiJsonParser.parseExamStartDateTime("2027-01-14 第18周 星期四 10:30-12:30"),
    )

    // Cutover exactly at 2027-01-04 14:00: 08:30 is completed, 14:00 and later are upcoming.
    val partition =
      TongjiJsonParser.partitionExamsByStartTime(
        exams = exams,
        now = LocalDateTime.of(2027, 1, 4, 14, 0),
      )

    assertEquals(listOf("半导体物理"), partition.completed.map { it.courseName })
    assertEquals(
      listOf("习近平新时代中国特色社会主义思想概论", "量子力学", "微波工程与天线设计"),
      partition.upcoming.map { it.courseName },
    )

    val nowJan4Afternoon = LocalDateTime.of(2027, 1, 4, 14, 0)
    assertEquals("今日已考", TongjiJsonParser.formatExamCountdown(exams[2].examTime, nowJan4Afternoon, true))
    assertEquals("今天开考", TongjiJsonParser.formatExamCountdown(exams[3].examTime, nowJan4Afternoon, false))
    assertEquals("还有 4 天", TongjiJsonParser.formatExamCountdown(exams[1].examTime, nowJan4Afternoon, false))
    assertEquals("还有 10 天", TongjiJsonParser.formatExamCountdown(exams[0].examTime, nowJan4Afternoon, false))

    val calendarEvents = TongjiJsonParser.toExamCalendarEvents(exams)
    assertEquals(4, calendarEvents.size)
    assertEquals("考试：微波工程与天线设计", calendarEvents[0].title)
    assertEquals(LocalDate.of(2027, 1, 14).toEpochDay(), calendarEvents[0].dateEpochDay)
    assertEquals("10:30-12:30", calendarEvents[0].timeRange)
    assertEquals(EventCategory.EXAM, calendarEvents[0].category)
    assertEquals(EventSource.TONGJI_EXAM, calendarEvents[0].source)
    assertEquals("tj_exam_EIE340401_2027-01-14", calendarEvents[0].externalId)
  }
}
