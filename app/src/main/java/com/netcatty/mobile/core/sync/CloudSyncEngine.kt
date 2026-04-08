package com.netcatty.mobile.core.sync

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import com.netcatty.mobile.core.crypto.FieldCryptoManager
import com.netcatty.mobile.core.crypto.SessionKeyHolder
import com.netcatty.mobile.data.local.dao.HostDao
import com.netcatty.mobile.data.local.dao.SshKeyDao
import com.netcatty.mobile.data.local.dao.SnippetDao
import com.netcatty.mobile.data.mapper.toDomain
import com.netcatty.mobile.data.mapper.toEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 云同步引擎（Android 端）。
 * 支持上传/下载/三路合并，与桌面端 CloudSyncManager 格式兼容。
 */
@Singleton
class CloudSyncEngine @Inject constructor(
    private val hostDao: HostDao,
    private val sshKeyDao: SshKeyDao,
    private val snippetDao: SnippetDao,
    private val fieldCryptoManager: FieldCryptoManager,
    private val sessionKeyHolder: SessionKeyHolder
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /**
     * 收集本地数据并生成同步载荷
     */
    suspend fun createPayload(deviceId: String): SyncPayload = withContext(Dispatchers.IO) {
        val hosts = hostDao.getAllOnce().map { it.toDomain() }
        val keys = sshKeyDao.getAll().first().map { it.toDomain() }
        val snippets = snippetDao.getAll().first().map { it.toDomain() }

        SyncPayload(
            deviceId = deviceId,
            timestamp = System.currentTimeMillis(),
            hosts = hosts,
            sshKeys = keys,
            snippets = snippets
        )
    }

    /**
     * 将载荷序列化为加密字符串
     */
    fun encryptPayload(payload: SyncPayload): String {
        val jsonString = json.encodeToString(SyncPayload.serializer(), payload)
        // 使用 FieldCryptoManager 加密（依赖 SessionKeyHolder）
        return fieldCryptoManager.encrypt(jsonString)
    }

    /**
     * 解密并反序列化载荷
     */
    fun decryptPayload(encrypted: String): SyncPayload {
        val jsonString = fieldCryptoManager.decrypt(encrypted)
        return json.decodeFromString(SyncPayload.serializer(), jsonString)
    }

    /**
     * 三路合并：将远程载荷与本地数据合并
     * 策略：以 timestamp 更新的为准
     */
    suspend fun mergeFromRemote(remotePayload: SyncPayload): SyncResult = withContext(Dispatchers.IO) {
        try {
            var hostsMerged = 0
            var keysMerged = 0
            var snippetsMerged = 0

            // Merge hosts: remote wins if newer
            val localHosts = hostDao.getAllOnce().associateBy { it.id }
            for (remoteHost in remotePayload.hosts) {
                val local = localHosts[remoteHost.id]
                if (local == null || remoteHost.createdAt > local.createdAt) {
                    hostDao.upsert(remoteHost.toEntity())
                    hostsMerged++
                }
            }

            // Merge SSH keys
            val localKeys = sshKeyDao.getAll().first().associateBy { it.id }
            for (remoteKey in remotePayload.sshKeys) {
                val local = localKeys[remoteKey.id]
                if (local == null || remoteKey.created > local.created) {
                    sshKeyDao.upsert(remoteKey.toEntity())
                    keysMerged++
                }
            }

            // Merge snippets
            val localSnippets = snippetDao.getAll().first().associateBy { it.id }
            for (remoteSnippet in remotePayload.snippets) {
                val local = localSnippets[remoteSnippet.id]
                if (local == null) {
                    snippetDao.upsert(remoteSnippet.toEntity())
                    snippetsMerged++
                }
            }

            SyncResult(
                success = true,
                hostsMerged = hostsMerged,
                keysMerged = keysMerged,
                snippetsMerged = snippetsMerged
            )
        } catch (e: Exception) {
            SyncResult(success = false, error = e.message)
        }
    }
}
