package com.netcatty.mobile.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.netcatty.mobile.data.local.dao.HostDao
import com.netcatty.mobile.data.local.dao.SshKeyDao
import com.netcatty.mobile.data.local.dao.SnippetDao
import com.netcatty.mobile.data.local.entity.*

@Database(
    entities = [
        HostEntity::class,
        SshKeyEntity::class,
        SnippetEntity::class,
        PortForwardingRuleEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun hostDao(): HostDao
    abstract fun sshKeyDao(): SshKeyDao
    abstract fun snippetDao(): SnippetDao
}
