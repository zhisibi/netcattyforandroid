package com.netcatty.mobile.ui.screens.sftp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.netcatty.mobile.core.crypto.FieldCryptoManager
import com.netcatty.mobile.core.ssh.SftpClient
import com.netcatty.mobile.core.ssh.SshConnection
import com.netcatty.mobile.core.ssh.SshSessionManager
import com.netcatty.mobile.domain.model.Host
import com.netcatty.mobile.domain.repository.HostRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SftpViewModel @Inject constructor(
    private val sshSessionManager: SshSessionManager,
    private val hostRepository: HostRepository,
    private val fieldCryptoManager: FieldCryptoManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SftpUiState())
    val uiState: StateFlow<SftpUiState> = _uiState.asStateFlow()

    private var sftpClient: SftpClient? = null
    private var sshConnectionId: String? = null
    private var connectedHostId: String? = null

    /**
     * 连接到指定主机：先建SSH会话，再开SFTP channel
     */
    fun connectToHost(hostId: String) {
        android.util.Log.d("SftpVM", "connectToHost called: hostId=$hostId")
        if (connectedHostId == hostId && sftpClient != null) return  // 已连接

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true, error = null) }

            val host = hostRepository.getHostById(hostId)
            if (host == null) {
                _uiState.update { it.copy(isLoading = false, error = "Host not found") }
                return@launch
            }

            try {
                // 1. 先建SSH连接
                val sshResult = sshSessionManager.connect(host)
                val connection = sshResult.getOrThrow()

                sshConnectionId = connection.id
                connectedHostId = hostId

                // 2. 在SSH session上开SFTP channel
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

    override fun onCleared() {
        super.onCleared()
        sftpClient?.disconnect()
        sshConnectionId?.let { sshSessionManager.disconnect(it) }
    }
}
