package com.netcatty.mobile.core.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 字段级 AES-GCM 加密管理器。
 * 与 sbssh 项目的 FieldCryptoManager 设计一致。
 *
 * 用于加密 Host.password、SSHKey.privateKey 等敏感字段。
 * 密钥由 SessionKeyHolder 在内存中持有。
 */
@Singleton
class FieldCryptoManager @Inject constructor(
    private val sessionKeyHolder: SessionKeyHolder
) {
    companion object {
        private const val AES_GCM = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 128
    }

    /**
     * 加密明文字符串 → Base64(iv + ciphertext)
     */
    fun encrypt(plaintext: String): String {
        val key = sessionKeyHolder.getKey()
            ?: throw SecurityException("Session key not available — unlock vault first")

        val iv = ByteArray(GCM_IV_LENGTH).also { java.security.SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance(AES_GCM)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        // Format: Base64(iv || ciphertext)
        val combined = iv + ciphertext
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    /**
     * 解密 Base64(iv + ciphertext) → 明文字符串
     */
    fun decrypt(encrypted: String): String {
        val key = sessionKeyHolder.getKey()
            ?: throw SecurityException("Session key not available — unlock vault first")

        val combined = Base64.decode(encrypted, Base64.NO_WRAP)
        require(combined.size > GCM_IV_LENGTH) { "Invalid encrypted data" }

        val iv = combined.sliceArray(0 until GCM_IV_LENGTH)
        val ciphertext = combined.sliceArray(GCM_IV_LENGTH until combined.size)

        val cipher = Cipher.getInstance(AES_GCM)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    /**
     * 检查密钥是否可用
     */
    fun isKeyAvailable(): Boolean = sessionKeyHolder.getKey() != null
}
