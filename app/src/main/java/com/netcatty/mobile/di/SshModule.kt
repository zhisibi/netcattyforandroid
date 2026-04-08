package com.netcatty.mobile.di

import com.netcatty.mobile.core.crypto.FieldCryptoManager
import com.netcatty.mobile.core.crypto.SessionKeyHolder
import com.netcatty.mobile.core.ssh.PortForwardingManager
import com.netcatty.mobile.core.ssh.SshSessionManager
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
    fun provideSshSessionManager(cryptoManager: FieldCryptoManager): SshSessionManager {
        return SshSessionManager(cryptoManager)
    }

    @Provides
    @Singleton
    fun providePortForwardingManager(sshSessionManager: SshSessionManager): PortForwardingManager {
        return PortForwardingManager(sshSessionManager)
    }
}
