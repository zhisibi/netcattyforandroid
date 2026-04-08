package com.netcatty.mobile.core.ssh

import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.Session
import com.jcraft.jsch.SftpProgressMonitor
import java.util.Vector

/**
 * SFTP 客户端。
 * 复用现有 SSH Session 的 SFTP Channel。
 */
class SftpClient(private val session: Session) {
    private var channel: ChannelSftp? = null

    data class SftpFileEntry(
        val name: String,
        val type: FileType,
        val size: Long,
        val lastModified: Long,
        val permissions: String? = null,
        val linkTarget: String? = null
    )

    enum class FileType { FILE, DIRECTORY, SYMLINK }

    fun connect(): ChannelSftp {
        if (channel != null && channel?.isConnected == true) return channel!!
        val ch = session.openChannel("sftp") as ChannelSftp
        ch.connect(30000)
        channel = ch
        return ch
    }

    fun listDirectory(path: String): List<SftpFileEntry> {
        val ch = channel ?: throw IllegalStateException("SFTP not connected")
        val entries = mutableListOf<SftpFileEntry>()

        @Suppress("UNCHECKED_CAST")
        val lsEntries = ch.ls(path) as? Vector<ChannelSftp.LsEntry>
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
                    permissions = entry.attrs.permissionsString
                )
            )
        }

        return entries.sortedWith(
            compareBy({ it.type != FileType.DIRECTORY }, { it.name.lowercase() })
        )
    }

    fun download(remotePath: String, localPath: String, monitor: SftpProgressMonitor? = null) {
        val ch = channel ?: throw IllegalStateException("SFTP not connected")
        ch.get(remotePath, localPath, monitor)
    }

    fun upload(localPath: String, remotePath: String, monitor: SftpProgressMonitor? = null) {
        val ch = channel ?: throw IllegalStateException("SFTP not connected")
        ch.put(localPath, remotePath, monitor)
    }

    fun mkdir(path: String) {
        val ch = channel ?: throw IllegalStateException("SFTP not connected")
        ch.mkdir(path)
    }

    fun deleteFile(path: String) {
        val ch = channel ?: throw IllegalStateException("SFTP not connected")
        ch.rm(path)
    }

    fun deleteDirectory(path: String) {
        val ch = channel ?: throw IllegalStateException("SFTP not connected")
        ch.rmdir(path)
    }

    fun rename(oldPath: String, newPath: String) {
        val ch = channel ?: throw IllegalStateException("SFTP not connected")
        ch.rename(oldPath, newPath)
    }

    fun chmod(path: String, mode: Int) {
        val ch = channel ?: throw IllegalStateException("SFTP not connected")
        ch.chmod(mode, path)
    }

    fun getHomeDir(): String {
        val ch = channel ?: throw IllegalStateException("SFTP not connected")
        return ch.home ?: "/"
    }

    fun disconnect() {
        try { channel?.disconnect() } catch (_: Exception) { }
        channel = null
    }
}
