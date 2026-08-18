package dev.companionremote.app.data

import android.content.Context
import android.util.Base64
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.companionremote.app.i18n.AppLanguage
import dev.companionremote.app.theme.ThemeVariant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "cyberremote_settings")

/** Light/dark theme preference. */
enum class ThemeMode { System, Light, Dark }

/** Haptic (vibration) strength for button feedback. */
enum class HapticStrength { Light, Medium, Strong }

/** Persists app-level preferences (language, theme, haptics, …). */
class SettingsRepository(context: Context) {

    private val appContext = context.applicationContext
    private val languageKey = stringPreferencesKey("language")
    private val themeKey = stringPreferencesKey("theme")
    private val themeVariantKey = stringPreferencesKey("theme_variant")
    private val hapticEnabledKey = booleanPreferencesKey("haptic_enabled")
    private val hapticStrengthKey = stringPreferencesKey("haptic_strength")
    private val introSeenKey = booleanPreferencesKey("intro_seen")
    private val defaultDeviceKey = stringPreferencesKey("default_device")
    private val androidTvShortcutsKey = stringPreferencesKey("android_tv_shortcuts")
    private val androidTvShortcutColumnsKey = stringPreferencesKey("android_tv_shortcut_columns")
    private val dynamicColorKey = booleanPreferencesKey("dynamic_color")
    private val remoteShelfModeKey = stringPreferencesKey("remote_shelf_mode")

    val language: Flow<AppLanguage> = appContext.settingsDataStore.data.map { prefs ->
        when (prefs[languageKey]) {
            "en" -> AppLanguage.English
            "zh" -> AppLanguage.Chinese
            "hi" -> AppLanguage.Hindi
            else -> AppLanguage.System
        }
    }

    val themeMode: Flow<ThemeMode> = appContext.settingsDataStore.data.map { prefs ->
        when (prefs[themeKey]) {
            "light" -> ThemeMode.Light
            "dark" -> ThemeMode.Dark
            else -> ThemeMode.System
        }
    }

    val themeVariant: Flow<ThemeVariant> = appContext.settingsDataStore.data.map { prefs ->
        when (prefs[themeVariantKey]) {
            "graphite" -> ThemeVariant.Graphite
            "ember" -> ThemeVariant.Ember
            "midnight" -> ThemeVariant.Midnight
            "ocean" -> ThemeVariant.Ocean
            "forest" -> ThemeVariant.Forest
            "rose" -> ThemeVariant.Rose
            else -> ThemeVariant.LocalBeam
        }
    }

    /** Use the Android 12+ wallpaper-derived Material You color scheme. */
    val dynamicColor: Flow<Boolean> = appContext.settingsDataStore.data.map { prefs ->
        prefs[dynamicColorKey] ?: false
    }

    /** Which optional control shelf is visible below the remote. */
    val remoteShelfMode: Flow<RemoteShelfMode> = appContext.settingsDataStore.data.map { prefs ->
        when (prefs[remoteShelfModeKey]) {
            "media" -> RemoteShelfMode.MediaButtons
            "both" -> RemoteShelfMode.ApplicationsAndMedia
            "none" -> RemoteShelfMode.None
            else -> RemoteShelfMode.ApplicationsAndMedia
        }
    }

    /** Vibrate on button presses (default on). */
    val hapticEnabled: Flow<Boolean> = appContext.settingsDataStore.data.map { prefs ->
        prefs[hapticEnabledKey] ?: true
    }

    val hapticStrength: Flow<HapticStrength> = appContext.settingsDataStore.data.map { prefs ->
        when (prefs[hapticStrengthKey]) {
            "light" -> HapticStrength.Light
            "strong" -> HapticStrength.Strong
            else -> HapticStrength.Medium
        }
    }

    /** Whether the first-run remote tutorial has been shown. */
    val introSeen: Flow<Boolean> = appContext.settingsDataStore.data.map { prefs ->
        prefs[introSeenKey] ?: false
    }

    /** The paired TV opened automatically when the app starts. */
    val defaultDeviceName: Flow<String?> = appContext.settingsDataStore.data.map { prefs ->
        prefs[defaultDeviceKey]?.takeIf { it.isNotBlank() }
    }

    /** The ordered Android TV shortcut buttons shown below the remote card. */
    val androidTvShortcuts: Flow<List<AppShortcut>> = appContext.settingsDataStore.data.map { prefs ->
        decodeShortcuts(prefs[androidTvShortcutsKey])
    }

    /** Number of shortcut buttons per row; supported layouts are 2 or 3. */
    val androidTvShortcutColumns: Flow<Int> = appContext.settingsDataStore.data.map { prefs ->
        prefs[androidTvShortcutColumnsKey]?.toIntOrNull()?.coerceIn(2, 4) ?: 2
    }

