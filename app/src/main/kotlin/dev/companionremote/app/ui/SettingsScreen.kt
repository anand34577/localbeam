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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.companionremote.app.AppViewModel
import dev.companionremote.app.DeviceVerify
import dev.companionremote.app.data.AppShortcut
import dev.companionremote.app.data.HapticStrength
import dev.companionremote.app.data.RemoteShelfMode
import dev.companionremote.app.data.ThemeMode
import dev.companionremote.app.i18n.AppLanguage
import dev.companionremote.app.i18n.LocalAppStrings
import dev.companionremote.app.theme.ThemeVariant
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: AppViewModel) {
    val s = LocalAppStrings.current
    val language by viewModel.language.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val themeVariant by viewModel.themeVariant.collectAsState()
    val dynamicColor by viewModel.dynamicColor.collectAsState()
    val remoteShelfMode by viewModel.remoteShelfMode.collectAsState()
    val hapticEnabled by viewModel.hapticEnabled.collectAsState()
    val hapticStrength by viewModel.hapticStrength.collectAsState()
    val paired by viewModel.pairedDevices.collectAsState()
    val defaultDevice by viewModel.defaultDeviceName.collectAsState()
    val activeDevice by viewModel.activeDeviceName.collectAsState()
    val deviceVerify by viewModel.deviceVerify.collectAsState()
    val androidTvShortcuts by viewModel.androidTvShortcuts.collectAsState()
    val androidTvShortcutColumns by viewModel.androidTvShortcutColumns.collectAsState()
    var renameTarget by remember { mutableStateOf<String?>(null) }
    var shortcutEditor by remember { mutableStateOf<ShortcutDraft?>(null) }
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
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f).padding(end = 12.dp)) {
                        Text("Use wallpaper colors", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        Text(
                            "Use Android 12+ Material You colors from your wallpaper.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = dynamicColor, onCheckedChange = viewModel::setDynamicColor)
                }
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

            // Android TV shortcuts
            SectionTitle("Android TV app buttons")
            SettingsCard {
                Text(
                    "Choose the order and grid width for the buttons below the remote card.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
                Text(
                    "Buttons per row",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, top = 4.dp),
                )
                OptionRow("2 columns (2×2 with four buttons)", androidTvShortcutColumns == 2) {
                    viewModel.setAndroidTvShortcutColumns(2)
                }
                OptionRow("3 columns (3×1 / 3×2 as buttons grow)", androidTvShortcutColumns == 3) {
                    viewModel.setAndroidTvShortcutColumns(3)
                }
                OptionRow("4 columns (4×1 / 4×2 as buttons grow)", androidTvShortcutColumns == 4) {
                    viewModel.setAndroidTvShortcutColumns(4)
                }
                androidTvShortcuts.forEachIndexed { index, shortcut ->
                    ShortcutSettingsRow(
                        shortcut = shortcut,
                        canMoveUp = index > 0,
                        canMoveDown = index < androidTvShortcuts.lastIndex,
                        canDelete = androidTvShortcuts.size > 1,
                        onMoveUp = {
                            viewModel.setAndroidTvShortcuts(androidTvShortcuts.move(index, index - 1))
                        },
                        onMoveDown = {
                            viewModel.setAndroidTvShortcuts(androidTvShortcuts.move(index, index + 1))
                        },
                        onEdit = {
                            shortcutEditor = ShortcutDraft(shortcut.id, shortcut.label, shortcut.target)
                        },
                        onDelete = {
                            viewModel.setAndroidTvShortcuts(androidTvShortcuts.filterIndexed { i, _ -> i != index })
                        },
                    )
                }
                OutlinedButton(
                    onClick = { shortcutEditor = ShortcutDraft(null, "", "") },
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Text("Add custom button", modifier = Modifier.padding(start = 8.dp))
                }
            }

            SectionTitle("Remote layout")
            SettingsCard {
                Text(
                    "Choose what appears below the main remote controls.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
                OptionRow("App shortcuts", remoteShelfMode == RemoteShelfMode.Applications) {
                    viewModel.setRemoteShelfMode(RemoteShelfMode.Applications)
                }
                OptionRow("Media controls", remoteShelfMode == RemoteShelfMode.MediaButtons) {
                    viewModel.setRemoteShelfMode(RemoteShelfMode.MediaButtons)
                }
                OptionRow("Apps + media controls", remoteShelfMode == RemoteShelfMode.ApplicationsAndMedia) {
                    viewModel.setRemoteShelfMode(RemoteShelfMode.ApplicationsAndMedia)
                }
                OptionRow("Hide lower section", remoteShelfMode == RemoteShelfMode.None) {
                    viewModel.setRemoteShelfMode(RemoteShelfMode.None)
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
    shortcutEditor?.let { draft ->
        ShortcutEditorDialog(
            draft = draft,
            onDismiss = { shortcutEditor = null },
            onSave = { label, target ->
                val id = draft.id ?: "custom_${UUID.randomUUID()}"
                val updated = androidTvShortcuts.toMutableList()
                val edited = AppShortcut(id, label.trim(), target.trim())
                val existingIndex = updated.indexOfFirst { it.id == draft.id }
                if (existingIndex >= 0) updated[existingIndex] = edited else updated += edited
                viewModel.setAndroidTvShortcuts(updated)
                shortcutEditor = null
            },
        )
    }
}

private data class ShortcutDraft(val id: String?, val label: String, val target: String)

private fun List<AppShortcut>.move(from: Int, to: Int): List<AppShortcut> {
    if (from !in indices || to !in indices) return this
    return toMutableList().also { items ->
        val item = items.removeAt(from)
        items.add(to, item)
    }
}

@Composable
private fun ShortcutSettingsRow(
    shortcut: AppShortcut,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    canDelete: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 4.dp)) {
            Text(shortcut.label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                shortcut.target,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(enabled = canMoveUp, onClick = onMoveUp) {
            Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Move up")
        }
        IconButton(enabled = canMoveDown, onClick = onMoveDown) {
            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Move down")
        }
        IconButton(onClick = onEdit) {
            Icon(Icons.Rounded.Edit, contentDescription = "Edit button")
        }
        IconButton(enabled = canDelete, onClick = onDelete) {
            Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete button")
        }
    }
}

@Composable
private fun ShortcutEditorDialog(
    draft: ShortcutDraft,
    onDismiss: () -> Unit,
    onSave: (label: String, target: String) -> Unit,
) {
    var label by remember(draft) { mutableStateOf(draft.label) }
    var target by remember(draft) { mutableStateOf(draft.target) }
    val valid = label.trim().isNotBlank() && isAndroidTvShortcutTarget(target)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (draft.id == null) "Add custom button" else "Edit button") },
        text = {
            Column {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Button label") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = target,
                    onValueChange = { target = it },
                    label = { Text("Package ID or deep link") },
                    supportingText = { Text("Example: com.example.app or https://example.com") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onSave(label, target) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun isAndroidTvShortcutTarget(value: String): Boolean {
    val target = value.trim()
    if (target.isBlank() || target.any(Char::isWhitespace) || target.length > 2048) return false
    val scheme = target.substringBefore(':', missingDelimiterValue = "")
    val hasScheme = scheme.isNotBlank() && scheme.first().isLetter() &&
        scheme.drop(1).all { it.isLetterOrDigit() || it == '+' || it == '-' || it == '.' }
    val packageId = target.matches(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+"))
    return hasScheme || packageId
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
