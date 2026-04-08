package com.netcatty.mobile.data.repository

import com.netcatty.mobile.data.local.dao.SshKeyDao
import com.netcatty.mobile.data.mapper.toDomain
import com.netcatty.mobile.data.mapper.toEntity
import com.netcatty.mobile.domain.model.SshKey
import com.netcatty.mobile.domain.repository.KeyRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KeyRepositoryImpl @Inject constructor(
    private val sshKeyDao: SshKeyDao
) : KeyRepository {

    override fun getAllKeys(): Flow<List<SshKey>> {
        return sshKeyDao.getAll().map { entities -> entities.map { it.toDomain() } }
    }

    override suspend fun getKeyById(id: String): SshKey? {
        return sshKeyDao.getById(id)?.toDomain()
    }

    override suspend fun insertKey(key: SshKey) {
        sshKeyDao.upsert(key.toEntity())
    }

    override suspend fun deleteKey(key: SshKey) {
        sshKeyDao.delete(key.toEntity())
    }
}
