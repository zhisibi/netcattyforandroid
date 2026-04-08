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

    // Active terminal sessions (sessionId → NetcattyTerminalSession)
    private val terminalSessions = mutableMapOf<String, NetcattyTerminalSession>()

    /**
     * 连接到主机并创建新的终端 Tab
     */
    fun connectToHost(hostId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isConnecting = true, connectionError = null) }

            val host = hostRepository.getHostById(hostId)
            if (host == null) {
                _uiState.update { it.copy(isConnecting = false, connectionError = "Host not found") }
                return@launch
            }

            val result = sshSessionManager.connect(host)
            result.fold(
                onSuccess = { connection ->
                    val tab = TerminalTab(
                        id = connection.id,
                        hostId = host.id,
                        hostLabel = host.label,
                        hostname = host.hostname,
                        username = host.username,
                        status = TerminalStatus.CONNECTED
                    )

                    // Create terminal session
                    val terminalSession = NetcattyTerminalSession(
                        connection = connection,
                        onOutput = { data ->
                            _uiState.update { state ->
                                val sessions = state.sessions.map {
                                    if (it.id == connection.id) it.copy(
                                        output = it.output.append(data)
                                    ) else it
                                }
                                state.copy(sessions = sessions)
                            }
                        },
                        onExit = {
                            _uiState.update { state ->
                                val sessions = state.sessions.map {
                                    if (it.id == connection.id) it.copy(status = TerminalStatus.DISCONNECTED)
                                    else it
                                }
                                state.copy(sessions = sessions)
                            }
                        }
                    )
                    terminalSessions[connection.id] = terminalSession
                    terminalSession.start()

                    _uiState.update {
                        it.copy(
                            sessions = it.sessions + tab,
                            activeSessionId = connection.id,
                            isConnecting = false
                        )
                    }

                    // Update last connected
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

    /**
     * 向当前终端写入数据
     */
    fun writeToTerminal(data: String) {
        val sessionId = _uiState.value.activeSessionId ?: return
        terminalSessions[sessionId]?.write(data)
    }

    /**
     * 发送特殊键
     */
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

    /**
     * 切换到指定 Tab
     */
    fun switchTab(sessionId: String) {
        _uiState.update { it.copy(activeSessionId = sessionId) }
    }

    /**
     * 关闭指定 Tab
     */
    fun closeTab(sessionId: String) {
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

    /**
     * 调整终端尺寸
     */
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
