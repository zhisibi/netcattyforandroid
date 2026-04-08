package com.netcatty.mobile.data.local.entity

import androidx.room.*

@Entity(tableName = "hosts", indices = [
    Index(value = ["groupName"]),
    Index(value = ["lastConnectedAt"])
])
data class HostEntity(
    @PrimaryKey val id: String,
    val label: String,
    val hostname: String,
    val port: Int = 22,
    val username: String,
    val authMethod: String = "PASSWORD",
    val passwordEncrypted: String? = null,
    val identityFileId: String? = null,
    val identityFileEncrypted: String? = null,
    val passphraseEncrypted: String? = null,
    val groupName: String? = null,
    val tags: String = "[]",
    val os: String = "linux",
    val deviceType: String = "GENERAL",
    val protocol: String = "SSH",
    val agentForwarding: Boolean = false,
    val startupCommand: String? = null,
    val proxyConfig: String? = null,
    val hostChain: String = "[]",
    val envVars: String = "[]",
    val charset: String = "UTF-8",
    val themeId: String? = null,
    val fontFamily: String? = null,
    val fontSize: Int? = null,
    val distro: String? = null,
    val keepaliveInterval: Int = 0,
    val legacyAlgorithms: Boolean = false,
    val pinned: Boolean = false,
    val lastConnectedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val sftpBookmarks: String = "[]",
    val keywordHighlightRules: String = "[]"
)

@Entity(tableName = "ssh_keys")
data class SshKeyEntity(
    @PrimaryKey val id: String,
    val label: String,
    val type: String = "ED25519",
    val keySize: Int? = null,
    val privateKeyEncrypted: String,
    val publicKey: String? = null,
    val certificate: String? = null,
    val passphraseEncrypted: String? = null,
    val category: String = "KEY",
    val created: Long = System.currentTimeMillis()
)

@Entity(tableName = "snippets")
data class SnippetEntity(
    @PrimaryKey val id: String,
    val label: String,
    val command: String,
    val tags: String = "[]",
    val targetHostIds: String = "[]",
    val shortcutKey: String? = null,
    val noAutoRun: Boolean = false
)

@Entity(tableName = "port_forwarding_rules")
data class PortForwardingRuleEntity(
    @PrimaryKey val id: String,
    val label: String,
    val type: String = "LOCAL",
    val localPort: Int,
    val bindAddress: String = "127.0.0.1",
    val remoteHost: String? = null,
    val remotePort: Int? = null,
    val hostId: String? = null,
    val autoStart: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
