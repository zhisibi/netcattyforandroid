package com.netcatty.mobile.data.mapper

import com.netcatty.mobile.data.local.entity.HostEntity
import com.netcatty.mobile.data.local.entity.PortForwardingRuleEntity
import com.netcatty.mobile.data.local.entity.SshKeyEntity
import com.netcatty.mobile.data.local.entity.SnippetEntity
import com.netcatty.mobile.domain.model.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

private val json = Json { ignoreUnknownKeys = true }

// ─── Host ───

fun HostEntity.toDomain(): Host = Host(
    id = id,
    label = label,
    hostname = hostname,
    port = port,
    username = username,
    authMethod = AuthMethod.valueOf(authMethod),
    passwordEncrypted = passwordEncrypted,
    identityFileId = identityFileId,
    identityFileEncrypted = identityFileEncrypted,
    passphraseEncrypted = passphraseEncrypted,
    group = groupName,
    tags = json.decodeFromString<List<String>>(tags),
    os = os,
    deviceType = DeviceType.valueOf(deviceType),
    protocol = HostProtocol.valueOf(protocol),
    agentForwarding = agentForwarding,
    startupCommand = startupCommand,
    proxyConfig = proxyConfig?.let { json.decodeFromString<ProxyConfig>(it) },
    hostChain = json.decodeFromString<List<String>>(hostChain),
    envVars = json.decodeFromString<List<EnvVar>>(envVars),
    charset = charset,
    themeId = themeId,
    fontFamily = fontFamily,
    fontSize = fontSize,
    distro = distro,
    keepaliveInterval = keepaliveInterval,
    legacyAlgorithms = legacyAlgorithms,
    pinned = pinned,
    lastConnectedAt = lastConnectedAt,
    createdAt = createdAt,
    sftpBookmarks = json.decodeFromString<List<SftpBookmark>>(sftpBookmarks),
    keywordHighlightRules = json.decodeFromString<List<KeywordHighlightRule>>(keywordHighlightRules)
)

fun Host.toEntity(): HostEntity = HostEntity(
    id = id,
    label = label,
    hostname = hostname,
    port = port,
    username = username,
    authMethod = authMethod.name,
    passwordEncrypted = passwordEncrypted,
    identityFileId = identityFileId,
    identityFileEncrypted = identityFileEncrypted,
    passphraseEncrypted = passphraseEncrypted,
    groupName = group,
    tags = json.encodeToString(tags),
    os = os,
    deviceType = deviceType.name,
    protocol = protocol.name,
    agentForwarding = agentForwarding,
    startupCommand = startupCommand,
    proxyConfig = proxyConfig?.let { json.encodeToString(it) },
    hostChain = json.encodeToString(hostChain),
    envVars = json.encodeToString(envVars),
    charset = charset,
    themeId = themeId,
    fontFamily = fontFamily,
    fontSize = fontSize,
    distro = distro,
    keepaliveInterval = keepaliveInterval,
    legacyAlgorithms = legacyAlgorithms,
    pinned = pinned,
    lastConnectedAt = lastConnectedAt,
    createdAt = createdAt,
    sftpBookmarks = json.encodeToString(sftpBookmarks),
    keywordHighlightRules = json.encodeToString(keywordHighlightRules)
)

// ─── SshKey ───

fun SshKeyEntity.toDomain(): SshKey = SshKey(
    id = id,
    label = label,
    type = KeyType.valueOf(type),
    keySize = keySize,
    privateKeyEncrypted = privateKeyEncrypted,
    publicKey = publicKey,
    certificate = certificate,
    passphraseEncrypted = passphraseEncrypted,
    category = KeyCategory.valueOf(category),
    created = created
)

fun SshKey.toEntity(): SshKeyEntity = SshKeyEntity(
    id = id,
    label = label,
    type = type.name,
    keySize = keySize,
    privateKeyEncrypted = privateKeyEncrypted,
    publicKey = publicKey,
    certificate = certificate,
    passphraseEncrypted = passphraseEncrypted,
    category = category.name,
    created = created
)

// ─── Snippet ───

fun SnippetEntity.toDomain(): Snippet = Snippet(
    id = id,
    label = label,
    command = command,
    tags = json.decodeFromString<List<String>>(tags),
    targetHostIds = json.decodeFromString<List<String>>(targetHostIds),
    shortcutKey = shortcutKey,
    noAutoRun = noAutoRun
)

fun Snippet.toEntity(): SnippetEntity = SnippetEntity(
    id = id,
    label = label,
    command = command,
    tags = json.encodeToString(tags),
    targetHostIds = json.encodeToString(targetHostIds),
    shortcutKey = shortcutKey,
    noAutoRun = noAutoRun
)

// ─── PortForwardingRule ───

fun PortForwardingRuleEntity.toDomain(): PortForwardingRule = PortForwardingRule(
    id = id,
    label = label,
    type = PortForwardingType.valueOf(type),
    localPort = localPort,
    bindAddress = bindAddress,
    remoteHost = remoteHost,
    remotePort = remotePort,
    hostId = hostId,
    autoStart = autoStart,
    createdAt = createdAt
)

fun PortForwardingRule.toEntity(): PortForwardingRuleEntity = PortForwardingRuleEntity(
    id = id,
    label = label,
    type = type.name,
    localPort = localPort,
    bindAddress = bindAddress,
    remoteHost = remoteHost,
    remotePort = remotePort,
    hostId = hostId,
    autoStart = autoStart,
    createdAt = createdAt
)
