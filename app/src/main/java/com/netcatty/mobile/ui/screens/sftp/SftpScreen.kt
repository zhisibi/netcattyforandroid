package com.netcatty.mobile.ui.screens.sftp

import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.netcatty.mobile.core.ssh.SftpClient

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SftpScreen(
    connectHostId: String? = null,
    viewModel: SftpViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(connectHostId) {
        if (connectHostId != null) {
            viewModel.connectToHost(connectHostId)
        }
    }

    // File action dialogs
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf<SftpClient.SftpFileEntry?>(null) }
    var showDeleteConfirm by remember { mutableStateOf<SftpClient.SftpFileEntry?>(null) }

    // File picker for upload
    val uploadLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.uploadFromUri(it, context.contentResolver)
        }
    }

    // Save picker for download
    val downloadLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri: Uri? ->
        uri?.let {
            viewModel.downloadToUri(uiState.pendingDownloadEntry!!, it, context.contentResolver)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    uiState.remotePath.ifBlank { "SFTP" },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            navigationIcon = {
                if (uiState.remotePath != "/" && uiState.remotePath.isNotBlank()) {
                    IconButton(onClick = { viewModel.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            },
            actions = {
                // Upload button
                if (uiState.isConnected) {
                    IconButton(onClick = {
                        uploadLauncher.launch(arrayOf("*/*"))
                    }) {
                        Icon(Icons.Default.UploadFile, contentDescription = "Upload")
                    }
                }
                // New folder button
                IconButton(onClick = { showNewFolderDialog = true }) {
                    Icon(Icons.Default.CreateNewFolder, contentDescription = "New folder")
                }
                // Refresh
                IconButton(onClick = { viewModel.refresh() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        )

        // Transfer progress bar
        uiState.transferProgress?.let { progress ->
            LinearProgressIndicator(
                progress = { if (progress.total > 0) progress.transferred.toFloat() / progress.total else 0f },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    progress.fileName,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "${formatFileSize(progress.transferred)} / ${formatFileSize(progress.total)}",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }

        // Error banner
        uiState.error?.let { error ->
            Snackbar(
                modifier = Modifier.padding(8.dp),
                action = {
                    TextButton(onClick = { viewModel.dismissError() }) { Text("Dismiss") }
                }
            ) { Text(error, maxLines = 2) }
        }

        if (!uiState.isConnected) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("No SFTP connection", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Connect to a host from Vault first", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                }
            }
        } else if (uiState.isLoading && uiState.remoteFiles.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(uiState.remoteFiles, key = { it.name }) { entry ->
                    SftpFileRow(
                        entry = entry,
                        onClick = {
                            if (entry.type == SftpClient.FileType.DIRECTORY) {
                                viewModel.navigateTo(entry.name)
                            } else {
                                // File click → show actions (download/preview)
                                showRenameDialog = entry
                            }
                        }
                    )
                }
            }
        }
    }

    // New folder dialog
    if (showNewFolderDialog) {
        NewFolderDialog(
            onDismiss = { showNewFolderDialog = false },
            onConfirm = { name ->
                viewModel.createFolder(name)
                showNewFolderDialog = false
            }
        )
    }

    // File actions dialog (rename / download / delete / preview)
    showRenameDialog?.let { entry ->
        FileActionsDialog(
            entry = entry,
            onDismiss = { showRenameDialog = null },
            onRename = { newName ->
                viewModel.rename(entry.name, newName)
                showRenameDialog = null
            },
            onDownload = {
                viewModel.prepareDownload(entry)
                downloadLauncher.launch(entry.name)
                showRenameDialog = null
            },
            onPreview = {
                viewModel.previewFile(entry, context.contentResolver)
                showRenameDialog = null
            },
            onDelete = {
                showRenameDialog = null
                showDeleteConfirm = entry
            }
        )
    }

    // Delete confirm
    showDeleteConfirm?.let { entry ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text("Delete ${if (entry.type == SftpClient.FileType.DIRECTORY) "folder" else "file"}?") },
            text = { Text("Are you sure you want to delete \"${entry.name}\"?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.delete(entry)
                        showDeleteConfirm = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun SftpFileRow(
    entry: SftpClient.SftpFileEntry,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = when (entry.type) {
                SftpClient.FileType.DIRECTORY -> Icons.Default.Folder
                SftpClient.FileType.SYMLINK -> Icons.Default.Link
                SftpClient.FileType.FILE -> when {
                    entry.name.endsWith(".txt") || entry.name.endsWith(".md") || entry.name.endsWith(".log") ||
                    entry.name.endsWith(".conf") || entry.name.endsWith(".yaml") || entry.name.endsWith(".yml") ||
                    entry.name.endsWith(".json") || entry.name.endsWith(".xml") || entry.name.endsWith(".sh") ||
                    entry.name.endsWith(".py") || entry.name.endsWith(".js") || entry.name.endsWith(".ts") ||
                    entry.name.endsWith(".kt") || entry.name.endsWith(".java") || entry.name.endsWith(".c") ||
                    entry.name.endsWith(".cfg") || entry.name.endsWith(".ini") || entry.name.endsWith(".toml")
                        -> Icons.Default.Description
                    entry.name.endsWith(".jpg") || entry.name.endsWith(".png") || entry.name.endsWith(".gif") ||
                    entry.name.endsWith(".webp") || entry.name.endsWith(".bmp")
                        -> Icons.Default.Image
                    else -> Icons.Default.Description
                }
            },
            contentDescription = null,
            tint = when (entry.type) {
                SftpClient.FileType.DIRECTORY -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                entry.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row {
                Text(
                    formatFileSize(entry.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                entry.permissions?.let { perm ->
                    Text(
                        "  $perm",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
    HorizontalDivider(modifier = Modifier.padding(horizontal = 52.dp))
}

@Composable
fun NewFolderDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Folder") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it; error = null },
                label = { Text("Folder name") },
                singleLine = true,
                isError = error != null,
                supportingText = error?.let { { Text(it) } }
            )
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isBlank()) error = "Name cannot be empty"
                else onConfirm(name.trim())
            }) { Text("Create") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun FileActionsDialog(
    entry: SftpClient.SftpFileEntry,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
    onDownload: () -> Unit,
    onPreview: () -> Unit,
    onDelete: () -> Unit
) {
    var newName by remember { mutableStateOf(entry.name) }
    var error by remember { mutableStateOf<String?>(null) }
    val isTextFile = entry.type == SftpClient.FileType.FILE && (
        entry.name.endsWith(".txt") || entry.name.endsWith(".md") || entry.name.endsWith(".log") ||
        entry.name.endsWith(".conf") || entry.name.endsWith(".yaml") || entry.name.endsWith(".yml") ||
        entry.name.endsWith(".json") || entry.name.endsWith(".xml") || entry.name.endsWith(".sh") ||
        entry.name.endsWith(".py") || entry.name.endsWith(".js") || entry.name.endsWith(".ts") ||
        entry.name.endsWith(".kt") || entry.name.endsWith(".java") || entry.name.endsWith(".c") ||
        entry.name.endsWith(".cfg") || entry.name.endsWith(".ini") || entry.name.endsWith(".toml") ||
        entry.name.endsWith(".env") || entry.name.endsWith(".properties") || entry.name.endsWith(".csv")
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Rename field
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it; error = null },
                    label = { Text("Rename") },
                    singleLine = true,
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } }
                )

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Download button
                    OutlinedButton(
                        onClick = onDownload,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Download")
                    }
                    // Preview button (only for text files)
                    if (isTextFile) {
                        OutlinedButton(
                            onClick = onPreview,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Preview")
                        }
                    }
                }

                // Delete button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Delete")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (newName.isBlank()) error = "Name cannot be empty"
                else if (newName == entry.name) onDismiss()
                else onRename(newName.trim())
            }) { Text("Rename") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun formatFileSize(size: Long): String = when {
    size < 1024 -> "$size B"
    size < 1024 * 1024 -> "${"%,.1f".format(size / 1024.0)} KB"
    size < 1024 * 1024 * 1024 -> "${"%,.1f".format(size / (1024.0 * 1024.0))} MB"
    else -> "${"%,.1f".format(size / (1024.0 * 1024.0 * 1024.0))} GB"
}
