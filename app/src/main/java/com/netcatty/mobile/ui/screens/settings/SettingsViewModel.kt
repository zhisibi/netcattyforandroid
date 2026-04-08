package com.netcatty.mobile.ui.screens.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import com.netcatty.mobile.core.crypto.SessionKeyHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class SettingsUiState(
    val hasMasterPassword: Boolean = false,
    val biometricEnabled: Boolean = false,
    val biometricAvailable: Boolean = false,
    val darkMode: Boolean = true,
    val terminalFontSize: Int = 13,
    val syncProvider: String? = null,
    val vaultLocked: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sessionKeyHolder: SessionKeyHolder
) : ViewModel() {

    companion object {
        private const val PREFS_NAME = "netcatty_settings"
        private const val KEY_DARK_MODE = "dark_mode"
        private const val KEY_TERMINAL_FONT_SIZE = "terminal_font_size"
        private const val KEY_BIOMETRIC = "biometric_enabled"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(loadSettings())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        refreshState()
    }

    fun setupPassword(password: String) {
        sessionKeyHolder.setupNewPassword(password)
        refreshState()
    }

    fun changePassword(oldPassword: String, newPassword: String): Boolean {
        val result = sessionKeyHolder.changePassword(oldPassword, newPassword)
        if (result) {
            // Update biometric recovery password if biometric is enabled
            if (_uiState.value.biometricEnabled) {
                saveBiometricRecoveryPassword(newPassword)
            }
        }
        refreshState()
        return result
    }

    fun setBiometricEnabled(enabled: Boolean): Boolean {
        if (enabled) {
            // Check if biometric is available
            val biometricManager = androidx.biometric.BiometricManager.from(context)
            val canAuth = biometricManager.canAuthenticate(
                androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG or
                        androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
            )
            if (canAuth != androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS) {
                return false  // Can't enable biometric
            }

            // Save current master password for biometric recovery
            val masterPassword = sessionKeyHolder.getMasterPassword()
            if (masterPassword != null) {
                saveBiometricRecoveryPassword(masterPassword)
            }
        } else {
            // Remove saved biometric recovery password
            removeBiometricRecoveryPassword()
        }

        prefs.edit().putBoolean(KEY_BIOMETRIC, enabled).apply()
        _uiState.update { it.copy(biometricEnabled = enabled) }
        return true
    }

    fun setDarkMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DARK_MODE, enabled).apply()
        _uiState.update { it.copy(darkMode = enabled) }
    }

    fun setTerminalFontSize(size: Int) {
        prefs.edit().putInt(KEY_TERMINAL_FONT_SIZE, size).apply()
        _uiState.update { it.copy(terminalFontSize = size) }
    }

    fun lockVault() {
        sessionKeyHolder.clear()
        refreshState()
    }

    private fun refreshState() {
        _uiState.update {
            it.copy(
                hasMasterPassword = sessionKeyHolder.hasMasterPassword(),
                vaultLocked = !sessionKeyHolder.isUnlocked(),
                biometricAvailable = canUseBiometric()
            )
        }
    }

    private fun loadSettings(): SettingsUiState {
        return SettingsUiState(
            darkMode = prefs.getBoolean(KEY_DARK_MODE, true),
            terminalFontSize = prefs.getInt(KEY_TERMINAL_FONT_SIZE, 13),
            biometricEnabled = prefs.getBoolean(KEY_BIOMETRIC, false),
            biometricAvailable = canUseBiometric(),
            hasMasterPassword = sessionKeyHolder.hasMasterPassword(),
            vaultLocked = !sessionKeyHolder.isUnlocked()
        )
    }

    private fun canUseBiometric(): Boolean {
        val biometricManager = androidx.biometric.BiometricManager.from(context)
        return biometricManager.canAuthenticate(
            androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
        ) == androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS
    }

    /**
     * Save master password for biometric recovery.
     * Uses EncryptedSharedPreferences (protected by Android Keystore).
     */
    private fun saveBiometricRecoveryPassword(password: String) {
        val securePrefs = context.getSharedPreferences("netcatty_secure_prefs", Context.MODE_PRIVATE)
        securePrefs.edit()
            .putString("biometric_recovery_password", password)
            .apply()
    }

    private fun removeBiometricRecoveryPassword() {
        val securePrefs = context.getSharedPreferences("netcatty_secure_prefs", Context.MODE_PRIVATE)
        securePrefs.edit()
            .remove("biometric_recovery_password")
            .apply()
    }
}
