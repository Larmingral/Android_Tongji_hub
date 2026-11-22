package com.example.campushub

import com.example.campushub.data.mail.ImapMailClient
import com.example.campushub.util.HtmlTextCleaner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MailHelperTest {

  @Test
  fun htmlTextCleaner_stripsScriptsAndFormatsParagraphs() {
    val html =
      """
      <html><head><style>.x{color:red}</style></head>
      <body>
        <div>同济大学教务处通知：</div>
        <p>请于&nbsp;11月25日&lt;周三&gt;前完成选课确认。</p>
        <ul><li>步骤一</li><li>步骤二</li></ul>
      </body></html>
      """.trimIndent()

    val clean = HtmlTextCleaner.toReadableText(html)
    assertTrue(clean.contains("同济大学教务处通知："))
    assertTrue(clean.contains("11月25日<周三>前完成选课确认。"))
    assertTrue(clean.contains("• 步骤一"))
  }

  @Test
  fun formatFileSize_handlesBytesKbMb() {
    assertEquals("未知大小", HtmlTextCleaner.formatFileSize(0))
    assertEquals("1.5 KB", HtmlTextCleaner.formatFileSize(1536))
    assertEquals("2.00 MB", HtmlTextCleaner.formatFileSize(2 * 1024 * 1024))
  }

  @Test
  fun decodeSubject_handlesBlankSubject() {
    assertEquals("（无主题）", ImapMailClient.decodeSubject("  "))
    assertEquals("关于期末考试安排", ImapMailClient.decodeSubject("关于期末考试安排"))
  }
}
