package com.netcatty.mobile.core.ssh

import com.jcraft.jsch.ChannelShell
import com.jcraft.jsch.Session
import java.io.InputStream
import java.io.OutputStream

/**
 * 单个 SSH 连接的完整状态。
 * 封装 JSch Session + ChannelShell + IO 流。
 *
 * 生命周期：connect() → [读写数据] → disconnect()
 * 不可重连，断开后需新建。
 */
data class SshConnection(
    val id: String,
    val hostId: String,
    val hostLabel: String,
    val username: String,
    val hostname: String,
    val port: Int,
    val session: Session,
    val channel: ChannelShell,
    val inputStream: InputStream,
    val outputStream: OutputStream,
    val createdAt: Long = System.currentTimeMillis(),
    var status: ConnectionStatus = ConnectionStatus.CONNECTED
) {
    enum class ConnectionStatus {
        CONNECTING, CONNECTED, DISCONNECTED, ERROR
    }
}
