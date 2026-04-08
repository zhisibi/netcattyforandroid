package com.netcatty.mobile.ui.screens.vault

import com.netcatty.mobile.domain.model.Host
import com.netcatty.mobile.domain.model.AuthMethod
import com.netcatty.mobile.domain.model.SshKey

data class VaultUiState(
    val hosts: List<Host> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = true,
    val showAddHostDialog: Boolean = false,
    val editingHost: Host? = null,
    val groups: List<String> = emptyList(),
    val selectedGroup: String? = null,
    val snackbarMessage: String? = null,
    val availableKeys: List<SshKey> = emptyList()
) {
    val filteredHosts: List<Host>
        get() {
            val query = searchQuery.lowercase()
            var result = hosts
            if (selectedGroup != null) {
                result = result.filter { it.group == selectedGroup }
            }
            if (query.isNotBlank()) {
                result = result.filter {
                    it.label.lowercase().contains(query) ||
                    it.hostname.lowercase().contains(query) ||
                    it.username.lowercase().contains(query)
                }
            }
            return result
        }

    val pinnedHosts: List<Host>
        get() = filteredHosts.filter { it.pinned }

    val unpinnedHosts: List<Host>
        get() = filteredHosts.filter { !it.pinned }
}

data class HostFormState(
    val label: String = "",
    val hostname: String = "",
    val port: String = "22",
    val username: String = "",
    val password: String = "",
    val authMethod: AuthMethod = AuthMethod.PASSWORD,
    val identityFileId: String? = null,  // SSH key ID for key-based auth
    val group: String = "",
    val tags: String = "",
    val isEdit: Boolean = false,
    val hostId: String? = null,
    val errors: Map<String, String> = emptyMap()
) {
    val isValid: Boolean
        get() = label.isNotBlank() && hostname.isNotBlank() && username.isNotBlank() && errors.isEmpty()
}
