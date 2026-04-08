package com.netcatty.mobile.domain.repository

import com.netcatty.mobile.domain.model.Host
import kotlinx.coroutines.flow.Flow

interface HostRepository {
    fun getAllHosts(): Flow<List<Host>>
    fun getHostsByGroup(group: String): Flow<List<Host>>
    fun searchHosts(query: String): Flow<List<Host>>
    suspend fun getHostById(id: String): Host?
    suspend fun insertHost(host: Host)
    suspend fun updateHost(host: Host)
    suspend fun deleteHost(host: Host)
    suspend fun updateLastConnected(id: String, timestamp: Long)
    suspend fun getAllHostsOnce(): List<Host>
}
