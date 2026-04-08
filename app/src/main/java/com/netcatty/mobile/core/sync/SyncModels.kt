package com.netcatty.mobile.core.sync

import com.netcatty.mobile.domain.model.Host
import com.netcatty.mobile.domain.model.SshKey
import com.netcatty.mobile.domain.model.Snippet
import kotlinx.serialization.Serializable

/**
 * 云同步载荷，与桌面端 CloudSyncManager 兼容。
 * 使用主密码加密后上传。
 */
@Serializable
data class SyncPayload(
    val version: Int = 2,
    val deviceId: String,
    val timestamp: Long = System.currentTimeMillis(),
    val hosts: List<Host> = emptyList(),
    val sshKeys: List<SshKey> = emptyList(),
    val snippets: List<Snippet> = emptyList()
)

@Serializable
data class SyncResult(
    val success: Boolean,
    val uploadedAt: Long? = null,
    val downloadedAt: Long? = null,
    val hostsMerged: Int = 0,
    val keysMerged: Int = 0,
    val snippetsMerged: Int = 0,
    val error: String? = null
)

enum class SyncState { IDLE, UPLOADING, DOWNLOADING, MERGING, ERROR }
enum class SyncProvider { GITHUB, WEBDAV, S3 }
