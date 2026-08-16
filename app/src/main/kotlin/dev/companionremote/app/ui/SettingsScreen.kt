package dev.companionremote.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.companionremote.app.AppViewModel
import dev.companionremote.app.DeviceVerify
import dev.companionremote.app.data.HapticStrength
import dev.companionremote.app.data.ThemeMode
import dev.companionremote.app.i18n.AppLanguage
import dev.companionremote.app.i18n.LocalAppStrings
import dev.companionremote.app.theme.ThemeVariant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: AppViewModel) {
    val s = LocalAppStrings.current
    val language by viewModel.language.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val themeVariant by viewModel.themeVariant.collectAsState()
    val hapticEnabled by viewModel.hapticEnabled.collectAsState()
    val hapticStrength by viewModel.hapticStrength.collectAsState()
    val paired by viewModel.pairedDevices.collectAsState()
    val defaultDevice by viewModel.defaultDeviceName.collectAsState()
    val activeDevice by viewModel.activeDeviceName.collectAsState()
    val deviceVerify by viewModel.deviceVerify.collectAsState()
    var renameTarget by remember { mutableStateOf<String?>(null) }
    val previewHaptic = rememberHapticPreview()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                title = { Text(s.settings, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.closeSettings() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            // Paired Apple TVs (top of the list)
            SectionTitle(s.pairedDevices)
            SettingsCard {
                if (paired.isEmpty()) {
                    Text(
                        s.noPairedDevices,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                } else {
                    paired.forEach { name ->
                        PairedDeviceRow(
                            name = name,
                            isDefault = name == defaultDevice || (paired.size == 1 && defaultDevice == null),
                            inUse = name == activeDevice,
                            verify = deviceVerify[name] ?: DeviceVerify.Idle,
                            onRefresh = { viewModel.verifyDevice(name) },
                            onSetDefault = { viewModel.setDefaultDevice(name) },
                            onRename = { renameTarget = name },
                            onForget = { viewModel.forgetDeviceByName(name) },
                        )
                    }
                }
            }

            // Appearance
            SectionTitle(s.theme)
            SettingsCard {
                OptionRow(s.themeSystem, themeMode == ThemeMode.System) { viewModel.setThemeMode(ThemeMode.System) }
                OptionRow(s.themeLight, themeMode == ThemeMode.Light) { viewModel.setThemeMode(ThemeMode.Light) }
                OptionRow(s.themeDark, themeMode == ThemeMode.Dark) { viewModel.setThemeMode(ThemeMode.Dark) }
            }
            SectionTitle(s.colorTheme)
            SettingsCard {
                OptionRow(s.themeLocalBeam, themeVariant == ThemeVariant.LocalBeam) {
                    viewModel.setThemeVariant(ThemeVariant.LocalBeam)
                }
                OptionRow(s.themeGraphite, themeVariant == ThemeVariant.Graphite) {
                    viewModel.setThemeVariant(ThemeVariant.Graphite)
                }
                OptionRow(s.themeEmber, themeVariant == ThemeVariant.Ember) {
                    viewModel.setThemeVariant(ThemeVariant.Ember)
                }
                OptionRow(s.themeMidnight, themeVariant == ThemeVariant.Midnight) {
                    viewModel.setThemeVariant(ThemeVariant.Midnight)
                }
                OptionRow(s.themeOcean, themeVariant == ThemeVariant.Ocean) {
                    viewModel.setThemeVariant(ThemeVariant.Ocean)
                }
                OptionRow(s.themeForest, themeVariant == ThemeVariant.Forest) {
                    viewModel.setThemeVariant(ThemeVariant.Forest)
                }
                OptionRow(s.themeRose, themeVariant == ThemeVariant.Rose) {
                    viewModel.setThemeVariant(ThemeVariant.Rose)
                }
            }

            // Button feedback (haptics)
            SectionTitle(s.haptics)
            SettingsCard {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(s.hapticVibrate, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        Text(
                            s.hapticVibrateDesc,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = hapticEnabled, onCheckedChange = { viewModel.setHapticEnabled(it) })
                }
                if (hapticEnabled) {
                    Text(
                        s.hapticStrength,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, top = 4.dp),
                    )
                    // Selecting a level buzzes at that strength so it can be felt.
                    OptionRow(s.hapticLight, hapticStrength == HapticStrength.Light) {
                        viewModel.setHapticStrength(HapticStrength.Light); previewHaptic(HapticStrength.Light)
                    }
                    OptionRow(s.hapticMedium, hapticStrength == HapticStrength.Medium) {
                        viewModel.setHapticStrength(HapticStrength.Medium); previewHaptic(HapticStrength.Medium)
                    }
                    OptionRow(s.hapticStrong, hapticStrength == HapticStrength.Strong) {
                        viewModel.setHapticStrength(HapticStrength.Strong); previewHaptic(HapticStrength.Strong)
                    }
                }
            }

            // Language
            SectionTitle(s.language)
            SettingsCard {
                OptionRow(s.languageSystem, language == AppLanguage.System) { viewModel.setLanguage(AppLanguage.System) }
                OptionRow(s.languageEnglish, language == AppLanguage.English) { viewModel.setLanguage(AppLanguage.English) }
                OptionRow(s.languageChinese, language == AppLanguage.Chinese) { viewModel.setLanguage(AppLanguage.Chinese) }
                OptionRow(s.languageHindi, language == AppLanguage.Hindi) { viewModel.setLanguage(AppLanguage.Hindi) }
            }

            // About
            SectionTitle(s.about)
            Text(
                s.author,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            )
            Text(
                s.aboutText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            )
            Text(
                s.repository,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
    renameTarget?.let { currentName ->
        RenameDialog(
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
private fun SectionTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 4.dp, top = 20.dp, bottom = 8.dp),
    )
}

@Composable
private fun SettingsCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(vertical = 4.dp), content = content)
    }
}

@Composable
private fun OptionRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected = selected, onClick = onSelect)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun PairedDeviceRow(
    name: String,
    isDefault: Boolean,
    inUse: Boolean,
    verify: DeviceVerify,
    onRefresh: () -> Unit,
    onSetDefault: () -> Unit,
    onRename: () -> Unit,
    onForget: () -> Unit,
) {
    val s = LocalAppStrings.current
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Rounded.Tv,
            contentDescription = null,
            Modifier.size(22.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(Modifier.padding(start = 16.dp).weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, style = MaterialTheme.typography.bodyLarge)
                if (verify == DeviceVerify.Ok) {
                    Spacer(Modifier.size(8.dp))
                    Box(Modifier.size(9.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                }
            }
            when {
                inUse -> Text(
                    s.inUse,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                )
                isDefault -> Text(
                    s.defaultRemote,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                verify == DeviceVerify.Ok -> Text(
                    s.paired,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                verify == DeviceVerify.Failed -> Text(
                    s.atvUnreachable,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        RadioButton(selected = isDefault, onClick = onSetDefault)
        if (verify == DeviceVerify.Checking) {
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            }
        } else {
            IconButton(onClick = onRefresh) {
                Icon(
                    Icons.Rounded.Refresh,
                    contentDescription = s.checkNow,
                    tint = if (verify == DeviceVerify.Ok) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = onRename) {
            Icon(Icons.Rounded.Edit, contentDescription = s.edit, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onForget) {
            Icon(Icons.Rounded.DeleteOutline, contentDescription = s.forget, tint = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun ToggleRow(title: String, desc: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun RenameDialog(currentName: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
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
            TextButton(
                enabled = name.trim().isNotBlank(),
                onClick = { onSave(name) },
            ) { Text(s.save) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(s.cancel) } },
    )
}
