package com.netcatty.mobile.domain.model

import java.util.UUID

data class Snippet(
    val id: String = UUID.randomUUID().toString(),
    val label: String,
    val command: String,
    val tags: List<String> = emptyList(),
    val targetHostIds: List<String> = emptyList(),
    val shortcutKey: String? = null,
    val noAutoRun: Boolean = false
)

enum class PortForwardingType { LOCAL, REMOTE, DYNAMIC }

data class PortForwardingRule(
    val id: String = UUID.randomUUID().toString(),
    val label: String,
    val type: PortForwardingType = PortForwardingType.LOCAL,
    val localPort: Int,
    val bindAddress: String = "127.0.0.1",
    val remoteHost: String? = null,
    val remotePort: Int? = null,
    val hostId: String? = null,
    val autoStart: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

enum class ThemeType { DARK, LIGHT }

data class TerminalTheme(
    val id: String,
    val name: String,
    val type: ThemeType,
    val isCustom: Boolean = false,
    val colors: TerminalColors
)

data class TerminalColors(
    val background: String = "#1e1e2e",
    val foreground: String = "#cdd6f4",
    val cursor: String = "#f5e0dc",
    val selection: String = "#585b7066",
    val black: String = "#45475a",
    val red: String = "#f38ba8",
    val green: String = "#a6e3a1",
    val yellow: String = "#f9e2af",
    val blue: String = "#89b4fa",
    val magenta: String = "#f5c2e7",
    val cyan: String = "#94e2d5",
    val white: String = "#bac2de",
    val brightBlack: String = "#585b70",
    val brightRed: String = "#f38ba8",
    val brightGreen: String = "#a6e3a1",
    val brightYellow: String = "#f9e2af",
    val brightBlue: String = "#89b4fa",
    val brightMagenta: String = "#f5c2e7",
    val brightCyan: String = "#94e2d5",
    val brightWhite: String = "#a6adc8"
)

data class KnownHost(
    val id: String = UUID.randomUUID().toString(),
    val hostname: String,
    val port: Int = 22,
    val keyType: String,
    val publicKey: String,
    val discoveredAt: Long = System.currentTimeMillis()
)

data class HostGroup(
    val name: String,
    val path: String,
    val hosts: List<Host> = emptyList()
)
