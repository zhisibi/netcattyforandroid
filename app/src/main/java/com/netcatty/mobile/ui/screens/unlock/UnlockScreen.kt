package com.netcatty.mobile.ui.screens.unlock

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

data class UnlockUiState(
    val hasMasterPassword: Boolean = false,
    val isUnlocked: Boolean = false,
    val error: String? = null,
    val isFirstTime: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnlockScreen(
    onUnlocked: () -> Unit,
    viewModel: UnlockViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(uiState.isUnlocked) {
        if (uiState.isUnlocked) onUnlocked()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.Lock,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                if (uiState.isFirstTime) "Set Master Password" else "Unlock Vault",
                style = MaterialTheme.typography.headlineMedium
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                if (uiState.isFirstTime) "Encrypt your SSH credentials with a master password"
                else "Enter your master password to continue",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(32.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it; localError = null },
                label = { Text("Password") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                isError = localError != null || uiState.error != null,
                supportingText = {
                    val msg = localError ?: uiState.error
                    if (msg != null) Text(msg, color = MaterialTheme.colorScheme.error)
                }
            )

            if (uiState.isFirstTime) {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it; localError = null },
                    label = { Text("Confirm Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    isError = localError != null
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    if (uiState.isFirstTime) {
                        when {
                            password.length < 6 -> localError = "At least 6 characters"
                            password != confirmPassword -> localError = "Passwords don't match"
                            else -> viewModel.setupAndUnlock(password)
                        }
                    } else {
                        if (password.isBlank()) {
                            localError = "Enter your password"
                        } else {
                            viewModel.unlock(password)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().testTag("unlockButton"),
                enabled = password.isNotBlank()
            ) {
                Text(if (uiState.isFirstTime) "Create & Unlock" else "Unlock")
            }

            if (!uiState.isFirstTime) {
                Spacer(modifier = Modifier.height(12.dp))
                TextButton(onClick = { /* TODO: biometric */ }) {
                    Text("Use Biometric")
                }
            }
        }
    }
}
