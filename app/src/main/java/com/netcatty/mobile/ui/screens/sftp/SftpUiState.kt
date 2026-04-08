package com.netcatty.mobile.ui.screens.sftp

import com.netcatty.mobile.core.ssh.SftpClient

data class SftpUiState(
    val isConnected: Boolean = false,
    val remotePath: String = "/",
    val localPath: String = "",
    val remoteFiles: List<SftpClient.SftpFileEntry> = emptyList(),
    val localFiles: List<LocalFileEntry> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val transferProgress: TransferProgress? = null
)

data class LocalFileEntry(
    val name: String,
    val type: SftpClient.FileType,
    val size: Long,
    val lastModified: Long,
    val path: String
)

data class TransferProgress(
    val fileName: String,
    val transferred: Long,
    val total: Long,
    val speed: String? = null
)
