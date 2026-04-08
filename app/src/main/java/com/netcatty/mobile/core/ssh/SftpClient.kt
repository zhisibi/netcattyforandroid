package com.netcatty.mobile.core.ssh

import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.Session
import com.jcraft.jsch.SftpProgressMonitor
import java.util.Vector

/**
 * SFTP 客户端。
 * 复用现有 SSH Session 的 SFTP Channel。
 *
 * 对应桌面端 sftpBridge.cjs 的核心功能。
 */
class SftpClient(private val session: Session) {
    private var channel: ChannelSftp? = null

    data class SftpFileEntry(
        val name: String,
        val type: FileType,
        val size: Long,
        val lastModified: Long,
        val permissions: String? = null,
        val owner: String? = null,
        val group: String? = null,
        val linkTarget: String? = null,
        val hidden: Boolean = false
    )

    enum class FileType { FILE, DIRECTORY, SYMLINK }

    /**
     * 连接 SFTP channel
     */
    fun connect(): ChannelSftp {
        if (channel != null && channel?.isConnected == true) return channel!!
        val ch = session.openChannel("sftp") as ChannelSftp
        ch.connect(30000)
        channel = ch
        return ch
    }

    /**
     * 列出目录内容
     */
    fun listDirectory(path: String): List<SftpFileEntry> {
        val ch = channel ?: throw IllegalStateException("SFTP not connected")
        val entries = mutableListOf<SftpFileEntry>()

        @Suppress("UNCHECKED_CAST")
        val lsEntries = ch.ls(path) as? Vector<com.jcraft.jsch.ChannelSftp.LsEntry>
            ?: return emptyList()

        for (entry in lsEntries) {
            if (entry.filename in listOf(".", "..")) continue
            entries.add(
                SftpFileEntry(
                    name = entry.filename,
                    type = when {
                        entry.attrs.isDir -> FileType.DIRECTORY
                        entry.attrs.isLink -> FileType.SYMLINK
                        else -> FileType.FILE
                    },
                    size = entry.attrs.size,
                    lastModified = entry.attrs.mTime * 1000L,
                    permissions = entry.attrs.permissionsString,
                    owner = entry.attrs.user,
                    group = entry.attrs.group
                )
            )
        }

        return entries.sortedWith(
            compareBy({ it.type != FileType.DIRECTORY }, { it.name.lowercase() })
        )
    }

    /**
     * 下载文件
     */
    fun download(remotePath: String, localPath: String, monitor: SftpProgressMonitor? = null) {
        val ch = channel ?: throw IllegalStateException("SFTP not connected")
        ch.get(remotePath, localPath, monitor)
    }

    /**
     * 上传文件
     */
    fun upload(localPath: String, remotePath: String, monitor: SftpProgressMonitor? = null) {
        val ch = channel ?: throw IllegalStateException("SFTP not connected")
        ch.put(localPath, remotePath, monitor)
    }

    /**
     * 创建目录
     */
    fun mkdir(path: String) {
        val ch = channel ?: throw IllegalStateException("SFTP not connected")
        ch.mkdir(path)
    }

    /**
     * 删除文件
     */
    fun deleteFile(path: String) {
        val ch = channel ?: throw IllegalStateException("SFTP not connected")
        ch.rm(path)
    }

    /**
     * 删除目录
     */
    fun deleteDirectory(path: String) {
        val ch = channel ?: throw IllegalStateException("SFTP not connected")
        ch.rmdir(path)
    }

    /**
     * 重命名
     */
    fun rename(oldPath: String, newPath: String) {
        val ch = channel ?: throw IllegalStateException("SFTP not connected")
        ch.rename(oldPath, newPath)
    }

    /**
     * 修改权限
     */
    fun chmod(path: String, mode: Int) {
        val ch = channel ?: throw IllegalStateException("SFTP not connected")
        ch.chmod(mode, path)
    }

    /**
     * 获取 home 目录
     */
    fun getHomeDir(): String {
        val ch = channel ?: throw IllegalStateException("SFTP not connected")
        return ch.home ?: "/"
    }

    /**
     * 断开 SFTP 连接
     */
    fun disconnect() {
        try { channel?.disconnect() } catch (_: Exception) { }
        channel = null
    }
}


