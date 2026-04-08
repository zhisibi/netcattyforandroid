package com.netcatty.mobile.ui.screens.terminal

import android.view.inputmethod.InputMethodManager
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch

private val TERMINAL_BG = Color(0xFF1E1E2E)
private val TERMINAL_FG = Color(0xFFCDD6F4)
private val CURSOR_COLOR = Color(0xFFF9E2AF)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    connectHostId: String? = null,
    viewModel: TerminalViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(connectHostId) {
        if (connectHostId != null && uiState.sessions.none { it.hostId == connectHostId }) {
            viewModel.connectToHost(connectHostId)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
    ) {
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
                                Text(tab.hostLabel, maxLines = 1, style = MaterialTheme.typography.labelMedium)
                                Spacer(modifier = Modifier.width(4.dp))
                                IconButton(onClick = { viewModel.closeTab(tab.id) }, modifier = Modifier.size(16.dp)) {
                                    Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(12.dp))
                                }
                            }
                        }
                    )
                }
            }
        }

        // Terminal area
        Box(
            modifier = Modifier
                .weight(1f)
                .background(TERMINAL_BG)
        ) {
            if (uiState.isConnecting) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = TERMINAL_FG)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Connecting…", color = TERMINAL_FG.copy(alpha = 0.7f), style = MaterialTheme.typography.bodyLarge)
                }
            } else if (uiState.sessions.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.Terminal, contentDescription = null, modifier = Modifier.size(64.dp), tint = TERMINAL_FG.copy(alpha = 0.3f))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("No active sessions", color = TERMINAL_FG.copy(alpha = 0.5f), style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Go to Vault and connect to a host", color = TERMINAL_FG.copy(alpha = 0.3f), style = MaterialTheme.typography.bodySmall)
                }
            } else {
                val activeTab = uiState.activeSession
                if (activeTab != null) {
                    TerminalContent(
                        activeTab = activeTab,
                        onSendText = { viewModel.writeToTerminal(it) },
                        onSpecialKey = { viewModel.sendSpecialKey(it) }
                    )
                }
            }
        }

        // Special keys row
        if (uiState.activeSession != null) {
            SpecialKeysRow(onSpecialKey = { viewModel.sendSpecialKey(it) })
        }
    }

    uiState.connectionError?.let { error ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissError() },
            title = { Text("Connection Error") },
            text = { Text(error) },
            confirmButton = { TextButton(onClick = { viewModel.dismissError() }) { Text("OK") } }
        )
    }
}

/**
 * Real terminal emulator content.
 *
 * - Output lines are rendered in a LazyColumn
 * - A blinking block cursor appears right after the last output character
 *   (i.e. after the `#` or `$` prompt)
 * - A hidden 0-dp BasicTextField captures all keyboard input and forwards
 *   it to the SSH session
 * - Tapping anywhere on the terminal shows the soft keyboard
 * - imePadding() on the parent pushes everything up when the keyboard appears
 */
@Composable
private fun TerminalContent(
    activeTab: TerminalTab,
    onSendText: (String) -> Unit,
    onSpecialKey: (SpecialKey) -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val focusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Hidden field for keyboard capture — always empty, we just detect diffs
    var hiddenText by remember { mutableStateOf(TextFieldValue(annotatedString = AnnotatedString(""), selection = TextRange(0))) }

    // Blinking cursor animation
    val infiniteTransition = rememberInfiniteTransition(label = "cursor")
    val cursorAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 530, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cursorAlpha"
    )

    // Auto-scroll to bottom when new output arrives
    LaunchedEffect(activeTab.output) {
        if (listState.layoutInfo.totalItemsCount > 0) {
            coroutineScope.launch {
                listState.animateScrollToItem(listState.layoutInfo.totalItemsCount - 1)
            }
        }
    }

    // Request focus when active tab changes (delay to ensure layout is complete)
    LaunchedEffect(activeTab.id) {
        kotlinx.coroutines.delay(300)
        try {
            focusRequester.requestFocus()
        } catch (_: IllegalStateException) {
            // BringIntoViewRequester not ready yet, ignore
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {

        // === Visible terminal output + cursor ===
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clickable {
                    try {
                        focusRequester.requestFocus()
                    } catch (_: IllegalStateException) {}
                    val imm = context.getSystemService(InputMethodManager::class.java)
                    imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
                }
        ) {
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

            // Terminal output + cursor
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                state = listState
            ) {
                val lines = activeTab.output.split("\n")
                items(lines.size) { index ->
                    // Last line: render text + blinking cursor right after it
                    if (index == lines.lastIndex) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = lines[index],
                                color = TERMINAL_FG,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp
                            )
                            // Blinking block cursor right after the last character
                            Box(
                                modifier = Modifier
                                    .width(8.dp)
                                    .height(16.dp)
                                    .background(CURSOR_COLOR.copy(alpha = cursorAlpha))
                            )
                        }
                    } else {
                        Text(
                            text = lines[index],
                            color = TERMINAL_FG,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        // === Hidden TextField: 0-dp, captures keyboard input ===
        BasicTextField(
            value = hiddenText,
            onValueChange = { newTfv ->
                val oldText = hiddenText.text
                val newText = newTfv.text

                if (newText.length > oldText.length) {
                    val added = newText.substring(oldText.length)
                    onSendText(added)
                } else if (newText.length < oldText.length) {
                    val deleteCount = oldText.length - newText.length
                    repeat(deleteCount) { onSpecialKey(SpecialKey.BACKSPACE) }
                }

                // Always reset to empty
                hiddenText = TextFieldValue(annotatedString = AnnotatedString(""), selection = TextRange(0))
            },
            modifier = Modifier
                .focusRequester(focusRequester)
                .size(0.dp),
            textStyle = TextStyle(fontSize = 1.sp, color = Color.Transparent),
            cursorBrush = SolidColor(Color.Transparent),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = { onSpecialKey(SpecialKey.ENTER) }
            )
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
        SpecialKeyBtn("Enter", onClick = { onSpecialKey(SpecialKey.ENTER) })
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
