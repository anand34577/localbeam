package dev.companionremote.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.companionremote.app.AppViewModel
import dev.companionremote.app.R
import dev.companionremote.app.discovery.TvPlatform
import dev.companionremote.app.i18n.LocalAppStrings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceListScreen(viewModel: AppViewModel) {
    val s = LocalAppStrings.current
    val ui by viewModel.deviceList.collectAsState()
    var showManualDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<String?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                title = {
                    Text(
                        s.appName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                actions = {
                    IconButton(onClick = { showManualDialog = true }) {
                        Icon(Icons.Rounded.Link, contentDescription = s.connectByIp)
                    }
                    if (ui.scanning) {
                        CircularProgressIndicator(Modifier.size(22.dp).padding(end = 10.dp), strokeWidth = 2.dp)
                    } else {
                        IconButton(onClick = { viewModel.startScan() }) {
                            Icon(Icons.Rounded.Refresh, contentDescription = s.rescan)
                        }
                    }
                    IconButton(onClick = { viewModel.openSettings() }) {
                        Icon(Icons.Rounded.Settings, contentDescription = s.settings)
                    }
                },
            )
        },
    ) { padding ->
        if (ui.devices.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(padding).padding(40.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.size(96.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.Tv,
                        contentDescription = null,
                        Modifier.size(44.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(24.dp))
                Text(
                    if (ui.scanning) s.lookingForAtv else s.noAtvFound,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    s.sameWifiHint,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
            ) {
                items(ui.devices, key = { it.name }) { device ->
                    Surface(
                        onClick = { viewModel.selectDevice(device) },
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier.size(48.dp).background(
                                    MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(14.dp),
                                ),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (device.platform == TvPlatform.AppleTv) {
                                    Icon(
                                        painterResource(R.drawable.ic_apple),
                                        contentDescription = null,
                                        Modifier.size(26.dp),
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    )
                                } else {
                                    Icon(
                                        Icons.Rounded.Tv,
                                        contentDescription = null,
                                        Modifier.size(26.dp),
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    )
                                }
                            }
                            Column(Modifier.padding(start = 16.dp).weight(1f)) {
                                Text(
                                    device.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium,
                                )
                                Text(
                                    listOfNotNull(
                                        device.model,
                                        if (device.name in ui.pairedNames) s.paired else s.tapToPair,
                                    ).joinToString("  ·  "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { renameTarget = device.name }) {
                                Icon(Icons.Rounded.Edit, contentDescription = s.edit)
                            }
                            TextButton(onClick = { viewModel.forgetDevice(device) }) { Text(s.forget) }
                        }
                    }
                }
            }
        }
    }

    if (showManualDialog) {
        var host by remember { mutableStateOf("") }
        var port by remember { mutableStateOf("") }
        var deviceName by remember { mutableStateOf("") }
        var platform by remember { mutableStateOf(TvPlatform.AppleTv) }
        AlertDialog(
            onDismissRequest = { showManualDialog = false },
            title = { Text(s.connectByIp) },
            text = {
                Column {
                    Text(s.connectByIpDesc, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = {
                            platform = TvPlatform.AppleTv
                            if (port == "6466") port = ""
                        }) { Text(s.appleTv) }
                        TextButton(onClick = {
                            platform = TvPlatform.AndroidTv
                            if (port.isBlank()) port = "6466"
                        }) { Text(s.androidTv) }
                    }
                    OutlinedTextField(
                        value = deviceName,
                        onValueChange = { deviceName = it },
                        label = { Text(s.deviceName) },
                        singleLine = true,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(host, { host = it }, label = { Text(s.ipAddress) }, singleLine = true)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(port, { port = it }, label = { Text(s.port) }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(
                    enabled = host.isNotBlank() && port.toIntOrNull()?.let { it in 1..65_535 } == true,
                    onClick = {
                        port.toIntOrNull()?.let { p ->
                            showManualDialog = false
                            viewModel.addManualDevice(host.trim(), p, platform, deviceName)
                        }
                    },
                ) { Text(s.connect) }
            },
            dismissButton = { TextButton(onClick = { showManualDialog = false }) { Text(s.cancel) } },
        )
    }

    renameTarget?.let { currentName ->
        DeviceRenameDialog(
            currentName = currentName,
            onDismiss = { renameTarget = null },
            onSave = { newName ->
                viewModel.renameDeviceByName(currentName, newName)
                renameTarget = null
            },
        )
    }
}

@Composable
private fun DeviceRenameDialog(currentName: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    val s = LocalAppStrings.current
    var name by remember(currentName) { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(s.renameDevice) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(s.deviceName) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(enabled = name.trim().isNotBlank(), onClick = { onSave(name) }) {
                Text(s.save)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(s.cancel) } },
    )
}
