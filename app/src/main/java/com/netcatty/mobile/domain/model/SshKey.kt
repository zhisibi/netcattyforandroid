package com.netcatty.mobile.domain.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
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

@Serializable
enum class KeyType { RSA, ECDSA, ED25519 }

@Serializable
enum class KeyCategory { KEY, CERTIFICATE, IDENTITY }
