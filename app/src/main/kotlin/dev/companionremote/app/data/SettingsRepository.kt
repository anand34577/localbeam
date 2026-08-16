package dev.companionremote.app.data

import android.content.Context
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

}
