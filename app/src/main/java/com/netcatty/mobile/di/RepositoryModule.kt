package com.netcatty.mobile.di

import com.netcatty.mobile.data.repository.HostRepositoryImpl
import com.netcatty.mobile.data.repository.KeyRepositoryImpl
import com.netcatty.mobile.data.repository.SnippetRepositoryImpl
import com.netcatty.mobile.domain.repository.HostRepository
import com.netcatty.mobile.domain.repository.KeyRepository
import com.netcatty.mobile.domain.repository.SnippetRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindHostRepository(impl: HostRepositoryImpl): HostRepository

    @Binds
    @Singleton
    abstract fun bindKeyRepository(impl: KeyRepositoryImpl): KeyRepository

    @Binds
    @Singleton
    abstract fun bindSnippetRepository(impl: SnippetRepositoryImpl): SnippetRepository
}
