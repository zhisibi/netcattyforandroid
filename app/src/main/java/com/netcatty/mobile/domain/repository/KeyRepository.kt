package com.netcatty.mobile.domain.repository

import com.netcatty.mobile.domain.model.SshKey
import kotlinx.coroutines.flow.Flow

interface KeyRepository {
    fun getAllKeys(): Flow<List<SshKey>>
    suspend fun getKeyById(id: String): SshKey?
    suspend fun insertKey(key: SshKey)
    suspend fun deleteKey(key: SshKey)
}
