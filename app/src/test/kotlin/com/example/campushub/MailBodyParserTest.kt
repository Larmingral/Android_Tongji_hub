package com.example.campushub

import com.example.campushub.data.mail.MailBodyBlock
import com.example.campushub.data.mail.MailBodyParser
import com.example.campushub.data.mail.MailImageSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MailBodyParserTest {

  @Test
  fun normalizeCid_stripsBracketsPrefixAndWhitespace() {
    assertEquals("image001.png@01dc123", MailBodyParser.normalizeCid("  <cid:Image001.png@01DC123> "))
    assertEquals("logo_1", MailBodyParser.normalizeCid("cid:logo_1"))
  }

  @Test
  fun parseHtml_interleavesTextAndCidBase64RemoteImages() {
    val html =
      """
      <div><p>第一段正文</p>
      <img src="cid:Image001.png@01A" alt="海报">
      <p>第二段正文</p>
      <img src="data:image/png;base64,iVBORw0KGgo=" alt="内嵌二维码">
      <img src="https://example.edu/banner.jpg" alt="横幅">
      </div>
      """.trimIndent()

    val blocks =
      MailBodyParser.parseHtml(
        html = html,
        inlineCids = setOf("image001.png@01a", "extra_unreferenced"),
      )

    assertEquals(6, blocks.size)
    assertEquals(MailBodyBlock.TextBlock("第一段正文"), blocks[0])
    assertEquals(
      MailBodyBlock.ImageBlock(MailImageSource.Cid("image001.png@01a"), "海报"),
      blocks[1],
    )
    assertEquals(MailBodyBlock.TextBlock("第二段正文"), blocks[2])
    assertEquals(
      MailBodyBlock.ImageBlock(
        MailImageSource.Base64Data("image/png", "iVBORw0KGgo="),
        "内嵌二维码",
      ),
      blocks[3],
    )
    assertEquals(
      MailBodyBlock.ImageBlock(
        MailImageSource.RemoteUrl("https://example.edu/banner.jpg"),
        "横幅",
      ),
      blocks[4],
    )
    assertTrue(blocks[5] == MailBodyBlock.ImageBlock(MailImageSource.Cid("extra_unreferenced")))
  }
}
