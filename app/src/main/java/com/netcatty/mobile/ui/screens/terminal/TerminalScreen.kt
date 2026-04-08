package com.netcatty.mobile.ui.screens.terminal

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.BaseInputConnection
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.netcatty.mobile.ui.theme.TerminalConfig
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

        // Terminal area — takes all remaining space above special keys
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

        // Special keys row — always pinned at bottom, above keyboard
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
 * Full-screen transparent View that accepts IME input.
 *
 * Why full-screen + transparent?
 * - IMM refuses showSoftInput() for views with 0 or tiny size ("not served")
 * - Full-size view ensures IMM always accepts it
 * - Transparent background + Compose renders on top → user sees terminal, not the View
 * - Touch events go to Compose layer (on top), which calls showKeyboard() programmatically
 */
class TerminalInputView(context: Context) : View(context) {

    /** Set by AndroidView update block — the bridge to ViewModel.writeToTerminal */
    var onTextInput: ((String) -> Unit) = {}

    fun showKeyboard() {
        requestFocus()
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(this, InputMethodManager.SHOW_FORCED)
    }

    fun hideKeyboard() {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(windowToken, 0)
    }

    override fun onCheckIsTextEditor(): Boolean = true

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        outAttrs.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN or
                EditorInfo.IME_FLAG_NO_EXTRACT_UI or
                EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING or
                EditorInfo.IME_ACTION_NONE
        outAttrs.actionId = EditorInfo.IME_ACTION_NONE
        outAttrs.initialSelStart = -1
        outAttrs.initialSelEnd = -1

        return object : BaseInputConnection(this, true) {

            override fun commitText(text: CharSequence, newCursorPosition: Int): Boolean {
                onTextInput(text.toString())
                return true
            }

            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                if (beforeLength > 0) {
                    onTextInput("\u007F")  // DEL = backspace in SSH
                }
                if (afterLength > 0) {
                    onTextInput("\u001B[3~")  // Delete key
                }
                return true
            }

            override fun deleteSurroundingTextInCodePoints(beforeLength: Int, afterLength: Int): Boolean {
                return deleteSurroundingText(beforeLength, afterLength)
            }

            override fun sendKeyEvent(event: KeyEvent): Boolean {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    when (event.keyCode) {
                        KeyEvent.KEYCODE_ENTER -> { onTextInput("\r"); return true }
                        KeyEvent.KEYCODE_DEL -> { onTextInput("\u007F"); return true }
                        KeyEvent.KEYCODE_TAB -> { onTextInput("\t"); return true }
                        KeyEvent.KEYCODE_DPAD_UP -> { onTextInput("\u001B[A"); return true }
                        KeyEvent.KEYCODE_DPAD_DOWN -> { onTextInput("\u001B[B"); return true }
                        KeyEvent.KEYCODE_DPAD_LEFT -> { onTextInput("\u001B[D"); return true }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> { onTextInput("\u001B[C"); return true }
                        KeyEvent.KEYCODE_HOME -> { onTextInput("\u001B[H"); return true }
                        KeyEvent.KEYCODE_MOVE_END -> { onTextInput("\u001B[F"); return true }
                        KeyEvent.KEYCODE_PAGE_UP -> { onTextInput("\u001B[5~"); return true }
                        KeyEvent.KEYCODE_PAGE_DOWN -> { onTextInput("\u001B[6~"); return true }
                        KeyEvent.KEYCODE_C -> {
                            if (event.isCtrlPressed) { onTextInput("\u0003"); return true }
                        }
                        KeyEvent.KEYCODE_D -> {
                            if (event.isCtrlPressed) { onTextInput("\u0004"); return true }
                        }
                        KeyEvent.KEYCODE_Z -> {
                            if (event.isCtrlPressed) { onTextInput("\u001A"); return true }
                        }
                        KeyEvent.KEYCODE_L -> {
                            if (event.isCtrlPressed) { onTextInput("\u000C"); return true }
                        }
                    }
                }
                return super.sendKeyEvent(event)
            }

            override fun performEditorAction(actionCode: Int): Boolean {
                onTextInput("\r")
                return true
            }

            override fun performPrivateCommand(action: String?, data: android.os.Bundle?): Boolean {
                return true
            }
        }
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
    var inputViewRef by remember { mutableStateOf<TerminalInputView?>(null) }
    val terminalFontSize = TerminalConfig.fontSize()

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

    // When keyboard is showing, keep cursor visible by scrolling to bottom
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(500)
            val view = inputViewRef ?: continue
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            if (imm.isActive(view)) {
                val total = listState.layoutInfo.totalItemsCount
                if (total > 0) {
                    val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
                    if (lastVisible < total - 1) {
                        coroutineScope.launch { listState.animateScrollToItem(total - 1) }
                    }
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {

        // Layer 1: Full-size TerminalInputView (bottom of z-order, transparent)
        AndroidView(
            factory = { ctx ->
                TerminalInputView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    isFocusable = true
                    isFocusableInTouchMode = true
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                }.also { inputViewRef = it }
            },
            update = { view ->
                view.onTextInput = onSendText
            },
            modifier = Modifier.fillMaxSize()
        )

        // Layer 2: Compose terminal content on top (receives touch events)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clickable(indication = null, interactionSource = null) {
                    inputViewRef?.showKeyboard()
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
                Spacer(modifier = Modifier.weight(1f))
                // Hide keyboard button
                IconButton(
                    onClick = { inputViewRef?.hideKeyboard() },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        Icons.Default.KeyboardHide,
                        contentDescription = "Hide keyboard",
                        tint = TERMINAL_FG.copy(alpha = 0.5f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Terminal output + blinking cursor
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
        }
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
