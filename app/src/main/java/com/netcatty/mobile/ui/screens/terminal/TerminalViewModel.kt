package com.netcatty.mobile.ui.screens.terminal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.netcatty.mobile.core.ssh.SshSessionManager
import com.netcatty.mobile.core.terminal.NetcattyTerminalSession
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
class TerminalViewModel @Inject constructor(
    private val sshSessionManager: SshSessionManager,
    private val hostRepository: HostRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TerminalUiState())
    val uiState: StateFlow<TerminalUiState> = _uiState.asStateFlow()

    private val terminalSessions = mutableMapOf<String, NetcattyTerminalSession>()

    private val connectedHostIds = mutableSetOf<String>()

    /**
     * 连接到主机并创建新的终端 Tab
     */
    fun connectToHost(hostId: String) {
        // 防止重复连接
        if (hostId in connectedHostIds) {
            android.util.Log.d("TerminalVM", "Already connected to $hostId, switching tab")
            val existing = _uiState.value.sessions.find { it.hostId == hostId }
            if (existing != null) {
                _uiState.update { it.copy(activeSessionId = existing.id) }
            }
            return
        }
        connectedHostIds.add(hostId)
        android.util.Log.d("TerminalVM", "connectToHost called: hostId=$hostId")
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isConnecting = true, connectionError = null) }

            val host = hostRepository.getHostById(hostId)
            if (host == null) {
                android.util.Log.e("TerminalVM", "Host not found: $hostId")
                _uiState.update { it.copy(isConnecting = false, connectionError = "Host not found") }
                return@launch
            }
            android.util.Log.d("TerminalVM", "Host found: ${host.username}@${host.hostname}:${host.port}")

            // 解密密码
            val result = sshSessionManager.connect(host)
            result.fold(
                onSuccess = { connection ->
                    val tab = TerminalTab(
                        id = connection.id,
                        hostId = host.id,
                        hostLabel = host.label,
                        hostname = host.hostname,
                        username = host.username,
                        status = TerminalStatus.CONNECTED,
                        output = ""
                    )

                    // 先添加tab到UI（确保onOutput能找到对应session）
                    _uiState.update {
                        it.copy(
                            sessions = it.sessions + tab,
                            activeSessionId = connection.id,
                            isConnecting = false
                        )
                    }

                    // 创建终端会话并启动读取
                    val terminalSession = NetcattyTerminalSession(
                        connection = connection,
                        onOutput = { data ->
                            android.util.Log.d("TerminalVM", "onOutput: len=${data.length} preview=${data.take(50)}")
                            _uiState.update { state ->
                                val sessions = state.sessions.map {
                                    if (it.id == connection.id) {
                                        it.copy(output = it.output + data)
                                    } else it
                                }
                                state.copy(sessions = sessions)
                            }
                        },
                        onExit = {
                            _uiState.update { state ->
                                val sessions = state.sessions.map {
                                    if (it.id == connection.id)
                                        it.copy(status = TerminalStatus.DISCONNECTED)
                                    else it
                                }
                                state.copy(sessions = sessions)
                            }
                        }
                    )
                    terminalSessions[connection.id] = terminalSession
                    terminalSession.start()

                    // 更新最后连接时间
                    launch { hostRepository.updateLastConnected(hostId, System.currentTimeMillis()) }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isConnecting = false,
                            connectionError = error.message ?: "Connection failed"
                        )
                    }
                }
            )
        }
    }

    fun writeToTerminal(data: String) {
        val sessionId = _uiState.value.activeSessionId ?: return
        terminalSessions[sessionId]?.write(data)
    }

    fun sendSpecialKey(key: SpecialKey) {
        val sessionId = _uiState.value.activeSessionId ?: return
        val session = terminalSessions[sessionId] ?: return
        when (key) {
            SpecialKey.ESC -> session.write("\u001B")
            SpecialKey.TAB -> session.write("\t")
            SpecialKey.CTRL_C -> session.write("\u0003")
            SpecialKey.CTRL_D -> session.write("\u0004")
            SpecialKey.CTRL_Z -> session.write("\u001A")
            SpecialKey.CTRL_L -> session.write("\u000C")
            SpecialKey.ENTER -> session.write("\r")
            SpecialKey.ARROW_UP -> session.write("\u001B[A")
            SpecialKey.ARROW_DOWN -> session.write("\u001B[B")
            SpecialKey.ARROW_RIGHT -> session.write("\u001B[C")
            SpecialKey.ARROW_LEFT -> session.write("\u001B[D")
            SpecialKey.HOME -> session.write("\u001B[H")
            SpecialKey.END -> session.write("\u001B[F")
            SpecialKey.BACKSPACE -> session.write("\u007F")
        }
    }

    fun switchTab(sessionId: String) {
        _uiState.update { it.copy(activeSessionId = sessionId) }
    }

    fun closeTab(sessionId: String) {
        val tab = _uiState.value.sessions.find { it.id == sessionId }
        if (tab != null) connectedHostIds.remove(tab.hostId)
        terminalSessions.remove(sessionId)?.close()
        sshSessionManager.disconnect(sessionId)
        _uiState.update { state ->
            val newSessions = state.sessions.filter { it.id != sessionId }
            val newActiveId = if (state.activeSessionId == sessionId) {
                newSessions.lastOrNull()?.id
            } else {
                state.activeSessionId
            }
            state.copy(sessions = newSessions, activeSessionId = newActiveId)
        }
    }

    fun resize(cols: Int, rows: Int) {
        val sessionId = _uiState.value.activeSessionId ?: return
        terminalSessions[sessionId]?.resize(cols, rows)
    }

    fun dismissError() {
        _uiState.update { it.copy(connectionError = null) }
    }

    override fun onCleared() {
        super.onCleared()
        terminalSessions.values.forEach { it.close() }
        terminalSessions.clear()
        sshSessionManager.disconnectAll()
    }
}

enum class SpecialKey {
    ESC, TAB, CTRL_C, CTRL_D, CTRL_Z, CTRL_L, ENTER,
    ARROW_UP, ARROW_DOWN, ARROW_RIGHT, ARROW_LEFT,
    HOME, END, BACKSPACE
}
