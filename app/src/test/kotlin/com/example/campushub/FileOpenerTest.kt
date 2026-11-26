package com.example.campushub

import com.example.campushub.util.FileOpener
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileOpenerTest {

  @Test
  fun isImageFile_recognizesCommonExtensionsAndMimeTypes() {
    assertTrue(FileOpener.isImageFile("notice_banner.PNG"))
    assertTrue(FileOpener.isImageFile("photo.jpg"))
    assertTrue(FileOpener.isImageFile("scan.webp"))
    assertTrue(FileOpener.isImageFile("inline_image", "image/jpeg"))

    assertFalse(FileOpener.isImageFile("schedule.pdf", "application/pdf"))
    assertFalse(FileOpener.isImageFile("homework.docx"))
  }

  @Test
  fun sanitizeFileName_replacesIllegalPathCharacters() {
    assertEquals("a_b_c_d.pdf", FileOpener.sanitizeFileName("a/b:c*d.pdf"))
    assertEquals("fallback", FileOpener.sanitizeFileName("   ", "fallback"))
    assertEquals("Download/TJ Hub", FileOpener.PUBLIC_DISPLAY_DIR)
  }
}
