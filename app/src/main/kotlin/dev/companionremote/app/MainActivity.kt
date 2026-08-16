package dev.companionremote.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dev.companionremote.app.data.ThemeMode
import dev.companionremote.app.i18n.LocalAppStrings
import dev.companionremote.app.i18n.currentSystemLanguage
import dev.companionremote.app.i18n.resolveStrings
import dev.companionremote.app.theme.modernColorScheme
import dev.companionremote.app.theme.ThemeVariant
import dev.companionremote.app.ui.DeviceListScreen
import dev.companionremote.app.ui.PairingScreen
import dev.companionremote.app.ui.RemoteScreen
import dev.companionremote.app.ui.SettingsScreen

class MainActivity : ComponentActivity() {

    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val themeMode by viewModel.themeMode.collectAsState()
            val themeVariant by viewModel.themeVariant.collectAsState()
            CompanionTheme(themeMode, themeVariant) {
                val screen by viewModel.screen.collectAsState()
                val language by viewModel.language.collectAsState()
                val strings = resolveStrings(language, currentSystemLanguage())
                CompositionLocalProvider(LocalAppStrings provides strings) {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        when (val current = screen) {
                            is Screen.Startup -> Unit
                            is Screen.DeviceList -> DeviceListScreen(viewModel)
                            is Screen.Settings -> SettingsScreen(viewModel)
                            is Screen.Pairing -> PairingScreen(viewModel, current.device)
                            is Screen.Remote -> RemoteScreen(viewModel, current.device)
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.onForeground()
    }
}

@Composable
fun CompanionTheme(
    themeMode: ThemeMode,
    themeVariant: ThemeVariant = ThemeVariant.LocalBeam,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    MaterialTheme(colorScheme = modernColorScheme(dark, themeVariant)) { content() }
}
