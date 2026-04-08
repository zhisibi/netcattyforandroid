package com.netcatty.mobile.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.netcatty.mobile.data.local.dao.HostDao
import com.netcatty.mobile.data.local.dao.PortForwardingRuleDao
import com.netcatty.mobile.data.local.dao.SshKeyDao
import com.netcatty.mobile.data.local.dao.SnippetDao
import com.netcatty.mobile.data.local.entity.HostEntity
import com.netcatty.mobile.data.local.entity.PortForwardingRuleEntity
import com.netcatty.mobile.data.local.entity.SshKeyEntity
import com.netcatty.mobile.data.local.entity.SnippetEntity

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
    abstract fun portForwardingRuleDao(): PortForwardingRuleDao
}
