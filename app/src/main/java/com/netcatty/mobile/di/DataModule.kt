package com.netcatty.mobile.di

import android.content.Context
import androidx.room.Room
import com.netcatty.mobile.data.local.AppDatabase
import com.netcatty.mobile.data.local.dao.HostDao
import com.netcatty.mobile.data.local.dao.PortForwardingRuleDao
import com.netcatty.mobile.data.local.dao.SshKeyDao
import com.netcatty.mobile.data.local.dao.SnippetDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "netcatty.db"
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideHostDao(db: AppDatabase): HostDao = db.hostDao()

    @Provides
    fun provideSshKeyDao(db: AppDatabase): SshKeyDao = db.sshKeyDao()

    @Provides
    fun provideSnippetDao(db: AppDatabase): SnippetDao = db.snippetDao()

    @Provides
    fun providePortForwardingRuleDao(db: AppDatabase): PortForwardingRuleDao = db.portForwardingRuleDao()
}
