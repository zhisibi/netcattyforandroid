package com.netcatty.mobile.ui.screens.settings

import androidx.lifecycle.ViewModel
import com.netcatty.mobile.core.crypto.SessionKeyHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class SettingsUiState(
    val hasMasterPassword: Boolean = false,
    val biometricEnabled: Boolean = false,
    val darkMode: Boolean = true,
    val terminalFontSize: Int = 13,
    val syncProvider: String? = null,
    val vaultLocked: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val sessionKeyHolder: SessionKeyHolder
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
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
        refreshState()
        return result
    }

    fun setBiometricEnabled(enabled: Boolean) {
        _uiState.update { it.copy(biometricEnabled = enabled) }
    }

    fun setDarkMode(enabled: Boolean) {
        _uiState.update { it.copy(darkMode = enabled) }
    }

    fun lockVault() {
        sessionKeyHolder.clear()
        refreshState()
    }

    private fun refreshState() {
        _uiState.update {
            it.copy(
                hasMasterPassword = sessionKeyHolder.hasMasterPassword(),
                vaultLocked = !sessionKeyHolder.isUnlocked()
            )
        }
    }
}
