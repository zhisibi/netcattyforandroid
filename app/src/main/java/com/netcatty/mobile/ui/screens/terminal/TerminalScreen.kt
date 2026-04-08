package com.netcatty.mobile.ui.screens.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

private val TERMINAL_BG = Color(0xFF1E1E2E)
private val TERMINAL_FG = Color(0xFFCDD6F4)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    connectHostId: String? = null,
    viewModel: TerminalViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var inputText by remember { mutableStateOf("") }

    // Auto-connect when navigating from Vault with a hostId
    LaunchedEffect(connectHostId) {
        if (connectHostId != null && uiState.sessions.none { it.hostId == connectHostId }) {
            viewModel.connectToHost(connectHostId)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {

        // Tab bar
        if (uiState.sessions.isNotEmpty()) {
            ScrollableTabRow(
                selectedTabIndex = uiState.sessions.indexOfFirst { it.id == uiState.activeSessionId }.coerceAtLeast(0),
                edgePadding = 8.dp,
                modifier = Modifier.fillMaxWidth(),
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ) {
                uiState.sessions.forEach { tab ->
                    Tab(
                        selected = tab.id == uiState.activeSessionId,
                        onClick = { viewModel.switchTab(tab.id) },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    tab.hostLabel,
                                    maxLines = 1,
                                    style = MaterialTheme.typography.labelMedium
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                IconButton(
                                    onClick = { viewModel.closeTab(tab.id) },
                                    modifier = Modifier.size(16.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(12.dp))
                                }
                            }
                        }
                    )
                }
            }
        }

        // Terminal output area
        Column(
            modifier = Modifier
                .weight(1f)
                .background(TERMINAL_BG)
        ) {
            if (uiState.isConnecting) {
                // Connecting state
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = TERMINAL_FG)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Connecting…",
                        color = TERMINAL_FG.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            } else if (uiState.sessions.isEmpty()) {
                // Empty state
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.Terminal,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = TERMINAL_FG.copy(alpha = 0.3f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "No active sessions",
                        color = TERMINAL_FG.copy(alpha = 0.5f),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Go to Vault and connect to a host",
                        color = TERMINAL_FG.copy(alpha = 0.3f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            } else {
                val activeTab = uiState.activeSession
                if (activeTab != null) {
                    // Status bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF313244))
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val statusColor = when (activeTab.status) {
                            TerminalStatus.CONNECTED -> Color(0xFFA6E3A1)
                            TerminalStatus.CONNECTING -> Color(0xFFF9E2AF)
                            TerminalStatus.DISCONNECTED -> Color(0xFFF38BA8)
                            TerminalStatus.ERROR -> Color(0xFFF38BA8)
                        }
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(statusColor))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "${activeTab.username}@${activeTab.hostLabel}",
                            color = TERMINAL_FG,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }

                    // Terminal content - use LazyColumn for proper rendering
                    val lines = activeTab.output.split("\n")
                    androidx.compose.foundation.lazy.LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(TERMINAL_BG)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        state = rememberLazyListState()
                    ) {
                        items(lines.size) { index ->
                            Text(
                                text = lines[index],
                                color = TERMINAL_FG,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                } else {
                    // No active session but sessions exist - shouldn't happen
                    Spacer(modifier = Modifier.weight(1f).background(TERMINAL_BG))
                }
            }
        }

        // Special keys row
        if (uiState.activeSession != null) {
            SpecialKeysRow(
                onSpecialKey = { viewModel.sendSpecialKey(it) }
            )
        }

        // Input bar
        if (uiState.activeSession != null) {
            InputBar(
                text = inputText,
                onTextChange = { inputText = it },
                onSend = {
                    if (inputText.isNotEmpty()) {
                        viewModel.writeToTerminal(inputText + "\r")
                        inputText = ""
                    }
                }
            )
        }
    }

    // Connection error dialog
    uiState.connectionError?.let { error ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissError() },
            title = { Text("Connection Error") },
            text = { Text(error) },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissError() }) { Text("OK") }
            }
        )
    }
}

@Composable
fun SpecialKeysRow(
    onSpecialKey: (SpecialKey) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF313244))
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        SpecialKeyBtn("ESC", onClick = { onSpecialKey(SpecialKey.ESC) })
        SpecialKeyBtn("Tab", onClick = { onSpecialKey(SpecialKey.TAB) })
        SpecialKeyBtn("⌃C", onClick = { onSpecialKey(SpecialKey.CTRL_C) })
        SpecialKeyBtn("⌃D", onClick = { onSpecialKey(SpecialKey.CTRL_D) })
        SpecialKeyBtn("⌃Z", onClick = { onSpecialKey(SpecialKey.CTRL_Z) })
        SpecialKeyBtn("⌃L", onClick = { onSpecialKey(SpecialKey.CTRL_L) })
        SpecialKeyBtn("↑", onClick = { onSpecialKey(SpecialKey.ARROW_UP) })
        SpecialKeyBtn("↓", onClick = { onSpecialKey(SpecialKey.ARROW_DOWN) })
        SpecialKeyBtn("←", onClick = { onSpecialKey(SpecialKey.ARROW_LEFT) })
        SpecialKeyBtn("→", onClick = { onSpecialKey(SpecialKey.ARROW_RIGHT) })
        SpecialKeyBtn("Home", onClick = { onSpecialKey(SpecialKey.HOME) })
        SpecialKeyBtn("End", onClick = { onSpecialKey(SpecialKey.END) })
    }
}

@Composable
fun SpecialKeyBtn(label: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.height(36.dp),
        contentPadding = PaddingValues(horizontal = 8.dp)
    ) {
        Text(
            label,
            color = TERMINAL_FG,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
fun InputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF181825))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Type command…", color = TERMINAL_FG.copy(alpha = 0.4f)) },
            textStyle = TextStyle(
                color = TERMINAL_FG,
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp
            ),
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Send
            ),
            keyboardActions = KeyboardActions(onSend = { onSend() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                cursorColor = TERMINAL_FG
            )
        )
        IconButton(onClick = onSend) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = TERMINAL_FG)
        }
    }
}
