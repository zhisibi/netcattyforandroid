package com.netcatty.mobile.ui.screens.portforward

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.netcatty.mobile.core.ssh.PortForwardingManager
import com.netcatty.mobile.domain.model.PortForwardingRule
import com.netcatty.mobile.domain.model.PortForwardingType

data class PortForwardUiState(
    val tunnels: List<PortForwardingManager.Tunnel> = emptyList(),
    val rules: List<PortForwardingRule> = emptyList()
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortForwardSheet(
    tunnels: List<PortForwardingManager.Tunnel>,
    onStartForward: (PortForwardingRule, String) -> Unit,
    onStopForward: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var showAddRuleDialog by remember { mutableStateOf(false) }
    var newRule by remember { mutableStateOf<PortForwardRuleForm?>(null) }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.CompareArrows, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Port Forwarding", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = { showAddRuleDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add rule")
            }
        }

        if (tunnels.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No active tunnels",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.heightIn(max = 300.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(tunnels, key = { it.id }) { tunnel ->
                    TunnelRow(
                        tunnel = tunnel,
                        onStop = { onStopForward(tunnel.id) }
                    )
                }
            }
        }
    }

    // Add rule dialog
    if (showAddRuleDialog) {
        AddPortForwardDialog(
            onDismiss = { showAddRuleDialog = false },
            onConfirm = { rule ->
                // TODO: Need sessionId from TerminalViewModel
                showAddRuleDialog = false
            }
        )
    }
}

@Composable
fun TunnelRow(
    tunnel: PortForwardingManager.Tunnel,
    onStop: () -> Unit
) {
    val typeLabel = when (tunnel.type) {
        PortForwardingType.LOCAL -> "L"
        PortForwardingType.REMOTE -> "R"
        PortForwardingType.DYNAMIC -> "D"
    }

    val statusColor = when (tunnel.status) {
        PortForwardingManager.TunnelStatus.ACTIVE -> MaterialTheme.colorScheme.primary
        PortForwardingManager.TunnelStatus.CONNECTING -> MaterialTheme.colorScheme.tertiary
        PortForwardingManager.TunnelStatus.INACTIVE -> MaterialTheme.colorScheme.onSurfaceVariant
        PortForwardingManager.TunnelStatus.ERROR -> MaterialTheme.colorScheme.error
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Type badge
            Surface(
                modifier = Modifier.size(36.dp),
                shape = MaterialTheme.shapes.small,
                color = statusColor.copy(alpha = 0.15f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        typeLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = statusColor
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = buildString {
                        append(tunnel.bindAddress)
                        append(":")
                        append(tunnel.localPort)
                        if (tunnel.remoteHost != null && tunnel.remotePort != null) {
                            append(" → ")
                            append(tunnel.remoteHost)
                            append(":")
                            append(tunnel.remotePort)
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = tunnel.status.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor
                )
            }

            IconButton(onClick = onStop) {
                Icon(
                    Icons.Default.Stop,
                    contentDescription = "Stop",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
fun AddPortForwardDialog(
    onDismiss: () -> Unit,
    onConfirm: (PortForwardRuleForm) -> Unit
) {
    var type by remember { mutableStateOf(PortForwardingType.LOCAL) }
    var localPort by remember { mutableStateOf("") }
    var bindAddress by remember { mutableStateOf("127.0.0.1") }
    var remoteHost by remember { mutableStateOf("localhost") }
    var remotePort by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Port Forward") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Type selector
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Type:", style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.width(8.dp))
                    PortForwardingType.values().forEach { t ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 8.dp)) {
                            RadioButton(
                                selected = type == t,
                                onClick = { type = t }
                            )
                            Text(
                                when (t) {
                                    PortForwardingType.LOCAL -> "Local"
                                    PortForwardingType.REMOTE -> "Remote"
                                    PortForwardingType.DYNAMIC -> "SOCKS"
                                },
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = bindAddress,
                        onValueChange = { bindAddress = it },
                        label = { Text("Bind") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = localPort,
                        onValueChange = { localPort = it },
                        label = { Text("Local Port *") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                if (type != PortForwardingType.DYNAMIC) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = remoteHost,
                            onValueChange = { remoteHost = it },
                            label = { Text("Remote Host") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = remotePort,
                            onValueChange = { remotePort = it },
                            label = { Text("Remote Port *") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val lp = localPort.toIntOrNull()
                val rp = remotePort.toIntOrNull()
                when {
                    lp == null || lp !in 1..65535 -> error = "Invalid local port (1-65535)"
                    type != PortForwardingType.DYNAMIC && (rp == null || rp !in 1..65535) -> error = "Invalid remote port (1-65535)"
                    else -> onConfirm(PortForwardRuleForm(type, bindAddress, lp, remoteHost, rp))
                }
            }) { Text("Start") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

data class PortForwardRuleForm(
    val type: PortForwardingType,
    val bindAddress: String,
    val localPort: Int,
    val remoteHost: String,
    val remotePort: Int?
)
