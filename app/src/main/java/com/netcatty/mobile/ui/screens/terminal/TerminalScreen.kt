package com.netcatty.mobile.ui.screens.terminal

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
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
 * A hidden View that accepts keyboard input and forwards it to the SSH session.
 * This avoids the BasicTextField + focusRequester crash (BringIntoViewRequester).
 */
class TerminalInputView(context: android.content.Context, private val onTextInput: (String) -> Unit) : View(context) {

    private var _imeEnabled = false

    fun showKeyboard() {
        val imm = context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as InputMethodManager
        if (!_imeEnabled) {
            _imeEnabled = true
            // This triggers onCreateInputConnection
            imm.restartInput(this)
            imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
        } else {
            imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
        }
        requestFocus()
    }

    fun hideKeyboard() {
        val imm = context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(windowToken, 0)
    }

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        outAttrs.inputType = EditorInfo.TYPE_CLASS_TEXT or EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN or EditorInfo.IME_FLAG_NO_EXTRACT_UI
        outAttrs.actionId = EditorInfo.IME_ACTION_NONE
        _imeEnabled = true

        return object : android.view.inputmethod.BaseInputConnection(this, true) {
            override fun commitText(text: CharSequence, newCursorPosition: Int): Boolean {
                onTextInput(text.toString())
                return true
            }

            override fun sendKeyEvent(event: KeyEvent): Boolean {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    when (event.keyCode) {
                        KeyEvent.KEYCODE_ENTER -> {
                            onTextInput("\r")
                            return true
                        }
                        KeyEvent.KEYCODE_DEL -> {
                            onTextInput("\u007F")  // backspace
                            return true
                        }
                        KeyEvent.KEYCODE_TAB -> {
                            onTextInput("\t")
                            return true
                        }
                        KeyEvent.KEYCODE_DPAD_UP -> {
                            onTextInput("\u001B[A")
                            return true
                        }
                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            onTextInput("\u001B[B")
                            return true
                        }
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            onTextInput("\u001B[D")
                            return true
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            onTextInput("\u001B[C")
                            return true
                        }
                    }
                }
                return super.sendKeyEvent(event)
            }

            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                if (beforeLength > 0) {
                    repeat(beforeLength) { onTextInput("\u007F") }
                    return true
                }
                return super.deleteSurroundingText(beforeLength, afterLength)
            }
        }
    }

    override fun onCheckIsTextEditor(): Boolean = true
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

    Box(modifier = Modifier.fillMaxSize()) {

        // === Visible terminal output + cursor ===
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clickable {
                    // Delegate to TerminalInputView to show keyboard
                    // We find the view and show keyboard through it
                    val rootView = (context as? android.app.Activity)?.window?.decorView
                    val inputView = rootView?.findViewWithTag<TerminalInputView>("terminal_input")
                    inputView?.showKeyboard()
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

        // === Hidden AndroidView for keyboard input — NO BasicTextField, NO focusRequester ===
        AndroidView(
            factory = { ctx ->
                TerminalInputView(ctx) { text ->
                    onSendText(text)
                }.apply {
                    tag = "terminal_input"
                    layoutParams = android.widget.FrameLayout.LayoutParams(1, 1)  // 1x1px, effectively invisible
                    isFocusable = true
                    isFocusableInTouchMode = true
                }
            },
            modifier = Modifier.size(1.dp)  // 1dp x 1dp — invisible but functional
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
