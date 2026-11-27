package com.nexus.prm.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.nio.ByteBuffer
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

interface LocalStorageCrypto {
  fun encrypt(plaintext: ByteArray): ByteArray
  fun decrypt(ciphertext: ByteArray): ByteArray
}

class KeystoreStorageCrypto(
  private val keyAlias: String = "nexus_directory_aes_key",
) : LocalStorageCrypto {

  private fun getOrCreateKey(): SecretKey {
    val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    val existing = keyStore.getKey(keyAlias, null) as? SecretKey
    if (existing != null) return existing

    val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
    val spec = KeyGenParameterSpec.Builder(
      keyAlias,
      KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
    )
      .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
      .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
      .setKeySize(256)
      .build()
    keyGenerator.init(spec)
    return keyGenerator.generateKey()
  }

  override fun encrypt(plaintext: ByteArray): ByteArray {
    val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
    cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
    val iv = cipher.iv
    val encrypted = cipher.doFinal(plaintext)
    return ByteBuffer.allocate(1 + iv.size + encrypted.size)
      .put(iv.size.toByte())
      .put(iv)
      .put(encrypted)
      .array()
  }

  override fun decrypt(ciphertext: ByteArray): ByteArray {
    require(ciphertext.size > 2) { "Encrypted payload is too short" }
    val ivLength = ciphertext[0].toInt() and 0xFF
    require(ivLength in 12..16 && ciphertext.size > 1 + ivLength) { "Invalid IV length" }
    val iv = ciphertext.copyOfRange(1, 1 + ivLength)
    val encrypted = ciphertext.copyOfRange(1 + ivLength, ciphertext.size)
    val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
    cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
    return cipher.doFinal(encrypted)
  }

  companion object {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128
  }
}

object BackupCrypto {
  private val MAGIC = byteArrayOf('N'.code.toByte(), 'X'.code.toByte(), 'B'.code.toByte(), 'K'.code.toByte())
  const val CURRENT_FORMAT_VERSION = 1
  private const val SALT_SIZE = 16
  private const val IV_SIZE = 12
  private const val HEADER_SIZE = 4 + 4 + SALT_SIZE + 4 + IV_SIZE // 40 bytes
  private const val DEFAULT_ITERATIONS = 120_000
  private const val KEY_BITS = 256
  private const val GCM_TAG_BITS = 128

  fun encrypt(plaintext: ByteArray, password: String): ByteArray {
    require(password.isNotEmpty()) { "Backup password must not be empty" }
    val random = SecureRandom()
    val salt = ByteArray(SALT_SIZE).also(random::nextBytes)
    val iv = ByteArray(IV_SIZE).also(random::nextBytes)
    val key = deriveKey(password, salt, DEFAULT_ITERATIONS)

    val header = ByteBuffer.allocate(HEADER_SIZE)
      .put(MAGIC)
      .putInt(CURRENT_FORMAT_VERSION)
      .put(salt)
      .putInt(DEFAULT_ITERATIONS)
      .put(iv)
      .array()

    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
    cipher.updateAAD(header)
    val encrypted = cipher.doFinal(plaintext)

    return ByteArray(header.size + encrypted.size).apply {
      System.arraycopy(header, 0, this, 0, header.size)
      System.arraycopy(encrypted, 0, this, header.size, encrypted.size)
    }
  }

  fun decrypt(blob: ByteArray, password: String): ByteArray {
    require(password.isNotEmpty()) { "Backup password must not be empty" }
    require(blob.size > HEADER_SIZE + 16) { "Backup file is damaged or incomplete" }

    val buffer = ByteBuffer.wrap(blob)
    val magic = ByteArray(4).also(buffer::get)
    require(magic.contentEquals(MAGIC)) { "Not a valid Nexus backup file" }

    val version = buffer.int
    require(version in 1..CURRENT_FORMAT_VERSION) { "Unsupported backup version: $version" }

    val salt = ByteArray(SALT_SIZE).also(buffer::get)
    val iterations = buffer.int
    require(iterations in 10_000..2_000_000) { "Invalid backup key derivation parameters" }

    val iv = ByteArray(IV_SIZE).also(buffer::get)
    val header = blob.copyOfRange(0, HEADER_SIZE)
    val ciphertext = blob.copyOfRange(HEADER_SIZE, blob.size)

    val key = deriveKey(password, salt, iterations)
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
    cipher.updateAAD(header)
    return cipher.doFinal(ciphertext)
  }

  private fun deriveKey(password: String, salt: ByteArray, iterations: Int): SecretKey {
    val spec = PBEKeySpec(password.toCharArray(), salt, iterations, KEY_BITS)
    return try {
      val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
      val bytes = factory.generateSecret(spec).encoded
      SecretKeySpec(bytes, "AES")
    } finally {
      spec.clearPassword()
    }
  }
}
