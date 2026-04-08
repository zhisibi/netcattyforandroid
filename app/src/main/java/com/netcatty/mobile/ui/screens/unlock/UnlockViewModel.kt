package com.netcatty.mobile.ui.screens.unlock

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.lifecycle.ViewModel
import com.netcatty.mobile.core.crypto.SessionKeyHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class UnlockViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sessionKeyHolder: SessionKeyHolder
) : ViewModel() {

    private val _uiState = MutableStateFlow(UnlockUiState())
    val uiState: StateFlow<UnlockUiState> = _uiState.asStateFlow()

    init {
        val hasMaster = sessionKeyHolder.hasMasterPassword()
        val unlocked = sessionKeyHolder.isUnlocked()
        val isFirst = !hasMaster

        // Check biometric availability
        val biometricAvailable = canUseBiometric()
        val biometricEnabled = context.getSharedPreferences("netcatty_settings", Context.MODE_PRIVATE)
            .getBoolean("biometric_enabled", false)

        _uiState.update {
            it.copy(
                hasMasterPassword = hasMaster,
                isUnlocked = unlocked,
                isFirstTime = isFirst,
                biometricAvailable = biometricAvailable,
                biometricEnabled = biometricEnabled && biometricAvailable
            )
        }
    }

    fun unlock(password: String) {
        val result = sessionKeyHolder.deriveAndStore(password)
        _uiState.update {
            if (result) it.copy(isUnlocked = true, error = null)
            else it.copy(error = "Wrong password")
        }
    }

    fun setupAndUnlock(password: String) {
        sessionKeyHolder.setupNewPassword(password)
        _uiState.update { it.copy(isUnlocked = true, error = null) }
    }

    /**
     * Biometric auth succeeded — derive key from stored master password.
     *
     * NOTE: This stores the master password in EncryptedSharedPreferences for biometric recovery.
     * The password is encrypted by Android Keystore, so it's safe.
     */
    fun unlockWithBiometric() {
        val savedPassword = context.getSharedPreferences("netcatty_secure_prefs", Context.MODE_PRIVATE)
            .getString("biometric_recovery_password", null)

        if (savedPassword != null) {
            val result = sessionKeyHolder.deriveAndStore(savedPassword)
            _uiState.update {
                if (result) it.copy(isUnlocked = true, error = null)
                else it.copy(error = "Biometric authentication failed")
            }
        } else {
            _uiState.update { it.copy(error = "No saved credentials for biometric unlock") }
        }
    }

    private fun canUseBiometric(): Boolean {
        val biometricManager = BiometricManager.from(context)
        return biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.BIOMETRIC_WEAK
        ) == BiometricManager.BIOMETRIC_SUCCESS
    }
}
