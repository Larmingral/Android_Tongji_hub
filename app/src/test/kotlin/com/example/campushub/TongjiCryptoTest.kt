package com.example.campushub

import com.example.campushub.data.academic.TongjiCrypto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TongjiCryptoTest {

  @Test
  fun swapAdjacentPairs_matchesFrontendParamHandler() {
    assertEquals("badc", TongjiCrypto.swapAdjacentPairs("abcd"))
    assertEquals("badce", TongjiCrypto.swapAdjacentPairs("abcde"))
  }

  @Test
  fun staticEncrypt_producesDeterministic24CharBase64For7DigitId() {
    val cipherText = TongjiCrypto.staticEncrypt("2400001")
    assertEquals(24, cipherText.length)
    assertTrue(cipherText.endsWith("=="))
  }
}
