package com.netcatty.mobile.core.ssh

import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.jcraft.jsch.ChannelShell
import com.jcraft.jsch.UserInfo
import com.netcatty.mobile.core.crypto.FieldCryptoManager
import com.netcatty.mobile.domain.model.AuthMethod
import com.netcatty.mobile.domain.model.Host
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SSH 会话生命周期管理器。
 * 单例，管理所有活跃的 SSH Session。
 *
 * 对应桌面端 sshBridge.cjs 的核心功能。
 */
@Singleton
class SshSessionManager @Inject constructor(
    private val cryptoManager: FieldCryptoManager
) {
    private val connections = ConcurrentHashMap<String, SshConnection>()
    private val jsch = JSch()

    /**
     * 建立 SSH 连接并返回连接对象
     */
    suspend fun connect(host: Host, passwordOverride: String? = null): Result<SshConnection> =
        withContext(Dispatchers.IO) {
            try {
                val session = jsch.getSession(host.username, host.hostname, host.port)

                // 配置认证
                configureAuth(session, host, passwordOverride)

                // 严格主机密钥检查（可配置）
                session.setConfig("StrictHostKeyChecking", "no")  // TODO: 实现交互式 known_hosts 验证

                // 连接超时
                session.connect(30000)

                // 创建 shell channel
                val channel = session.openChannel("shell") as ChannelShell
                channel.setPtyType("xterm-256color", 80, 24, 800, 600)
                channel.connect()

                val connection = SshConnection(
                    id = UUID.randomUUID().toString(),
                    hostId = host.id,
                    hostLabel = host.label,
                    username = host.username,
                    hostname = host.hostname,
                    port = host.port,
                    session = session,
                    channel = channel,
                    inputStream = channel.inputStream,
                    outputStream = channel.outputStream,
                    status = SshConnection.ConnectionStatus.CONNECTED
                )

                connections[connection.id] = connection
                Result.success(connection)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * 向终端写入数据（用户输入）
     */
    fun write(sessionId: String, data: String) {
        try {
            connections[sessionId]?.outputStream?.apply {
                write(data.toByteArray(Charsets.UTF_8))
                flush()
            }
        } catch (_: Exception) { }
    }

    /**
     * 调整终端尺寸
     */
    fun resize(sessionId: String, cols: Int, rows: Int) {
        try {
            connections[sessionId]?.channel?.setPtySize(cols, rows, cols * 8, rows * 16)
        } catch (_: Exception) { }
    }

    /**
     * 断开指定会话
     */
    fun disconnect(sessionId: String) {
        connections.remove(sessionId)?.apply {
            try { channel.disconnect() } catch (_: Exception) { }
            try { session.disconnect() } catch (_: Exception) { }
        }
    }

    /**
     * 获取活跃连接
     */
    fun getConnection(sessionId: String): SshConnection? = connections[sessionId]

    /**
     * 获取所有活跃会话 ID
     */
    fun getActiveSessionIds(): List<String> = connections.keys.toList()

    /**
     * 断开所有会话
     */
    fun disconnectAll() {
        connections.keys.toList().forEach { disconnect(it) }
    }

    // ─── Auth configuration ───

    private fun configureAuth(session: Session, host: Host, passwordOverride: String?) {
        when (host.authMethod) {
            AuthMethod.PASSWORD -> {
                val password = passwordOverride
                    ?: host.passwordEncrypted?.let { cryptoManager.decrypt(it) }
                if (password != null) {
                    session.setPassword(password)
                }
            }
            AuthMethod.KEY -> {
                // TODO: 从 KeyRepository 获取密钥并添加到 JSch
            }
            AuthMethod.CERTIFICATE -> {
                // TODO: 证书认证
            }
        }

        // 通用设置
        session.setConfig("PreferredAuthentications", "publickey,password,keyboard-interactive")

        // Keepalive
        if (host.keepaliveInterval > 0) {
            session.setServerAliveInterval(host.keepaliveInterval * 1000)
        }

        // Agent forwarding
        if (host.agentForwarding) {
            session.setConfig("ForwardAgent", "yes")
        }

        // Legacy algorithms for older network equipment
        if (host.legacyAlgorithms) {
            session.setConfig(
                "kex",
                "diffie-hellman-group-exchange-sha256,diffie-hellman-group-exchange-sha1,diffie-hellman-group14-sha256,diffie-hellman-group14-sha1,diffie-hellman-group1-sha1"
            )
            session.setConfig(
                "server_host_key",
                "ssh-rsa,ssh-dss,ecdsa-sha2-nistp256,ssh-ed25519"
            )
        }

        // 键盘交互式认证回调
        session.userInfo = object : UserInfo {
            override fun getPassword(): String? = passwordOverride
                ?: host.passwordEncrypted?.let { cryptoManager.decrypt(it) }
            override fun promptYesNo(message: String?): Boolean = true
            override fun showMessage(message: String?) {}
            override fun promptPassphrase(message: String?): String? = null
            override fun promptPassword(message: String?): String? = passwordOverride
                ?: host.passwordEncrypted?.let { cryptoManager.decrypt(it) }
        }
    }
}
