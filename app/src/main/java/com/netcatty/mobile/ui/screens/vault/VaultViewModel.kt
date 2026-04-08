package com.netcatty.mobile.ui.screens.vault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.netcatty.mobile.core.crypto.FieldCryptoManager
import com.netcatty.mobile.domain.model.AuthMethod
import com.netcatty.mobile.domain.model.Host
import com.netcatty.mobile.domain.repository.HostRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class VaultViewModel @Inject constructor(
    private val hostRepository: HostRepository,
    private val fieldCryptoManager: FieldCryptoManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(VaultUiState())
    val uiState: StateFlow<VaultUiState> = _uiState.asStateFlow()

    private val _formState = MutableStateFlow(HostFormState())
    val formState: StateFlow<HostFormState> = _formState.asStateFlow()

    init {
        viewModelScope.launch {
            hostRepository.getAllHosts().collect { hosts ->
                val groups = hosts.mapNotNull { it.group }.distinct().sorted()
                _uiState.update { it.copy(hosts = hosts, groups = groups, isLoading = false) }
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun onGroupSelected(group: String?) {
        _uiState.update { it.copy(selectedGroup = group) }
    }

    fun showAddHostDialog() {
        _formState.update { HostFormState() }
        _uiState.update { it.copy(showAddHostDialog = true) }
    }

    fun hideAddHostDialog() {
        _uiState.update { it.copy(showAddHostDialog = false, editingHost = null) }
    }

    fun showEditHostDialog(host: Host) {
        _formState.update {
            HostFormState(
                label = host.label,
                hostname = host.hostname,
                port = host.port.toString(),
                username = host.username,
                authMethod = host.authMethod,
                group = host.group ?: "",
                tags = host.tags.joinToString(", "),
                isEdit = true,
                hostId = host.id
            )
        }
        _uiState.update { it.copy(showAddHostDialog = true, editingHost = host) }
    }

    fun onFormLabelChanged(value: String) {
        _formState.update { it.copy(label = value, errors = it.errors - "label") }
    }

    fun onFormHostnameChanged(value: String) {
        _formState.update { it.copy(hostname = value, errors = it.errors - "hostname") }
    }

    fun onFormPortChanged(value: String) {
        _formState.update { it.copy(port = value) }
    }

    fun onFormUsernameChanged(value: String) {
        _formState.update { it.copy(username = value, errors = it.errors - "username") }
    }

    fun onFormPasswordChanged(value: String) {
        _formState.update { it.copy(password = value) }
    }

    fun onFormAuthMethodChanged(value: AuthMethod) {
        _formState.update { it.copy(authMethod = value) }
    }

    fun onFormGroupChanged(value: String) {
        _formState.update { it.copy(group = value) }
    }

    fun onFormTagsChanged(value: String) {
        _formState.update { it.copy(tags = value) }
    }

    fun saveHost() {
        val form = _formState.value
        val errors = mutableMapOf<String, String>()
        if (form.label.isBlank()) errors["label"] = "Label is required"
        if (form.hostname.isBlank()) errors["hostname"] = "Hostname is required"
        if (form.username.isBlank()) errors["username"] = "Username is required"

        if (errors.isNotEmpty()) {
            _formState.update { it.copy(errors = errors) }
            return
        }

        viewModelScope.launch {
            val port = form.port.toIntOrNull() ?: 22
            val tags = form.tags.split(",").map { it.trim() }.filter { it.isNotBlank() }
            val passwordEncrypted = if (form.authMethod == AuthMethod.PASSWORD && form.password.isNotBlank()) {
                if (fieldCryptoManager.isKeyAvailable()) fieldCryptoManager.encrypt(form.password) else form.password
            } else null

            val existingHost = _uiState.value.editingHost
            val host = Host(
                id = form.hostId ?: existingHost?.id ?: java.util.UUID.randomUUID().toString(),
                label = form.label,
                hostname = form.hostname,
                port = port,
                username = form.username,
                authMethod = form.authMethod,
                passwordEncrypted = passwordEncrypted,
                group = form.group.ifBlank { null },
                tags = tags,
                createdAt = existingHost?.createdAt ?: System.currentTimeMillis()
            )

            hostRepository.insertHost(host)
            hideAddHostDialog()
        }
    }

    fun deleteHost(host: Host) {
        viewModelScope.launch {
            hostRepository.deleteHost(host)
        }
    }

    fun togglePin(host: Host) {
        viewModelScope.launch {
            hostRepository.updateHost(host.copy(pinned = !host.pinned))
        }
    }

    fun dismissSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }
}
