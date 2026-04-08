package com.netcatty.mobile.di

import com.netcatty.mobile.core.sync.CloudSyncEngine
import com.netcatty.mobile.core.sync.GitHubSyncAdapter
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SyncModule {

    @Provides
    @Singleton
    fun provideCloudSyncEngine(
        cloudSyncEngine: CloudSyncEngine
    ): CloudSyncEngine = cloudSyncEngine

    @Provides
    @Singleton
    fun provideGitHubSyncAdapter(
        gitHubSyncAdapter: GitHubSyncAdapter
    ): GitHubSyncAdapter = gitHubSyncAdapter
}
