package com.netcatty.mobile.domain.model

import java.util.UUID

/**
 * SSH 密钥。
 * 字段与桌面端 SSHKey 对齐。
 */
data class SshKey(
    val id: String = UUID.randomUUID().toString(),
    val label: String,
    val type: KeyType = KeyType.ED25519,
    val keySize: Int? = null,
    val privateKeyEncrypted: String,
    val publicKey: String? = null,
    val certificate: String? = null,
    val passphraseEncrypted: String? = null,
    val category: KeyCategory = KeyCategory.KEY,
    val created: Long = System.currentTimeMillis()
)

enum class KeyType { RSA, ECDSA, ED25519 }
enum class KeyCategory { KEY, CERTIFICATE, IDENTITY }
