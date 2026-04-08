package com.netcatty.mobile.core.ssh

import com.netcatty.mobile.domain.model.PortForwardingRule
import com.netcatty.mobile.domain.model.PortForwardingType
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SSH 端口转发管理器。
 * 支持 Local (-L)、Remote (-R)、Dynamic (-D/socks) 三种类型。
 */
@Singleton
class PortForwardingManager @Inject constructor(
    private val sshSessionManager: SshSessionManager
) {
    private val tunnels = ConcurrentHashMap<String, Tunnel>()

    data class Tunnel(
        val id: String,
        val ruleId: String,
        val type: PortForwardingType,
        val localPort: Int,
        val bindAddress: String,
        val remoteHost: String?,
        val remotePort: Int?,
        val hostId: String?,
        val sessionId: String,
        val status: TunnelStatus,
        val error: String? = null
    )

    enum class TunnelStatus { INACTIVE, CONNECTING, ACTIVE, ERROR }

    /**
     * 启动端口转发
     */
    fun startForward(rule: PortForwardingRule, sessionId: String): Result<Tunnel> {
        return try {
            val connection = sshSessionManager.getConnection(sessionId)
                ?: return Result.failure(Exception("SSH session not found: $sessionId"))

            val tunnelId = UUID.randomUUID().toString()

            when (rule.type) {
                PortForwardingType.LOCAL -> {
                    val port = connection.session.setPortForwardingL(
                        rule.bindAddress,
                        rule.localPort,
                        rule.remoteHost ?: "localhost",
                        rule.remotePort ?: 0
                    )
                }
                PortForwardingType.REMOTE -> {
                    connection.session.setPortForwardingR(
                        rule.bindAddress,
                        rule.localPort,
                        rule.remoteHost ?: "localhost",
                        rule.remotePort ?: 0
                    )
                }
                PortForwardingType.DYNAMIC -> {
                    // Dynamic (SOCKS) port forwarding via setPortForwardingL
                    // In JSch, dynamic forwarding is done through setPortForwardingL with a null remote
                    connection.session.setPortForwardingL(
                        rule.bindAddress,
                        rule.localPort,
                        null,
                        0
                    )
                }
            }

            val tunnel = Tunnel(
                id = tunnelId,
                ruleId = rule.id,
                type = rule.type,
                localPort = rule.localPort,
                bindAddress = rule.bindAddress,
                remoteHost = rule.remoteHost,
                remotePort = rule.remotePort,
                hostId = rule.hostId,
                sessionId = sessionId,
                status = TunnelStatus.ACTIVE
            )
            tunnels[tunnelId] = tunnel
            Result.success(tunnel)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 停止端口转发
     */
    fun stopForward(tunnelId: String) {
        tunnels.remove(tunnelId)?.let { tunnel ->
            try {
                val connection = sshSessionManager.getConnection(tunnel.sessionId)
                when (tunnel.type) {
                    PortForwardingType.LOCAL -> {
                        connection?.session?.delPortForwardingL(tunnel.localPort)
                    }
                    PortForwardingType.REMOTE -> {
                        connection?.session?.delPortForwardingR(tunnel.localPort)
                    }
                    PortForwardingType.DYNAMIC -> {
                        connection?.session?.delPortForwardingL(tunnel.localPort)
                    }
                }
            } catch (_: Exception) { }
        }
    }

    fun getActiveTunnels(): List<Tunnel> = tunnels.values.toList()

    fun getTunnel(tunnelId: String): Tunnel? = tunnels[tunnelId]

    fun stopAllForSession(sessionId: String) {
        tunnels.filter { it.value.sessionId == sessionId }.keys.forEach { stopForward(it) }
    }
}