    suspend fun setLanguage(language: AppLanguage) {
        appContext.settingsDataStore.edit { prefs ->
            prefs[languageKey] = when (language) {
                AppLanguage.English -> "en"
                AppLanguage.Chinese -> "zh"
                AppLanguage.Hindi -> "hi"
                AppLanguage.System -> "system"
            }
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        appContext.settingsDataStore.edit { prefs ->
            prefs[themeKey] = when (mode) {
                ThemeMode.Light -> "light"
                ThemeMode.Dark -> "dark"
                ThemeMode.System -> "system"
            }
        }
    }

    suspend fun setThemeVariant(variant: ThemeVariant) {
        appContext.settingsDataStore.edit { prefs ->
            prefs[themeVariantKey] = when (variant) {
                ThemeVariant.LocalBeam -> "local_beam"
                ThemeVariant.Graphite -> "graphite"
                ThemeVariant.Ember -> "ember"
                ThemeVariant.Midnight -> "midnight"
                ThemeVariant.Ocean -> "ocean"
                ThemeVariant.Forest -> "forest"
                ThemeVariant.Rose -> "rose"
            }
        }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        appContext.settingsDataStore.edit { prefs -> prefs[dynamicColorKey] = enabled }
    }

    suspend fun setRemoteShelfMode(mode: RemoteShelfMode) {
        appContext.settingsDataStore.edit { prefs ->
            prefs[remoteShelfModeKey] = when (mode) {
                RemoteShelfMode.Applications -> "applications"
                RemoteShelfMode.MediaButtons -> "media"
                RemoteShelfMode.ApplicationsAndMedia -> "both"
                RemoteShelfMode.None -> "none"
            }
        }
    }

    suspend fun setHapticEnabled(enabled: Boolean) {
        appContext.settingsDataStore.edit { prefs -> prefs[hapticEnabledKey] = enabled }
    }

    suspend fun setHapticStrength(strength: HapticStrength) {
        appContext.settingsDataStore.edit { prefs ->
            prefs[hapticStrengthKey] = when (strength) {
                HapticStrength.Light -> "light"
                HapticStrength.Medium -> "medium"
                HapticStrength.Strong -> "strong"
            }
        }
    }

    suspend fun setIntroSeen(seen: Boolean) {
        appContext.settingsDataStore.edit { prefs -> prefs[introSeenKey] = seen }
    }

    suspend fun setDefaultDeviceName(name: String?) {
        appContext.settingsDataStore.edit { prefs ->
            if (name.isNullOrBlank()) {
                prefs.remove(defaultDeviceKey)
            } else {
                prefs[defaultDeviceKey] = name
            }
        }
    }

    suspend fun setAndroidTvShortcuts(shortcuts: List<AppShortcut>) {
        appContext.settingsDataStore.edit { prefs ->
            prefs[androidTvShortcutsKey] = encodeShortcuts(shortcuts)
        }
    }

    suspend fun setAndroidTvShortcutColumns(columns: Int) {
        appContext.settingsDataStore.edit { prefs ->
            prefs[androidTvShortcutColumnsKey] = columns.coerceIn(2, 4).toString()
        }
    }

    private fun encodeShortcuts(shortcuts: List<AppShortcut>): String = shortcuts
        .take(MAX_SHORTCUTS)
        .joinToString("\n") { shortcut ->
            listOf(shortcut.id, shortcut.label, shortcut.target)
                .joinToString("|") { encodePart(it) }
        }

    private fun decodeShortcuts(value: String?): List<AppShortcut> {
        if (value.isNullOrBlank()) return AppShortcutDefaults.all
        val parsed: List<AppShortcut> = value.lineSequence().mapNotNull { record ->
            val fields = record.split('|', limit = 3)
            if (fields.size != 3) return@mapNotNull null
            val decoded = fields.map(::decodePart)
            if (decoded.any { it == null }) return@mapNotNull null
            val id = decoded[0] ?: return@mapNotNull null
            val label = decoded[1] ?: return@mapNotNull null
            val target = decoded[2] ?: return@mapNotNull null
            if (id.isBlank() || label.isBlank() || target.isBlank()) return@mapNotNull null
            AppShortcut(id, label, target)
        }.toList().distinctBy { it.id }.take(MAX_SHORTCUTS)
        return if (parsed.isEmpty()) AppShortcutDefaults.all else parsed
    }

    private fun encodePart(value: String): String = Base64.encodeToString(
        value.toByteArray(Charsets.UTF_8),
        Base64.NO_WRAP or Base64.URL_SAFE,
    )

    private fun decodePart(value: String): String? = runCatching {
        Base64.decode(value, Base64.NO_WRAP or Base64.URL_SAFE).toString(Charsets.UTF_8)
    }.getOrNull()

    private companion object {
        const val MAX_SHORTCUTS = 12
    }

}
