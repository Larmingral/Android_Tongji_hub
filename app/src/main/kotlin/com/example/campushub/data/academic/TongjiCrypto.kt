package com.example.campushub.data.academic

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Implements the AES-CBC encryption expected by 1.tongji.edu.cn endpoints such as
 * findStudentTimetab.
 */
object TongjiCrypto {

  private const val STATIC_KEY = "fCm^7p1AwqKPTNjn"
  private const val STATIC_IV = "MaW5a2v%%I9em\$UI"

  fun staticEncrypt(plain: String): String = aesCbcBase64(plain, STATIC_KEY, STATIC_IV)

  fun sessionEncrypt(plain: String, aesKey: String, aesIv: String): String =
    aesCbcBase64(plain, swapAdjacentPairs(aesKey), swapAdjacentPairs(aesIv))

  fun swapAdjacentPairs(input: String): String {
    val chars = input.toCharArray()
    var i = 0
    while (i + 1 < chars.size) {
      val tmp = chars[i]
      chars[i] = chars[i + 1]
      chars[i + 1] = tmp
      i += 2
    }
    return String(chars)
  }

  private fun aesCbcBase64(plain: String, keyStr: String, ivStr: String): String {
    val encodedPlain = encodeUriComponent(plain)
    val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
    val keySpec = SecretKeySpec(keyStr.toByteArray(StandardCharsets.UTF_8), "AES")
    val ivSpec = IvParameterSpec(ivStr.toByteArray(StandardCharsets.UTF_8))
    cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
    val encrypted = cipher.doFinal(encodedPlain.toByteArray(StandardCharsets.UTF_8))
    return Base64.getEncoder().encodeToString(encrypted)
  }

  private fun encodeUriComponent(value: String): String =
    URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}
