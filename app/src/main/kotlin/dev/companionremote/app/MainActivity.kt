package dev.companionremote.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
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
import androidx.compose.ui.platform.LocalContext
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
        handleNotificationIntent(intent)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_PERMISSION_REQUEST)
        }
        setContent {
            val themeMode by viewModel.themeMode.collectAsState()
            val themeVariant by viewModel.themeVariant.collectAsState()
            val dynamicColor by viewModel.dynamicColor.collectAsState()
            CompanionTheme(themeMode, themeVariant, dynamicColor) {
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
    }

    private fun handleNotificationIntent(intent: Intent?) {
        if (intent?.action == ACTION_RECONNECT) viewModel.reconnect()
    }

    private companion object {
        const val ACTION_RECONNECT = "dev.companionremote.app.RECONNECT_REMOTE"
        const val NOTIFICATION_PERMISSION_REQUEST = 4128
    }
}

@Composable
fun CompanionTheme(
    themeMode: ThemeMode,
    themeVariant: ThemeVariant = ThemeVariant.LocalBeam,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val dark = when (themeMode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val colorScheme = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) androidx.compose.material3.dynamicDarkColorScheme(context)
        else androidx.compose.material3.dynamicLightColorScheme(context)
    } else {
        modernColorScheme(dark, themeVariant)
    }
    MaterialTheme(colorScheme = colorScheme) { content() }
}
