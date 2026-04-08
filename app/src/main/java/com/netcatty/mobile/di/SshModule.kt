package com.netcatty.mobile.di

import com.netcatty.mobile.core.crypto.FieldCryptoManager
import com.netcatty.mobile.core.crypto.SessionKeyHolder
import com.netcatty.mobile.core.ssh.PortForwardingManager
import com.netcatty.mobile.core.ssh.SshSessionManager
import com.netcatty.mobile.domain.repository.KeyRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SshModule {

    @Provides
    @Singleton
    fun provideSshSessionManager(
        cryptoManager: FieldCryptoManager,
        keyRepository: KeyRepository
    ): SshSessionManager {
        return SshSessionManager(cryptoManager, keyRepository)
    }

    @Provides
    @Singleton
    fun providePortForwardingManager(sshSessionManager: SshSessionManager): PortForwardingManager {
        return PortForwardingManager(sshSessionManager)
    }
}
