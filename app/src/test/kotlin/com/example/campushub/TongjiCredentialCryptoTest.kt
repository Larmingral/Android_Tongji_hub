package com.example.campushub

import com.example.campushub.data.auth.AesGcmCipher
import com.example.campushub.data.auth.TongjiSsoAutoLoginRunner
import javax.crypto.AEADBadTagException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class TongjiCredentialCryptoTest {

  @Test
  fun aesGcm_roundTripPreservesPayloadAndUsesRandomIv() {
    val key = AesGcmCipher.generateAes256Key()
    val payload = """{"username":"2400001","password":"DemoPassword!@#123"}""".toByteArray(Charsets.UTF_8)

    val encrypted1 = AesGcmCipher.encrypt(payload, key)
    val encrypted2 = AesGcmCipher.encrypt(payload, key)

    assertFalse("Two encryptions must generate distinct random IVs", encrypted1.contentEquals(encrypted2))
    assertEquals(
      String(payload, Charsets.UTF_8),
      String(AesGcmCipher.decrypt(encrypted1, key), Charsets.UTF_8),
    )
    assertEquals(
      String(payload, Charsets.UTF_8),
      String(AesGcmCipher.decrypt(encrypted2, key), Charsets.UTF_8),
    )
  }

  @Test
  fun aesGcm_wrongKeyThrowsAeadException() {
    val key1 = AesGcmCipher.generateAes256Key()
    val key2 = AesGcmCipher.generateAes256Key()
    val encrypted = AesGcmCipher.encrypt("secret".toByteArray(Charsets.UTF_8), key1)

    try {
      AesGcmCipher.decrypt(encrypted, key2)
      fail("Expected AEADBadTagException when decrypting with a wrong key")
    } catch (_: AEADBadTagException) {
      // expected
    }
  }

  @Test
  fun ssoHelper_recognizesTongjiSsoHostsAndEscapesQuotesInAutofillJs() {
    assertTrue(TongjiSsoAutoLoginRunner.isSsoHost("https://iam.tongji.edu.cn/idp/oauth2/authorize"))
    assertTrue(TongjiSsoAutoLoginRunner.isSsoHost("https://ids.tongji.edu.cn/nidp/idff/sso"))
    assertFalse(TongjiSsoAutoLoginRunner.isSsoHost("https://1.tongji.edu.cn/workbench"))

    val js =
      TongjiSsoAutoLoginRunner.buildAutofillJs(
        username = "2400001",
        password = """AbCDef123!@#XyZ'"\$""",
        autoSubmit = true,
      )
    assertTrue(js.contains("\"2400001\""))
    assertTrue(js.contains("AbCDef123!@#XyZ"))
    assertTrue(js.contains("var autoSubmit = true;"))
    assertTrue(js.contains("CAPTCHA_REQUIRED"))
    assertTrue(js.contains("j_password"))
  }
}
