package com.netcatty.mobile.ui.screens.vault

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.netcatty.mobile.domain.model.AuthMethod
import com.netcatty.mobile.domain.model.Host

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultScreen(
    onConnectSsh: (String) -> Unit = {},
    onConnectSftp: (String) -> Unit = {},
    viewModel: VaultViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val formState by viewModel.formState.collectAsState()
    var showDeleteConfirm by remember { mutableStateOf<Host?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Netcatty") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.showAddHostDialog() },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add host")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Search bar
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.onSearchQueryChanged(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search hosts…") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(24.dp)
            )

            // Group filter chips
            if (uiState.groups.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = uiState.selectedGroup == null,
                            onClick = { viewModel.onGroupSelected(null) },
                            label = { Text("All") }
                        )
                    }
                    items(uiState.groups) { group ->
                        FilterChip(
                            selected = uiState.selectedGroup == group,
                            onClick = { viewModel.onGroupSelected(group) },
                            label = { Text(group) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Host list
            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (uiState.filteredHosts.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.AutoMirrored.Filled.ViewList,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "No hosts yet",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Tap + to add your first SSH host",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (uiState.pinnedHosts.isNotEmpty()) {
                        item {
                            Text(
                                "Pinned",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                            )
                        }
                        items(uiState.pinnedHosts, key = { "pinned-${it.id}" }) { host ->
                            HostCard(
                                host = host,
                                onConnectSsh = { onConnectSsh(host.id) },
                                onConnectSftp = { onConnectSftp(host.id) },
                                onEdit = { viewModel.showEditHostDialog(host) },
                                onDelete = { showDeleteConfirm = host },
                                onPin = { viewModel.togglePin(host) }
                            )
                        }
                    }

                    if (uiState.unpinnedHosts.isNotEmpty()) {
                        if (uiState.pinnedHosts.isNotEmpty()) {
                            item {
                                Text(
                                    "All Hosts",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp, top = 8.dp)
                                )
                            }
                        }
                        items(uiState.unpinnedHosts, key = { it.id }) { host ->
                            HostCard(
                                host = host,
                                onConnectSsh = { onConnectSsh(host.id) },
                                onConnectSftp = { onConnectSftp(host.id) },
                                onEdit = { viewModel.showEditHostDialog(host) },
                                onDelete = { showDeleteConfirm = host },
                                onPin = { viewModel.togglePin(host) }
                            )
                        }
                    }
                }
            }
        }

        // Add/Edit Host Dialog
        if (uiState.showAddHostDialog) {
            HostFormDialog(
                formState = formState,
                isEdit = formState.isEdit,
                availableKeys = uiState.availableKeys,
                onLabelChange = viewModel::onFormLabelChanged,
                onHostnameChange = viewModel::onFormHostnameChanged,
                onPortChange = viewModel::onFormPortChanged,
                onUsernameChange = viewModel::onFormUsernameChanged,
                onPasswordChange = viewModel::onFormPasswordChanged,
                onAuthMethodChange = viewModel::onFormAuthMethodChanged,
                onIdentityKeyChange = viewModel::onFormIdentityKeyChanged,
                onGroupChange = viewModel::onFormGroupChanged,
                onTagsChange = viewModel::onFormTagsChanged,
                onSave = viewModel::saveHost,
                onDismiss = viewModel::hideAddHostDialog
            )
        }

        // Delete confirmation
        showDeleteConfirm?.let { host ->
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = null },
                title = { Text("Delete host?") },
                text = { Text("Remove \"${host.label}\" from your vault?") },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.deleteHost(host)
                        showDeleteConfirm = null
                    }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirm = null }) { Text("Cancel") }
                }
            )
        }
    }
}

