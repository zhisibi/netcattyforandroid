package com.netcatty.mobile.ui.screens.sftp

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.netcatty.mobile.core.ssh.SftpClient
import com.netcatty.mobile.core.ssh.SshSessionManager
import com.netcatty.mobile.domain.repository.HostRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.OutputStream
import javax.inject.Inject

@HiltViewModel
class SftpViewModel @Inject constructor(
    private val sshSessionManager: SshSessionManager,
    private val hostRepository: HostRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SftpUiState())
    val uiState: StateFlow<SftpUiState> = _uiState.asStateFlow()

    private var sftpClient: SftpClient? = null
    private var sshConnectionId: String? = null
    private var connectedHostId: String? = null

    fun connectToHost(hostId: String) {
        if (connectedHostId == hostId && sftpClient != null) return

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true, error = null) }

            val host = hostRepository.getHostById(hostId)
            if (host == null) {
                _uiState.update { it.copy(isLoading = false, error = "Host not found") }
                return@launch
            }

            try {
                val sshResult = sshSessionManager.connect(host)
                val connection = sshResult.getOrThrow()

                sshConnectionId = connection.id
                connectedHostId = hostId

                val client = SftpClient(connection.session)
                client.connect()
                sftpClient = client

                val homeDir = client.getHomeDir()
                _uiState.update { it.copy(isConnected = true, remotePath = homeDir, isLoading = false) }
                listDirectory(homeDir)
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Connection failed") }
            }
        }
    }

    fun navigateTo(name: String) {
        val current = _uiState.value.remotePath
        val newPath = if (current == "/") "/$name" else "$current/$name"
        _uiState.update { it.copy(remotePath = newPath) }
        listDirectory(newPath)
    }

    fun navigateUp() {
        val current = _uiState.value.remotePath
        val parent = current.substringBeforeLast("/")
        val newPath = if (parent.isEmpty()) "/" else parent
        _uiState.update { it.copy(remotePath = newPath) }
        listDirectory(newPath)
    }

    fun refresh() {
        listDirectory(_uiState.value.remotePath)
    }

    fun createFolder(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val client = sftpClient ?: throw IllegalStateException("SFTP not connected")
                val path = buildPath(_uiState.value.remotePath, name)
                client.mkdir(path)
                listDirectory(_uiState.value.remotePath)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun rename(oldName: String, newName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val client = sftpClient ?: throw IllegalStateException("SFTP not connected")
                val oldPath = buildPath(_uiState.value.remotePath, oldName)
                val newPath = buildPath(_uiState.value.remotePath, newName)
                client.rename(oldPath, newPath)
                listDirectory(_uiState.value.remotePath)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun delete(entry: SftpClient.SftpFileEntry) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val client = sftpClient ?: throw IllegalStateException("SFTP not connected")
                val path = buildPath(_uiState.value.remotePath, entry.name)
                when (entry.type) {
                    SftpClient.FileType.DIRECTORY -> client.deleteDirectory(path)
                    else -> client.deleteFile(path)
                }
                listDirectory(_uiState.value.remotePath)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    /**
     * Prepare a download — store the entry so the callback knows what to download
     */
    fun prepareDownload(entry: SftpClient.SftpFileEntry) {
        _uiState.update { it.copy(pendingDownloadEntry = entry) }
    }

    /**
     * Download a remote file to a SAF Uri (chosen by user)
     */
    fun downloadToUri(entry: SftpClient.SftpFileEntry, uri: Uri, contentResolver: ContentResolver) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val client = sftpClient ?: throw IllegalStateException("SFTP not connected")
                val remotePath = buildPath(_uiState.value.remotePath, entry.name)

                _uiState.update { it.copy(transferProgress = TransferProgress(entry.name, 0, entry.size)) }

                // Download to temp file first
                val cacheDir = File("/data/data/com.netcatty.mobile/cache")
                val tmpFile = File(cacheDir, "download_tmp_${entry.name}")
                client.download(remotePath, tmpFile.absolutePath, object : com.jcraft.jsch.SftpProgressMonitor {
                    override fun init(op: Int, src: String, dest: String, max: Long) {}
                    override fun count(count: Long): Boolean {
                        _uiState.update { state ->
                            state.copy(transferProgress = state.transferProgress?.copy(transferred = count))
                        }
                        return true
                    }
                    override fun end() {}
                })

                // Copy temp file to SAF Uri
                contentResolver.openOutputStream(uri)?.use { output ->
                    tmpFile.inputStream().use { input -> input.copyTo(output) }
                }

                tmpFile.delete()
                _uiState.update { it.copy(transferProgress = null, pendingDownloadEntry = null) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message, transferProgress = null) }
            }
        }
    }

    /**
     * Upload a file from a content URI (picked via SAF)
     */
    fun uploadFromUri(uri: Uri, contentResolver: ContentResolver) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val client = sftpClient ?: throw IllegalStateException("SFTP not connected")

                val fileName = queryFileName(contentResolver, uri) ?: "upload_${System.currentTimeMillis()}"
                val remotePath = buildPath(_uiState.value.remotePath, fileName)

                _uiState.update { it.copy(transferProgress = TransferProgress(fileName, 0, 1)) }

                // Read to temp file
                val cacheDir = File("/data/data/com.netcatty.mobile/cache")
                val tmpFile = File(cacheDir, "upload_tmp_${fileName}")
                contentResolver.openInputStream(uri)?.use { input ->
                    tmpFile.outputStream().use { output -> input.copyTo(output) }
                }

                _uiState.update { it.copy(transferProgress = TransferProgress(fileName, 0, tmpFile.length())) }

                client.upload(tmpFile.absolutePath, remotePath, object : com.jcraft.jsch.SftpProgressMonitor {
                    override fun init(op: Int, src: String, dest: String, max: Long) {}
                    override fun count(count: Long): Boolean {
                        _uiState.update { state ->
                            state.copy(transferProgress = state.transferProgress?.copy(transferred = count))
                        }
                        return true
                    }
                    override fun end() {
                        _uiState.update { it.copy(transferProgress = null) }
                    }
                })

                tmpFile.delete()
                listDirectory(_uiState.value.remotePath)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message, transferProgress = null) }
            }
        }
    }

    /**
     * Preview a text file — download to cache and open with external app
     */
    fun previewFile(entry: SftpClient.SftpFileEntry, contentResolver: ContentResolver) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val client = sftpClient ?: throw IllegalStateException("SFTP not connected")
                val remotePath = buildPath(_uiState.value.remotePath, entry.name)

                // Download to cache
                val cacheDir = File("/data/data/com.netcatty.mobile/cache/sftp_preview")
                cacheDir.mkdirs()
                val localFile = File(cacheDir, entry.name)
                client.download(remotePath, localFile.absolutePath)

                // Notify UI to open the file
                _uiState.update { it.copy(previewFile = localFile) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    fun clearPreview() {
        _uiState.update { it.copy(previewFile = null) }
    }

    private fun listDirectory(path: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val client = sftpClient ?: throw IllegalStateException("SFTP not connected")
                val files = client.listDirectory(path)
                _uiState.update { it.copy(remoteFiles = files, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    private fun buildPath(parent: String, name: String): String {
        return if (parent == "/") "/$name" else "$parent/$name"
    }

    private fun queryFileName(contentResolver: ContentResolver, uri: Uri): String? {
        return contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex >= 0) cursor.getString(nameIndex) else null
        }
    }

    override fun onCleared() {
        super.onCleared()
        sftpClient?.disconnect()
        sshConnectionId?.let { sshSessionManager.disconnect(it) }
    }
}
