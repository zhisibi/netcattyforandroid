package com.netcatty.mobile.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onVaultLocked: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showPasswordDialog by remember { mutableStateOf(false) }
    var showChangePasswordDialog by remember { mutableStateOf(false) }
    var showFontSizeDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            // ── Security ──
            item {
                SectionHeader("Security")
            }

            item {
                ListItem(
                    headlineContent = { Text("Master Password") },
                    supportingContent = {
                        Text(if (uiState.hasMasterPassword) "Enabled" else "Not set")
                    },
                    leadingContent = { Icon(Icons.Default.Lock, contentDescription = null) },
                    modifier = Modifier.clickable {
                        if (!uiState.hasMasterPassword) showPasswordDialog = true
                        else showChangePasswordDialog = true
                    }
                )
            }

            item {
                ListItem(
                    headlineContent = { Text("Biometric Unlock") },
                    supportingContent = { Text("Use fingerprint to unlock vault") },
                    leadingContent = { Icon(Icons.Default.Fingerprint, contentDescription = null) },
                    trailingContent = {
                        Switch(
                            checked = uiState.biometricEnabled,
                            onCheckedChange = { viewModel.setBiometricEnabled(it) }
                        )
                    }
                )
            }

            item {
                ListItem(
                    headlineContent = { Text("Lock Vault") },
                    supportingContent = { Text("Clear session key from memory") },
                    leadingContent = { Icon(Icons.Default.LockClock, contentDescription = null) },
                    modifier = Modifier.clickable {
                        viewModel.lockVault()
                        onVaultLocked()
                    }
                )
            }

            // ── Terminal ──
            item {
                SectionHeader("Terminal")
            }

            item {
                ListItem(
                    headlineContent = { Text("Font Size") },
                    supportingContent = { Text("${uiState.terminalFontSize}sp") },
                    leadingContent = { Icon(Icons.Default.TextFields, contentDescription = null) },
                    modifier = Modifier.clickable { showFontSizeDialog = true }
                )
            }

            // ── Appearance ──
            item {
                SectionHeader("Appearance")
            }

            item {
                ListItem(
                    headlineContent = { Text("Dark Mode") },
                    leadingContent = { Icon(Icons.Default.DarkMode, contentDescription = null) },
                    trailingContent = {
                        Switch(
                            checked = uiState.darkMode,
                            onCheckedChange = { viewModel.setDarkMode(it) }
                        )
                    }
                )
            }

            // ── Cloud Sync ──
            item {
                SectionHeader("Cloud Sync")
            }

            item {
                ListItem(
                    headlineContent = { Text("Sync Provider") },
                    supportingContent = { Text(uiState.syncProvider ?: "Not configured") },
                    leadingContent = { Icon(Icons.Default.Cloud, contentDescription = null) },
                    modifier = Modifier.clickable { /* TODO: sync setup */ }
                )
            }

            // ── About ──
            item {
                SectionHeader("About")
            }

            item {
                ListItem(
                    headlineContent = { Text("Version") },
                    supportingContent = { Text("1.0.0 (alpha)") },
                    leadingContent = { Icon(Icons.Default.Info, contentDescription = null) }
                )
            }

            item {
                ListItem(
                    headlineContent = { Text("Open Source Licenses") },
                    leadingContent = { Icon(Icons.Default.Code, contentDescription = null) },
                    modifier = Modifier.clickable { /* TODO */ }
                )
            }

            // Bottom spacing
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }

    // Dialogs
    if (showPasswordDialog) {
        PasswordSetupDialog(
            onDismiss = { showPasswordDialog = false },
            onConfirm = { password ->
                viewModel.setupPassword(password)
                showPasswordDialog = false
            }
        )
    }

    if (showChangePasswordDialog) {
        ChangePasswordDialog(
            onDismiss = { showChangePasswordDialog = false },
            onConfirm = { oldPwd, newPwd ->
                val result = viewModel.changePassword(oldPwd, newPwd)
                if (result) showChangePasswordDialog = false
            }
        )
    }

    if (showFontSizeDialog) {
        FontSizeDialog(
            currentSize = uiState.terminalFontSize,
            onDismiss = { showFontSizeDialog = false },
            onConfirm = { size ->
                viewModel.setTerminalFontSize(size)
                showFontSizeDialog = false
            }
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
fun PasswordSetupDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set Master Password") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; error = null },
                    label = { Text("Password") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it; error = null },
                    label = { Text("Confirm Password") },
                    singleLine = true,
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when {
                    password.length < 6 -> error = "Password must be at least 6 characters"
                    password != confirmPassword -> error = "Passwords don't match"
                    else -> onConfirm(password)
                }
            }) { Text("Set") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun ChangePasswordDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var oldPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change Master Password") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = oldPassword,
                    onValueChange = { oldPassword = it; error = null },
                    label = { Text("Current Password") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it; error = null },
                    label = { Text("New Password") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it; error = null },
                    label = { Text("Confirm New Password") },
                    singleLine = true,
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when {
                    newPassword.length < 6 -> error = "Password must be at least 6 characters"
                    newPassword != confirmPassword -> error = "Passwords don't match"
                    else -> onConfirm(oldPassword, newPassword)
                }
            }) { Text("Change") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun FontSizeDialog(
    currentSize: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var size by remember { mutableStateOf(currentSize) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Terminal Font Size") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$size sp", style = MaterialTheme.typography.headlineMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Slider(
                    value = size.toFloat(),
                    onValueChange = { size = it.toInt() },
                    valueRange = 8f..24f,
                    steps = 15
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("8", style = MaterialTheme.typography.labelSmall)
                    Text("24", style = MaterialTheme.typography.labelSmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(size) }) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
