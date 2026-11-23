package com.example.campushub.data.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class TongjiCredentials(
  val username: String,
  val password: String,
)

data class TongjiCredentialSummary(
  val hasCredentials: Boolean = false,
  val maskedUsername: String? = null,
)

/**
 * Pure JVM AES/GCM/NoPadding helper so cryptographic round-trip behavior
 * can be unit tested directly without AndroidKeyStore.
 */
object AesGcmCipher {
  private const val TRANSFORMATION = "AES/GCM/NoPadding"
  private const val IV_LENGTH_BYTES = 12
  private const val TAG_LENGTH_BITS = 128

  private val random = SecureRandom()

  fun generateAes256Key(): SecretKey =
    KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

  fun encrypt(plaintext: ByteArray, key: SecretKey): ByteArray {
    val cipher = Cipher.getInstance(TRANSFORMATION)
    cipher.init(Cipher.ENCRYPT_MODE, key)
    val iv =
      cipher.iv?.takeIf { it.size == IV_LENGTH_BYTES }
        ?: ByteArray(IV_LENGTH_BYTES).also { random.nextBytes(it) }
    val ciphertext = cipher.doFinal(plaintext)
    return ByteArray(iv.size + ciphertext.size).apply {
      System.arraycopy(iv, 0, this, 0, iv.size)
      System.arraycopy(ciphertext, 0, this, iv.size, ciphertext.size)
    }
  }

  fun decrypt(payload: ByteArray, key: SecretKey): ByteArray {
    require(payload.size > IV_LENGTH_BYTES) { "Encrypted payload is too short" }
    val iv = payload.copyOfRange(0, IV_LENGTH_BYTES)
    val ciphertext = payload.copyOfRange(IV_LENGTH_BYTES, payload.size)
    val cipher = Cipher.getInstance(TRANSFORMATION)
    cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH_BITS, iv))
    return cipher.doFinal(ciphertext)
  }
}

class TongjiCredentialStore(context: Context) {

  val appContext: Context = context.applicationContext

  private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
  private val json = Json { ignoreUnknownKeys = true }
  private val _statusFlow = MutableStateFlow(computeSummary())
  val statusFlow: StateFlow<TongjiCredentialSummary> = _statusFlow.asStateFlow()

  fun hasCredentials(): Boolean =
    !prefs.getString(KEY_ENCRYPTED_PAYLOAD, null).isNullOrBlank() && loadCredentials() != null

  fun maskedUsername(): String? = maskUsername(loadCredentials()?.username)

  private fun computeSummary(): TongjiCredentialSummary {
    val creds = loadCredentials()
    return TongjiCredentialSummary(
      hasCredentials = creds != null,
      maskedUsername = maskUsername(creds?.username),
    )
  }

  private fun maskUsername(raw: String?): String? {
    val username = raw?.trim().orEmpty()
    if (username.isEmpty()) return null
    return if (username.length <= 4) {
      "${username.first()}***"
    } else {
      "${username.take(2)}***${username.takeLast(2)}"
    }
  }

  fun saveCredentials(username: String, password: String) {
    val cleanUser = username.trim()
    val cleanPass = password
    require(cleanUser.isNotEmpty() && cleanPass.isNotEmpty()) { "学号和密码不能为空" }

    val serialized = json.encodeToString(TongjiCredentials(cleanUser, cleanPass)).toByteArray(Charsets.UTF_8)
    val encrypted = AesGcmCipher.encrypt(serialized, getOrCreateSecretKey())
    val encoded = Base64.encodeToString(encrypted, Base64.NO_WRAP)
    prefs.edit().putString(KEY_ENCRYPTED_PAYLOAD, encoded).commit()
    _statusFlow.value = TongjiCredentialSummary(hasCredentials = true, maskedUsername = maskUsername(cleanUser))
  }

  fun loadCredentials(): TongjiCredentials? {
    val encoded = prefs.getString(KEY_ENCRYPTED_PAYLOAD, null) ?: return null
    return runCatching {
      val encrypted = Base64.decode(encoded, Base64.NO_WRAP)
      val key = getExistingSecretKey() ?: return null
      val plaintext = AesGcmCipher.decrypt(encrypted, key).toString(Charsets.UTF_8)
      val creds = json.decodeFromString(TongjiCredentials.serializer(), plaintext)
      creds.takeIf { it.username.isNotBlank() && it.password.isNotBlank() }
    }.getOrNull()
  }

  fun clearCredentials() {
    prefs.edit().remove(KEY_ENCRYPTED_PAYLOAD).commit()
    _statusFlow.value = TongjiCredentialSummary(hasCredentials = false, maskedUsername = null)
  }

  private fun getExistingSecretKey(): SecretKey? {
    val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
    return entry?.secretKey
  }

  private fun getOrCreateSecretKey(): SecretKey {
    getExistingSecretKey()?.let { return it }
    val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
    val spec =
      KeyGenParameterSpec.Builder(
        KEY_ALIAS,
        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
      )
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        .setKeySize(256)
        .build()
    keyGenerator.init(spec)
    return keyGenerator.generateKey()
  }

  companion object {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "campushub_tongji_sso_aes_v1"
    private const val PREFS_NAME = "tongji_sso_credentials"
    private const val KEY_ENCRYPTED_PAYLOAD = "encrypted_payload"
  }
}