@Composable
fun HostCard(
    host: Host,
    onConnectSsh: () -> Unit,
    onConnectSftp: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onPin: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Row 1: Icon + Info
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onConnectSsh)  // 点击整个卡片头部连SSH
            ) {
                // Icon
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("🐧", modifier = Modifier.padding(4.dp))
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Info
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = host.label,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${host.username}@${host.hostname}:${host.port}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (host.group != null) {
                        Text(
                            text = host.group,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Row 2: SSH + SFTP connect buttons (大按钮，容易点击)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // SSH button - 填充按钮，更显眼
                Button(
                    onClick = onConnectSsh,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Terminal, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("SSH", style = MaterialTheme.typography.titleSmall)
                }

                // SFTP button - 描边按钮
                OutlinedButton(
                    onClick = onConnectSftp,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("SFTP", style = MaterialTheme.typography.titleSmall)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Row 3: 小操作按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                // Pin
                IconButton(onClick = onPin) {
                    Icon(
                        Icons.Default.PushPin,
                        contentDescription = if (host.pinned) "Unpin" else "Pin",
                        tint = if (host.pinned) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                }
                // Edit
                IconButton(onClick = onEdit) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Edit",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
                // Delete
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HostFormDialog(
    formState: HostFormState,
    isEdit: Boolean,
    availableKeys: List<com.netcatty.mobile.domain.model.SshKey> = emptyList(),
    onLabelChange: (String) -> Unit,
    onHostnameChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onAuthMethodChange: (AuthMethod) -> Unit,
    onIdentityKeyChange: (String?) -> Unit,
    onGroupChange: (String) -> Unit,
    onTagsChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEdit) "Edit Host" else "Add Host") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = formState.label,
                    onValueChange = onLabelChange,
                    label = { Text("Label *") },
                    isError = formState.errors.containsKey("label"),
                    supportingText = formState.errors["label"]?.let { { Text(it) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = formState.hostname,
                    onValueChange = onHostnameChange,
                    label = { Text("Hostname *") },
                    isError = formState.errors.containsKey("hostname"),
                    supportingText = formState.errors["hostname"]?.let { { Text(it) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = formState.port,
                        onValueChange = onPortChange,
                        label = { Text("Port") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = formState.username,
                        onValueChange = onUsernameChange,
                        label = { Text("Username *") },
                        isError = formState.errors.containsKey("username"),
                        supportingText = formState.errors["username"]?.let { { Text(it) } },
                        singleLine = true,
                        modifier = Modifier.weight(2f)
                    )
                }

                // Auth method selector
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Auth:", style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.width(8.dp))
                    AuthMethod.values().forEach { method ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(end = 12.dp)
                        ) {
                            RadioButton(
                                selected = formState.authMethod == method,
                                onClick = { onAuthMethodChange(method) }
                            )
                            Text(
                                text = when (method) {
                                    AuthMethod.PASSWORD -> "Password"
                                    AuthMethod.KEY -> "Key"
                                    AuthMethod.CERTIFICATE -> "Cert"
                                },
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }

                if (formState.authMethod == AuthMethod.PASSWORD) {
                    OutlinedTextField(
                        value = formState.password,
                        onValueChange = onPasswordChange,
                        label = { Text("Password") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (formState.authMethod == AuthMethod.KEY) {
                    if (availableKeys.isEmpty()) {
                        Text(
                            "No SSH keys configured. Add keys in Settings.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        var keyExpanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = keyExpanded,
                            onExpandedChange = { keyExpanded = !keyExpanded }
                        ) {
                            OutlinedTextField(
                                value = availableKeys.find { it.id == formState.identityFileId }?.label ?: "Select key",
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = keyExpanded) },
                                modifier = Modifier.fillMaxWidth().menuAnchor(),
                                label = { Text("SSH Key") }
                            )
                            ExposedDropdownMenu(
                                expanded = keyExpanded,
                                onDismissRequest = { keyExpanded = false }
                            ) {
                                availableKeys.forEach { key ->
                                    DropdownMenuItem(
                                        text = { Text("${key.label} (${key.type})") },
                                        onClick = {
                                            onIdentityKeyChange(key.id)
                                            keyExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = formState.group,
                    onValueChange = onGroupChange,
                    label = { Text("Group") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = formState.tags,
                    onValueChange = onTagsChange,
                    label = { Text("Tags (comma separated)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onSave) {
                Text(if (isEdit) "Save" else "Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
