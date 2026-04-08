package com.netcatty.mobile.data.local.dao

import androidx.room.*
import com.netcatty.mobile.data.local.entity.HostEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HostDao {
    @Query("SELECT * FROM hosts ORDER BY pinned DESC, lastConnectedAt DESC")
    fun getAll(): Flow<List<HostEntity>>

    @Query("SELECT * FROM hosts WHERE id = :id")
    suspend fun getById(id: String): HostEntity?

    @Query("SELECT * FROM hosts WHERE groupName = :group ORDER BY label ASC")
    fun getByGroup(group: String): Flow<List<HostEntity>>

    @Query("SELECT * FROM hosts WHERE label LIKE '%' || :query || '%' OR hostname LIKE '%' || :query || '%'")
    fun search(query: String): Flow<List<HostEntity>>

    @Query("SELECT * FROM hosts ORDER BY pinned DESC, lastConnectedAt DESC")
    suspend fun getAllOnce(): List<HostEntity>

    @Upsert
    suspend fun upsert(host: HostEntity)

    @Delete
    suspend fun delete(host: HostEntity)

    @Query("DELETE FROM hosts WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE hosts SET lastConnectedAt = :timestamp WHERE id = :id")
    suspend fun updateLastConnected(id: String, timestamp: Long)
}

@Dao
interface SshKeyDao {
    @Query("SELECT * FROM ssh_keys ORDER BY created DESC")
    fun getAll(): Flow<List<com.netcatty.mobile.data.local.entity.SshKeyEntity>>

    @Query("SELECT * FROM ssh_keys WHERE id = :id")
    suspend fun getById(id: String): com.netcatty.mobile.data.local.entity.SshKeyEntity?

    @Upsert
    suspend fun upsert(key: com.netcatty.mobile.data.local.entity.SshKeyEntity)

    @Delete
    suspend fun delete(key: com.netcatty.mobile.data.local.entity.SshKeyEntity)
}

@Dao
interface SnippetDao {
    @Query("SELECT * FROM snippets ORDER BY label ASC")
    fun getAll(): Flow<List<com.netcatty.mobile.data.local.entity.SnippetEntity>>

    @Query("SELECT * FROM snippets WHERE id = :id")
    suspend fun getById(id: String): com.netcatty.mobile.data.local.entity.SnippetEntity?

    @Upsert
    suspend fun upsert(snippet: com.netcatty.mobile.data.local.entity.SnippetEntity)

    @Delete
    suspend fun delete(snippet: com.netcatty.mobile.data.local.entity.SnippetEntity)
}
