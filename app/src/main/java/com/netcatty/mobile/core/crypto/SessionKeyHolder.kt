package com.netcatty.mobile.core.crypto

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 内存中的会话密钥持有者。
 * 密钥由用户密码通过 PBKDF2 派生，应用退出后清除。
 *
 * 参考 sbssh 项目的 SessionKeyHolder。
 */
@Singleton
class SessionKeyHolder @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val PBKDF2_ITERATIONS = 600_000
        private const val KEY_LENGTH_BITS = 256
        private const val SALT_LENGTH = 32
        private const val PREFS_NAME = "netcatty_secure_prefs"
        private const val KEY_SALT = "pbkdf2_salt"
        private const val KEY_VERIFY_HASH = "pbkdf2_verify_hash"
        private const val KEY_HAS_MASTER = "has_master_password"
    }

    private var derivedKey: SecretKey? = null
    private var masterPassword: String? = null

    private val securePrefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /**
     * 从密码派生会话密钥并存储在内存中。
     * @return true 如果密码正确（或首次设置）
     */
    fun deriveAndStore(password: String): Boolean {
        val salt = getOrCreateSalt()
        val key = deriveKey(password, salt)

        // 验证密码（如果已有验证哈希）
        val storedHash = securePrefs.getString(KEY_VERIFY_HASH, null)
        if (storedHash != null) {
            val currentHash = hashKey(key)
            if (currentHash != storedHash) {
                return false  // 密码错误
            }
        }

        // 首次设置：保存验证哈希
        if (storedHash == null) {
            securePrefs.edit()
                .putString(KEY_VERIFY_HASH, hashKey(key))
                .putBoolean(KEY_HAS_MASTER, true)
                .apply()
        }

        derivedKey = key
        masterPassword = password
        return true
    }

    /**
     * 设置新主密码（首次或修改密码时）
     */
    fun setupNewPassword(password: String) {
        val salt = ByteArray(SALT_LENGTH).also { SecureRandom().nextBytes(it) }
        val key = deriveKey(password, salt)
        val hash = hashKey(key)

        securePrefs.edit()
            .putString(KEY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(KEY_VERIFY_HASH, hash)
            .putBoolean(KEY_HAS_MASTER, true)
            .apply()

        derivedKey = key
        masterPassword = password
    }

    /**
     * 修改主密码
     */
    fun changePassword(oldPassword: String, newPassword: String): Boolean {
        val salt = getOrCreateSalt()
        val oldKey = deriveKey(oldPassword, salt)

        val storedHash = securePrefs.getString(KEY_VERIFY_HASH, null)
        if (storedHash != null && hashKey(oldKey) != storedHash) {
            return false  // 旧密码错误
        }

        // 生成新 salt 和哈希
        val newSalt = ByteArray(SALT_LENGTH).also { SecureRandom().nextBytes(it) }
        val newKey = deriveKey(newPassword, newSalt)
        val newHash = hashKey(newKey)

        securePrefs.edit()
            .putString(KEY_SALT, Base64.encodeToString(newSalt, Base64.NO_WRAP))
            .putString(KEY_VERIFY_HASH, newHash)
            .apply()

        derivedKey = newKey
        masterPassword = newPassword
        return true
    }

    /**
     * 验证密码是否正确（不改变当前密钥状态）
     */
    fun verifyPassword(password: String): Boolean {
        val salt = getOrCreateSalt()
        val key = deriveKey(password, salt)
        val storedHash = securePrefs.getString(KEY_VERIFY_HASH, null) ?: return false
        return hashKey(key) == storedHash
    }

    /**
     * 获取内存中的派生密钥
     */
    fun getKey(): SecretKey? = derivedKey

    /**
     * 获取主密码（用于云同步加密）
     */
    fun getMasterPassword(): String? = masterPassword

    /**
     * 是否已设置主密码
     */
    fun hasMasterPassword(): Boolean {
        return securePrefs.getBoolean(KEY_HAS_MASTER, false)
    }

    /**
     * 清除内存中的密钥
     */
    fun clear() {
        derivedKey = null
        masterPassword = null
    }

    /**
     * 是否已解锁（密钥在内存中）
     */
    fun isUnlocked(): Boolean = derivedKey != null

    // ─── Private helpers ───

    private fun deriveKey(password: String, salt: ByteArray): SecretKey {
        val spec = PBEKeySpec(
            password.toCharArray(),
            salt,
            PBKDF2_ITERATIONS,
            KEY_LENGTH_BITS
        )
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }

    private fun getOrCreateSalt(): ByteArray {
        val stored = securePrefs.getString(KEY_SALT, null)
        if (stored != null) {
            return Base64.decode(stored, Base64.NO_WRAP)
        }
        // 首次使用，生成新 salt
        val salt = ByteArray(SALT_LENGTH).also { SecureRandom().nextBytes(it) }
        securePrefs.edit()
            .putString(KEY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .apply()
        return salt
    }

    private fun hashKey(key: SecretKey): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(key.encoded)
        return Base64.encodeToString(hash, Base64.NO_WRAP)
    }
}
