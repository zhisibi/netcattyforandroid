package com.netcatty.mobile.ui.screens.terminal

import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.netcatty.mobile.ui.theme.TerminalConfig
import com.netcatty.mobile.ui.screens.snippet.SnippetPickerSheet
import kotlinx.coroutines.launch

private val TERMINAL_BG = ComposeColor(0xFF1E1E2E)
private val TERMINAL_FG = ComposeColor(0xFFCDD6F4)
private val CURSOR_COLOR = ComposeColor(0xFFF9E2AF)
private val INPUT_BAR_BG = ComposeColor(0xFF181825)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    connectHostId: String? = null,
    viewModel: TerminalViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showSnippetSheet by remember { mutableStateOf(false) }
    val snippets by viewModel.snippets.collectAsState(initial = emptyList())

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
            SpecialKeysRow(
                onSpecialKey = { viewModel.sendSpecialKey(it) },
                onShowSnippets = { showSnippetSheet = true }
            )
        }
    }

    // Snippet bottom sheet
    if (showSnippetSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSnippetSheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            SnippetPickerSheet(
                snippets = snippets,
                onExecuteSnippet = { snippet ->
                    viewModel.executeSnippet(snippet)
                    showSnippetSheet = false
                },
                onAddSnippet = { label, command ->
                    viewModel.addSnippet(label, command)
                },
                onDeleteSnippet = { snippet ->
                    viewModel.deleteSnippet(snippet)
                },
                onDismiss = { showSnippetSheet = false }
            )
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

@Composable
private fun TerminalContent(
    activeTab: TerminalTab,
    onSendText: (String) -> Unit,
    onSpecialKey: (SpecialKey) -> Unit
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val terminalFontSize = TerminalConfig.fontSize()

    // Input field state — each character is sent immediately and field is cleared
    var inputFieldValue by remember { mutableStateOf(TextFieldValue("")) }

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

    // Auto-focus input when session becomes active
    LaunchedEffect(activeTab.id) {
        kotlinx.coroutines.delay(300)
        try { focusRequester.requestFocus() } catch (_: Exception) { }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Status bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ComposeColor(0xFF313244))
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val statusColor = when (activeTab.status) {
                TerminalStatus.CONNECTED -> ComposeColor(0xFFA6E3A1)
                TerminalStatus.CONNECTING -> ComposeColor(0xFFF9E2AF)
                TerminalStatus.DISCONNECTED -> ComposeColor(0xFFF38BA8)
                TerminalStatus.ERROR -> ComposeColor(0xFFF38BA8)
            }
            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(statusColor))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                "${activeTab.username}@${activeTab.hostLabel}",
                color = TERMINAL_FG,
                style = MaterialTheme.typography.labelSmall
            )
        }

        // Terminal output
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            state = listState
        ) {
            val lines = activeTab.output.split("\n")
            items(lines.size) { index ->
                if (index == lines.lastIndex) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = lines[index],
                            color = TERMINAL_FG,
                            fontFamily = FontFamily.Monospace,
                            fontSize = terminalFontSize
                        )
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
                        fontSize = terminalFontSize,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // Input bar — visible text input at the bottom of the terminal
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(INPUT_BAR_BG)
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .height(44.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Terminal prompt indicator
            Text(
                "›",
                color = TERMINAL_FG.copy(alpha = 0.5f),
                fontFamily = FontFamily.Monospace,
                fontSize = 16.sp,
                modifier = Modifier.padding(end = 4.dp)
            )

            // Input field
            androidx.compose.foundation.text.BasicTextField(
                value = inputFieldValue,
                onValueChange = { newValue ->
                    // Only process if text was actually added (not deletion)
                    val oldText = inputFieldValue.text
                    val newText = newValue.text
                    
                    if (newText.length > oldText.length) {
                        // New characters were typed — send them to SSH
                        val added = newText.substring(oldText.length)
                        onSendText(added)
                    } else if (newText.length < oldText.length) {
                        // Characters were deleted — send backspace
                        val deletedCount = oldText.length - newText.length
                        repeat(deletedCount) { onSendText("\u007F") }
                    }
                    
                    // Clear the field after sending (keeps IME happy)
                    inputFieldValue = TextFieldValue("", TextRange(0))
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .focusRequester(focusRequester),
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = TERMINAL_FG,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp
                ),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        onSendText("\r")
                    }
                ),
                singleLine = true,
                cursorBrush = androidx.compose.ui.graphics.SolidColor(ComposeColor.Transparent)
            )

            // Keyboard toggle button
            val context = LocalContext.current
            IconButton(
                onClick = {
                    val imm = context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    val activity = context as? android.app.Activity
                    val view = activity?.currentFocus
                    if (view != null) {
                        if (imm.isActive(view)) {
                            imm.hideSoftInputFromWindow(view.windowToken, 0)
                        } else {
                            try { focusRequester.requestFocus() } catch (_: Exception) { }
                            imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
                        }
                    } else {
                        try { focusRequester.requestFocus() } catch (_: Exception) { }
                    }
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Default.Keyboard,
                    contentDescription = "Keyboard",
                    tint = TERMINAL_FG.copy(alpha = 0.5f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun SpecialKeysRow(
    onSpecialKey: (SpecialKey) -> Unit,
    onShowSnippets: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ComposeColor(0xFF313244))
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        SpecialKeyBtn("⚡", onClick = onShowSnippets)
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
        SpecialKeyBtn("⌫", onClick = { onSpecialKey(SpecialKey.BACKSPACE) })
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
