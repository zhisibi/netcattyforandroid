package com.netcatty.mobile.core.terminal

import com.netcatty.mobile.core.ssh.SshConnection
import java.io.IOException

/**
 * 桥接 JSch SSH Channel 和 UI。
 *
 * 数据流：
 *   JSch InputStream → strip ANSI → onOutput callback → UI
 *   UI 输入 → write() → JSch OutputStream
 */
class NetcattyTerminalSession(
    private val connection: SshConnection,
    private val onOutput: (String) -> Unit,
    private val onExit: () -> Unit
) {
    private var readThread: Thread? = null
    private var alive = true

    fun start() {
        readThread = Thread({
            val buffer = ByteArray(8192)
            try {
                while (alive && connection.status == SshConnection.ConnectionStatus.CONNECTED) {
                    val read = connection.inputStream.read(buffer)
                    if (read == -1) break
                    val raw = String(buffer, 0, read, Charsets.UTF_8)
                    // Strip ANSI codes for plain text rendering
                    val cleaned = AnsiStripper.strip(raw)
                    if (cleaned.isNotEmpty()) {
                        onOutput(cleaned)
                    }
                }
            } catch (_: IOException) {
                // Connection closed
            } finally {
                alive = false
                connection.status = SshConnection.ConnectionStatus.DISCONNECTED
                onExit()
            }
        }, "ssh-read-${connection.id}").apply {
            isDaemon = true
            start()
        }
    }

    /** 用户输入 → SSH Channel */
    fun write(data: String) {
        if (!alive) return
        try {
            connection.outputStream.apply {
                write(data.toByteArray(Charsets.UTF_8))
                flush()
            }
        } catch (_: IOException) {
            alive = false
        }
    }

    /** 调整终端尺寸 */
    fun resize(cols: Int, rows: Int) {
        try {
            connection.channel.setPtySize(cols, rows, cols * 8, rows * 16)
        } catch (_: Exception) { }
    }

    /** 关闭会话 */
    fun close() {
        alive = false
        try { connection.channel.disconnect() } catch (_: Exception) { }
        try { connection.session.disconnect() } catch (_: Exception) { }
        connection.status = SshConnection.ConnectionStatus.DISCONNECTED
    }

    fun isAlive(): Boolean = alive && connection.status == SshConnection.ConnectionStatus.CONNECTED
}
