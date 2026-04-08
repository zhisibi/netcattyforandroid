package com.netcatty.mobile.ui.screens.unlock

import androidx.lifecycle.ViewModel
import com.netcatty.mobile.core.crypto.SessionKeyHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class UnlockViewModel @Inject constructor(
    private val sessionKeyHolder: SessionKeyHolder
) : ViewModel() {

    private val _uiState = MutableStateFlow(UnlockUiState())
    val uiState: StateFlow<UnlockUiState> = _uiState.asStateFlow()

    init {
        val hasMaster = sessionKeyHolder.hasMasterPassword()
        val unlocked = sessionKeyHolder.isUnlocked()
        _uiState.update {
            it.copy(
                hasMasterPassword = hasMaster,
                isUnlocked = unlocked,
                isFirstTime = !hasMaster
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
}
