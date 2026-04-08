package com.netcatty.mobile.domain.model

import java.util.UUID

/**
 * SSH 主机配置。
 * 字段与桌面端 Netcatty domain/models.ts Host 接口对齐。
 */
data class Host(
    val id: String = UUID.randomUUID().toString(),
    val label: String,
    val hostname: String,
    val port: Int = 22,
    val username: String,
    val authMethod: AuthMethod = AuthMethod.PASSWORD,
    val passwordEncrypted: String? = null,
    val identityFileId: String? = null,
    val identityFileEncrypted: String? = null,
    val passphraseEncrypted: String? = null,
    val group: String? = null,
    val tags: List<String> = emptyList(),
    val os: String = "linux",
    val deviceType: DeviceType = DeviceType.GENERAL,
    val protocol: HostProtocol = HostProtocol.SSH,
    val agentForwarding: Boolean = false,
    val startupCommand: String? = null,
    val proxyConfig: ProxyConfig? = null,
    val hostChain: List<String> = emptyList(),
    val envVars: List<EnvVar> = emptyList(),
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
    val sftpBookmarks: List<SftpBookmark> = emptyList(),
    val keywordHighlightRules: List<KeywordHighlightRule> = emptyList()
)

enum class AuthMethod { PASSWORD, KEY, CERTIFICATE }
enum class HostProtocol { SSH, TELNET, LOCAL, SERIAL }
enum class DeviceType { GENERAL, NETWORK }

data class ProxyConfig(
    val type: String = "socks5",
    val host: String,
    val port: Int,
    val username: String? = null,
    val password: String? = null
)

data class EnvVar(
    val name: String,
    val value: String
)

data class SftpBookmark(
    val id: String = UUID.randomUUID().toString(),
    val path: String,
    val label: String,
    val global: Boolean = false
)

data class KeywordHighlightRule(
    val id: String = UUID.randomUUID().toString(),
    val label: String,
    val patterns: List<String>,
    val color: String,
    val enabled: Boolean = true
)
