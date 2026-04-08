package com.netcatty.mobile.ui.screens.unlock

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel

data class UnlockUiState(
    val hasMasterPassword: Boolean = false,
    val isUnlocked: Boolean = false,
    val error: String? = null,
    val isFirstTime: Boolean = false,
    val biometricAvailable: Boolean = false,
    val biometricEnabled: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnlockScreen(
    onUnlocked: () -> Unit,
    viewModel: UnlockViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(uiState.isUnlocked) {
        if (uiState.isUnlocked) onUnlocked()
    }

    // Auto-trigger biometric if available and not first time
    LaunchedEffect(uiState.biometricAvailable, uiState.biometricEnabled, uiState.isFirstTime) {
        if (uiState.biometricAvailable && uiState.biometricEnabled && !uiState.isFirstTime && !uiState.isUnlocked) {
            showBiometricPrompt(context) { success ->
                if (success) viewModel.unlockWithBiometric()
            }
        }
    }

    // Auto-focus password field
    LaunchedEffect(Unit) {
        if (!uiState.isFirstTime && !uiState.biometricEnabled) {
            focusRequester.requestFocus()
        }
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
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                isError = localError != null || uiState.error != null,
                supportingText = {
                    val msg = localError ?: uiState.error
                    if (msg != null) Text(msg, color = MaterialTheme.colorScheme.error)
                },
                keyboardOptions = KeyboardOptions(
                    imeAction = if (uiState.isFirstTime) ImeAction.Next else ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (!uiState.isFirstTime && password.isNotBlank()) {
                            viewModel.unlock(password)
                        }
                    }
                )
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
                    isError = localError != null,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            when {
                                password.length < 6 -> localError = "At least 6 characters"
                                password != confirmPassword -> localError = "Passwords don't match"
                                else -> viewModel.setupAndUnlock(password)
                            }
                        }
                    )
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

            // Biometric unlock button
            if (!uiState.isFirstTime && uiState.biometricAvailable) {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = {
                        showBiometricPrompt(context) { success ->
                            if (success) viewModel.unlockWithBiometric()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Fingerprint, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Use Biometric")
                }
            }
        }
    }
}

/**
 * Show Android BiometricPrompt and return success/failure via callback
 */
private fun showBiometricPrompt(context: Context, onResult: (Boolean) -> Unit) {
    val activity = context as? FragmentActivity ?: return

    val executor = ContextCompat.getMainExecutor(activity)
    val biometricPrompt = BiometricPrompt(
        activity,
        executor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onResult(true)
            }

            override fun onAuthenticationFailed() {
                onResult(false)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                onResult(false)
            }
        }
    )

    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle("Unlock Netcatty")
        .setSubtitle("Use biometric to access your vault")
        .setNegativeButtonText("Use password")
        .setAllowedAuthenticators(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.BIOMETRIC_WEAK
        )
        .build()

    biometricPrompt.authenticate(promptInfo)
}
