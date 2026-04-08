package com.netcatty.mobile.ui.screens.terminal

import com.netcatty.mobile.domain.model.Host

data class TerminalUiState(
    val sessions: List<TerminalTab> = emptyList(),
    val activeSessionId: String? = null,
    val isConnecting: Boolean = false,
    val connectionError: String? = null,
    val showKeyboard: Boolean = false
) {
    val activeSession: TerminalTab?
        get() = sessions.find { it.id == activeSessionId }
}

data class TerminalTab(
    val id: String,
    val hostId: String,
    val hostLabel: String,
    val hostname: String,
    val username: String,
    val status: TerminalStatus = TerminalStatus.CONNECTING,
    val output: StringBuilder = StringBuilder()
)

enum class TerminalStatus { CONNECTING, CONNECTED, DISCONNECTED, ERROR }
