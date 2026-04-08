package com.netcatty.mobile.data.repository

import com.netcatty.mobile.data.local.dao.HostDao
import com.netcatty.mobile.data.mapper.toDomain
import com.netcatty.mobile.data.mapper.toEntity
import com.netcatty.mobile.domain.model.Host
import com.netcatty.mobile.domain.repository.HostRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HostRepositoryImpl @Inject constructor(
    private val hostDao: HostDao
) : HostRepository {

    override fun getAllHosts(): Flow<List<Host>> {
        return hostDao.getAll().map { entities -> entities.map { it.toDomain() } }
    }

    override fun getHostsByGroup(group: String): Flow<List<Host>> {
        return hostDao.getByGroup(group).map { entities -> entities.map { it.toDomain() } }
    }

    override fun searchHosts(query: String): Flow<List<Host>> {
        return hostDao.search(query).map { entities -> entities.map { it.toDomain() } }
    }

    override suspend fun getHostById(id: String): Host? {
        return hostDao.getById(id)?.toDomain()
    }

    override suspend fun insertHost(host: Host) {
        hostDao.upsert(host.toEntity())
    }

    override suspend fun updateHost(host: Host) {
        hostDao.upsert(host.toEntity())
    }

    override suspend fun deleteHost(host: Host) {
        hostDao.delete(host.toEntity())
    }

    override suspend fun updateLastConnected(id: String, timestamp: Long) {
        hostDao.updateLastConnected(id, timestamp)
    }

    override suspend fun getAllHostsOnce(): List<Host> {
        return hostDao.getAllOnce().map { it.toDomain() }
    }
}
