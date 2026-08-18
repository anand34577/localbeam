package dev.companionremote.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.LocalMovies
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.isSystemInDarkTheme
import dev.companionremote.app.AppViewModel
import dev.companionremote.app.ConnectionState
import dev.companionremote.app.MediaAction
import dev.companionremote.app.R
import dev.companionremote.app.androidtv.AndroidTvButton
import dev.companionremote.app.data.AppShortcut
import dev.companionremote.app.data.RemoteShelfMode
import dev.companionremote.app.discovery.DiscoveredAtv
import dev.companionremote.app.discovery.TvPlatform
import dev.companionremote.app.i18n.LocalAppStrings
import dev.companionremote.app.theme.glass
import dev.companionremote.protocol.client.HidCommand
import dev.companionremote.protocol.client.KeyboardFocusState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteScreen(viewModel: AppViewModel, device: DiscoveredAtv) {
    val connectionState by viewModel.connectionState.collectAsState()
    val connectionError by viewModel.connectionError.collectAsState()
    val hapticEnabled by viewModel.hapticEnabled.collectAsState()
    val hapticStrength by viewModel.hapticStrength.collectAsState()
    val androidTvShortcuts by viewModel.androidTvShortcuts.collectAsState()
    val androidTvShortcutColumns by viewModel.androidTvShortcutColumns.collectAsState()
    val remoteShelfMode by viewModel.remoteShelfMode.collectAsState()
    val androidTvVoiceActive by viewModel.androidTvVoiceActive.collectAsState()
    val keyboardFocus by viewModel.keyboardFocus.collectAsState()
    val keyboardText by viewModel.keyboardText.collectAsState()
    val keyboardOpenRequest by viewModel.keyboardOpenRequest.collectAsState()
    val introSeen by viewModel.introSeen.collectAsState()
    val androidTv = device.platform == TvPlatform.AndroidTv
    val s = LocalAppStrings.current
    val context = LocalContext.current
    var powerMenu by remember { mutableStateOf(false) }
    var tab by remember { mutableIntStateOf(0) }
    var introDismissed by remember { mutableStateOf(false) }

    val buzz = rememberHaptic(hapticEnabled, hapticStrength)

    fun press(command: HidCommand) { buzz(); viewModel.pressButton(command) }
    fun hold(command: HidCommand) { buzz(); viewModel.holdButton(command) }
    fun ok() { buzz(); viewModel.touchTap() }
    fun okLong() { buzz(); viewModel.holdButton(HidCommand.Select) }
    fun volStep(up: Boolean) { viewModel.pressButton(if (up) HidCommand.VolumeUp else HidCommand.VolumeDown) }
    fun volTap(up: Boolean) { buzz(); volStep(up) }
    fun pressAndroid(command: AndroidTvButton) { buzz(); viewModel.pressAndroidButton(command) }
    fun launchAndroidApp(packageName: String) { viewModel.launchApp(packageName) }
    val localVoice = rememberVoiceInput(
        onResult = viewModel::dictateText,
        onError = {},
    )
    var voicePointerDown by remember { mutableStateOf(false) }
    val androidVoicePermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted && voicePointerDown) viewModel.startAndroidTvVoice()
    }
    fun startVoicePress() {
        if (voicePointerDown) return
        voicePointerDown = true
        buzz()
        if (!androidTv) {
            localVoice.start()
        } else if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            viewModel.startAndroidTvVoice()
        } else {
            androidVoicePermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    fun stopVoicePress() {
        if (!voicePointerDown) return
        voicePointerDown = false
        buzz()
        if (!androidTv) localVoice.stop() else viewModel.stopAndroidTvVoice()
    }
    val voiceActive = if (androidTv) androidTvVoiceActive else localVoice.active

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusDot(connectionState)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                device.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                when (connectionState) {
                                    ConnectionState.Connected -> s.connected
                                    ConnectionState.Connecting -> s.connecting
                                    ConnectionState.Disconnected -> s.disconnected
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { viewModel.closeRemote() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.openSettings() }) {
                        Icon(Icons.Rounded.Settings, contentDescription = s.settings)
                    }
                    IconButton(
                        onClick = {
                            if (androidTv) {
                                buzz()
                                // Android's power key toggles between sleep and wake.
                                viewModel.sleep()
                            } else {
                                powerMenu = true
                            }
                        },
                    ) {
                        Icon(Icons.Rounded.PowerSettingsNew, contentDescription = "Power")
                    }
                    if (!androidTv) {
                        DropdownMenu(expanded = powerMenu, onDismissRequest = { powerMenu = false }) {
                            DropdownMenuItem(text = { Text(s.wake) }, onClick = { powerMenu = false; viewModel.wake() })
                            DropdownMenuItem(text = { Text(s.sleep) }, onClick = { powerMenu = false; viewModel.sleep() })
                        }
                    }
                },
            )
            },
        ) { padding ->
            PullToRefreshBox(
                isRefreshing = connectionState == ConnectionState.Connecting,
                onRefresh = {
                    // A pull is an explicit reconnect request even when the
                    // status label still says Connected. Restart the transport
                    // and let the connection state turn the indicator off.
                    viewModel.reconnect()
                },
                modifier = Modifier.fillMaxSize().padding(padding),
            ) {
                Column(Modifier.fillMaxSize()) {
                    SegmentedTabs(tab, includeTouch = !androidTv, onSelect = { tab = it })

                    when (connectionState) {
                        ConnectionState.Disconnected -> ConnectionBanner(connectionError) { viewModel.reconnect() }
                        ConnectionState.Connecting -> ReconnectingBanner()
                        ConnectionState.Connected -> Unit
                    }

                    if (tab == 0) {
                        DpadPane(
                            androidTv = androidTv,
                            press = ::press,
                            pressAndroid = ::pressAndroid,
                            hold = ::hold,
                            ok = ::ok,
                            okLong = ::okLong,
                            volTap = ::volTap,
                            voiceActive = voiceActive,
                            onVoicePress = ::startVoicePress,
                            onVoiceRelease = ::stopVoicePress,
                            shelfMode = remoteShelfMode,
                            mediaPress = { action -> buzz(); viewModel.pressMedia(action) },
                            shortcuts = androidTvShortcuts,
                            shortcutColumns = androidTvShortcutColumns,
                            launchApp = ::launchAndroidApp,
                            shortcutPress = buzz,
                        )
                    } else if (!androidTv) {
                        TouchpadPane(viewModel, ::press, ::hold, ::ok, ::okLong)
                    }
                }
            }
        }

        if (androidTv && keyboardFocus == KeyboardFocusState.Focused) {
            AndroidTvKeyboardBridge(
                text = keyboardText,
                openRequest = keyboardOpenRequest,
                onTextChanged = viewModel::onKeyboardTextChanged,
                onDismiss = viewModel::dismissKeyboard,
            )
        }

        // First-run tutorial, shown once right after the first pairing.
        if (!introSeen && !introDismissed) {
            IntroOverlay(onDone = { introDismissed = true; viewModel.markIntroSeen() })
        }
    }
}

/**
 * A 1dp local editor used only as an IME bridge. Android TV supplies the
 * focus event; the phone's normal keyboard edits this field, and the
 * ViewModel sends the resulting string back using the TV's batch text API.
 */
@Composable
private fun AndroidTvKeyboardBridge(
    text: String,
    openRequest: Long,
    onTextChanged: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    DisposableEffect(Unit) {
        onDispose { keyboardController?.hide() }
    }

    BasicTextField(
        value = text,
        onValueChange = onTextChanged,
        modifier = Modifier
            .size(1.dp)
            .alpha(0f)
            .focusRequester(focusRequester),
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = {
            keyboardController?.hide()
            focusManager.clearFocus(force = true)
            onDismiss()
        }),
    )

    LaunchedEffect(openRequest) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }
}

@Composable
private fun StatusDot(state: ConnectionState) {
    val darkTheme = isSystemInDarkTheme()
    val connectedColor = if (darkTheme) Color(0xFF81C995) else Color(0xFF197A3A)
    val disconnectedColor = if (darkTheme) Color(0xFFFF9A9A) else Color(0xFFC62828)
    val targetColor = when (state) {
        ConnectionState.Connected -> connectedColor
        ConnectionState.Connecting -> MaterialTheme.colorScheme.tertiary
        ConnectionState.Disconnected -> disconnectedColor
    }
    val color by animateColorAsState(targetColor, label = "connectionStatusColor")
    val transition = rememberInfiniteTransition(label = "connectionStatusPulse")
    val connectingScale by transition.animateFloat(
        initialValue = 0.84f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "connectionStatusScale",
    )
    Box(
        Modifier
            .size(10.dp)
            .graphicsLayer {
                val scale = if (state == ConnectionState.Connecting) connectingScale else 1f
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(color),
    )
}

@Composable
private fun ReconnectingBanner() {
    val s = LocalAppStrings.current
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            Text(
                s.reconnecting,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SegmentedTabs(selected: Int, includeTouch: Boolean, onSelect: (Int) -> Unit) {
    val s = LocalAppStrings.current
    val titles = if (includeTouch) {
        listOf(s.tabRemote to Icons.Default.Gamepad, s.tabTouch to Icons.Default.TouchApp)
    } else {
        listOf(s.tabRemote to Icons.Default.Gamepad)
    }
    val safeSelected = selected.coerceIn(0, titles.lastIndex)
    TabRow(
        selectedTabIndex = safeSelected,
        containerColor = MaterialTheme.colorScheme.background,
        indicator = { positions ->
            TabRowDefaults.PrimaryIndicator(
                Modifier.tabIndicatorOffset(positions[safeSelected]),
                width = 40.dp,
            )
        },
    ) {
        titles.forEachIndexed { i, (title, icon) ->
            Tab(
                selected = safeSelected == i,
                onClick = { onSelect(i) },
                text = { Text(title, style = MaterialTheme.typography.labelLarge) },
                icon = { Icon(icon, contentDescription = null, Modifier.size(20.dp)) },
                selectedContentColor = MaterialTheme.colorScheme.primary,
                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ConnectionBanner(error: String?, onReconnect: () -> Unit) {
    val s = LocalAppStrings.current
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            Modifier.padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                error ?: s.connectionLost,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onReconnect) { Text(s.reconnect) }
        }
    }
}

/** Full remote pane: big D-pad dial, a row of action keys, volume slider. */
@Composable
private fun DpadPane(
    androidTv: Boolean,
    press: (HidCommand) -> Unit,
    pressAndroid: (AndroidTvButton) -> Unit,
    hold: (HidCommand) -> Unit,
    ok: () -> Unit,
    okLong: () -> Unit,
    volTap: (Boolean) -> Unit,
    voiceActive: Boolean,
    onVoicePress: () -> Unit,
    onVoiceRelease: () -> Unit,
    shelfMode: RemoteShelfMode,
    mediaPress: (MediaAction) -> Unit,
    shortcuts: List<AppShortcut>,
    shortcutColumns: Int,
    launchApp: (String) -> Unit,
    shortcutPress: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 600.dp
        val dpadDiameter = when {
            maxWidth < 360.dp -> (maxWidth - 80.dp).coerceIn(208.dp, 248.dp)
            maxWidth < 600.dp -> (maxWidth * 0.56f).coerceIn(224.dp, 264.dp)
            else -> (maxWidth * 0.23f).coerceIn(232.dp, 280.dp)
        }
        if (wide) {
            Row(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                horizontalArrangement = Arrangement.spacedBy(28.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    Modifier.weight(1.1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    DpadDial(diameter = dpadDiameter, press = press, ok = ok, okLong = okLong)
                }
                Column(
                    Modifier.weight(0.9f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Surface(
                        shape = RoundedCornerShape(28.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            RemoteActionPanel(
                                androidTv,
                                press,
                                pressAndroid,
                                hold,
                                volTap,
                                voiceActive,
                                onVoicePress,
                                onVoiceRelease,
                            )
                        }
                    }
                    if (shelfMode != RemoteShelfMode.None) {
                        Spacer(Modifier.height(16.dp))
                        when (shelfMode) {
                            RemoteShelfMode.Applications -> if (androidTv) {
                                AndroidTvShortcutPanel(shortcuts, shortcutColumns, launchApp, shortcutPress)
                            }
                            RemoteShelfMode.MediaButtons -> MediaButtonPanel(androidTv, mediaPress, shortcutPress)
                            RemoteShelfMode.ApplicationsAndMedia -> {
                                if (androidTv) {
                                    AndroidTvShortcutPanel(shortcuts, shortcutColumns, launchApp, shortcutPress)
                                    Spacer(Modifier.height(16.dp))
                                }
                                MediaButtonPanel(androidTv, mediaPress, shortcutPress)
                            }
                            RemoteShelfMode.None -> Unit
                        }
                    }
                }
            }
        } else {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                DpadDial(diameter = dpadDiameter, press = press, ok = ok, okLong = okLong)
                Spacer(Modifier.height(24.dp))
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        RemoteActionPanel(
                            androidTv,
                            press,
                            pressAndroid,
                            hold,
                            volTap,
                            voiceActive,
                            onVoicePress,
                            onVoiceRelease,
                        )
                    }
                }
                    if (shelfMode != RemoteShelfMode.None) {
                        Spacer(Modifier.height(16.dp))
                        when (shelfMode) {
                            RemoteShelfMode.Applications -> if (androidTv) {
                                AndroidTvShortcutPanel(shortcuts, shortcutColumns, launchApp, shortcutPress)
                            }
                            RemoteShelfMode.MediaButtons -> MediaButtonPanel(androidTv, mediaPress, shortcutPress)
                            RemoteShelfMode.ApplicationsAndMedia -> {
                                if (androidTv) {
                                    AndroidTvShortcutPanel(shortcuts, shortcutColumns, launchApp, shortcutPress)
                                    Spacer(Modifier.height(16.dp))
                                }
                                MediaButtonPanel(androidTv, mediaPress, shortcutPress)
                            }
                            RemoteShelfMode.None -> Unit
                    }
                }
            }
        }
    }
}

@Composable
private fun RemoteActionPanel(
    androidTv: Boolean,
    press: (HidCommand) -> Unit,
    pressAndroid: (AndroidTvButton) -> Unit,
    hold: (HidCommand) -> Unit,
    volTap: (Boolean) -> Unit,
    voiceActive: Boolean,
    onVoicePress: () -> Unit,
    onVoiceRelease: () -> Unit,
) {
    var moreMenu by remember { mutableStateOf(false) }

    @Composable
    fun keyRow(content: @Composable RowScope.() -> Unit) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Keep volume beside the primary action grid. The rail still supports
        // tapping its upper/lower half and vertical swipes, but no longer
        // creates a separate, mostly empty row at the bottom of the card.
        VolumeSlider(onStep = volTap, onTap = volTap)
        Column(Modifier.weight(1f)) {
            keyRow {
                RoundKey(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    label = "Back",
                    size = 56.dp,
                    onClick = { press(HidCommand.Menu) },
                )
                RoundKey(
                    icon = Icons.Rounded.Home,
                    label = "Home (hold: Control Center)",
                    size = 56.dp,
                    onClick = { press(HidCommand.Home) },
                    onLongClick = { hold(HidCommand.Home) },
                )
                if (androidTv) {
                    RoundKey(
                        icon = Icons.Rounded.Settings,
                        label = "TV settings",
                        size = 56.dp,
                        onClick = { pressAndroid(AndroidTvButton.Settings) },
                    )
                } else {
                    RoundKey(
                        icon = Icons.Rounded.Tv,
                        label = "Guide",
                        size = 56.dp,
                        onClick = { press(HidCommand.Guide) },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            keyRow {
                if (androidTv) {
                    RoundKey(
                        icon = Icons.Rounded.VolumeOff,
                        label = "Mute",
                        size = 56.dp,
                        onClick = { pressAndroid(AndroidTvButton.Mute) },
                    )
                    RoundKey(
                        icon = Icons.Rounded.Mic,
                        label = "Hold to talk",
                        size = 56.dp,
                        accent = voiceActive,
                        onClick = {},
                        onTouchDown = onVoicePress,
                        onTouchUp = onVoiceRelease,
                    )
                    RoundKey(
                        icon = Icons.Rounded.Tv,
                        label = "TV input",
                        size = 56.dp,
                        onClick = { pressAndroid(AndroidTvButton.TvInput) },
                    )
                } else {
                    RoundKey(
                        icon = Icons.Rounded.KeyboardArrowUp,
                        label = "Channel up",
                        size = 56.dp,
                        onClick = { press(HidCommand.ChannelIncrement) },
                    )
                    RoundKey(
                        icon = Icons.Rounded.Mic,
                        label = "Hold to talk",
                        size = 56.dp,
                        onClick = {},
                        onTouchDown = onVoicePress,
                        onTouchUp = onVoiceRelease,
                    )
                    Box {
                        RoundKey(icon = Icons.Rounded.MoreVert, label = "More controls", size = 56.dp, onClick = { moreMenu = true })
                        DropdownMenu(expanded = moreMenu, onDismissRequest = { moreMenu = false }) {
                            DropdownMenuItem(text = { Text("Channel down") }, onClick = { moreMenu = false; press(HidCommand.ChannelDecrement) })
                            DropdownMenuItem(text = { Text("Page up") }, onClick = { moreMenu = false; press(HidCommand.PageUp) })
                            DropdownMenuItem(text = { Text("Page down") }, onClick = { moreMenu = false; press(HidCommand.PageDown) })
                        }
                    }
                }
            }
        }
    }
}

private enum class ShortcutBrand { Zee5, YouTube, PrimeVideo, JioHotstar, Custom }

private fun AppShortcut.brand(): ShortcutBrand = when {
    id.equals("zee5", ignoreCase = true) || label.equals("ZEE5", ignoreCase = true) -> ShortcutBrand.Zee5
    id.equals("youtube", ignoreCase = true) || label.equals("YouTube", ignoreCase = true) -> ShortcutBrand.YouTube
    id.equals("prime_video", ignoreCase = true) || label.equals("Prime Video", ignoreCase = true) -> ShortcutBrand.PrimeVideo
    id.equals("jiohotstar", ignoreCase = true) || label.equals("JioHotstar", ignoreCase = true) -> ShortcutBrand.JioHotstar
    else -> ShortcutBrand.Custom
}

@Composable
private fun MediaButtonPanel(
    androidTv: Boolean,
    onPress: (MediaAction) -> Unit,
    onPressed: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            "Media controls",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MediaButton(Icons.Rounded.FastRewind, "Rewind", Modifier.weight(1f), onPress, onPressed, MediaAction.Rewind)
            MediaButton(Icons.Rounded.PlayArrow, "Play/Pause", Modifier.weight(1f), onPress, onPressed, MediaAction.PlayPause)
            MediaButton(Icons.Rounded.FastForward, "Fast forward", Modifier.weight(1f), onPress, onPressed, MediaAction.FastForward)
            if (androidTv) {
                MediaButton(Icons.Rounded.Stop, "Stop", Modifier.weight(1f), onPress, onPressed, MediaAction.Stop)
            }
        }
    }
}

@Composable
private fun MediaButton(
    icon: ImageVector,
    label: String,
    modifier: Modifier,
    onPress: (MediaAction) -> Unit,
    onPressed: () -> Unit,
    action: MediaAction,
) {
    RoundKey(
        icon = icon,
        label = label,
        size = 52.dp,
        modifier = modifier,
        onClick = { onPressed(); onPress(action) },
    )
}

/** Separate app section below the action card; its grid fills row by row. */
@Composable
private fun AndroidTvShortcutPanel(
    shortcuts: List<AppShortcut>,
    columns: Int,
    onLaunch: (String) -> Unit,
    onPressed: () -> Unit,
) {
    if (shortcuts.isEmpty()) return
    val safeColumns = columns.coerceIn(2, 4)
    Column(Modifier.fillMaxWidth()) {
        Text(
            "Apps",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        shortcuts.chunked(safeColumns).forEachIndexed { rowIndex, row ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { shortcut ->
                    AndroidTvShortcutButton(
                        shortcut = shortcut,
                        modifier = Modifier.weight(1f),
                        onClick = { onLaunch(shortcut.target) },
                        onPressed = onPressed,
                    )
                }
                repeat(safeColumns - row.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
            if (rowIndex < (shortcuts.size - 1) / safeColumns) Spacer(Modifier.height(8.dp))
        }
    }
}

/** App shortcut with the same scale/ripple treatment as the circular remote keys. */
@Composable
private fun AndroidTvShortcutButton(
    shortcut: AppShortcut,
    modifier: Modifier,
    onClick: () -> Unit,
    onPressed: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "appShortcutScale",
    )
    LaunchedEffect(pressed) {
        if (pressed) onPressed()
    }
    val brand = shortcut.brand()
    val brandContainer = when (brand) {
        ShortcutBrand.Zee5 -> MaterialTheme.colorScheme.tertiaryContainer
        ShortcutBrand.YouTube -> MaterialTheme.colorScheme.errorContainer
        ShortcutBrand.PrimeVideo -> MaterialTheme.colorScheme.secondaryContainer
        ShortcutBrand.JioHotstar -> MaterialTheme.colorScheme.primaryContainer
        ShortcutBrand.Custom -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val brandContent = when (brand) {
        ShortcutBrand.Zee5 -> MaterialTheme.colorScheme.onTertiaryContainer
        ShortcutBrand.YouTube -> MaterialTheme.colorScheme.onErrorContainer
        ShortcutBrand.PrimeVideo -> MaterialTheme.colorScheme.onSecondaryContainer
        ShortcutBrand.JioHotstar -> MaterialTheme.colorScheme.onPrimaryContainer
        ShortcutBrand.Custom -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val brandIcon = when (brand) {
        ShortcutBrand.Zee5 -> Icons.Rounded.LiveTv
        ShortcutBrand.YouTube -> Icons.Rounded.PlayArrow
        ShortcutBrand.PrimeVideo -> Icons.Rounded.LocalMovies
        ShortcutBrand.JioHotstar -> Icons.Rounded.Tv
        ShortcutBrand.Custom -> Icons.Rounded.Apps
    }
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        }.height(64.dp),
        shape = RoundedCornerShape(16.dp),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
        interactionSource = interactionSource,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                Modifier.size(30.dp).clip(CircleShape).background(brandContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(brandIcon, contentDescription = null, Modifier.size(18.dp), tint = brandContent)
            }
            Text(
                shortcut.label,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Responsive circular D-pad with a centre OK (tap = select, hold = menu). */
@Composable
private fun DpadDial(
    diameter: Dp,
    press: (HidCommand) -> Unit,
    ok: () -> Unit,
    okLong: () -> Unit,
) {
    val arrowSize = (diameter * 0.24f).coerceIn(68.dp, 88.dp)
    val okSize = (diameter * 0.30f).coerceIn(80.dp, 96.dp)
    Box(
        Modifier
            .size(diameter)
            .glass(CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        DialArrow(Icons.Rounded.KeyboardArrowUp, "Up", arrowSize, Modifier.align(Alignment.TopCenter)) { press(HidCommand.Up) }
        DialArrow(Icons.Rounded.KeyboardArrowDown, "Down", arrowSize, Modifier.align(Alignment.BottomCenter)) { press(HidCommand.Down) }
        DialArrow(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Left", arrowSize, Modifier.align(Alignment.CenterStart)) { press(HidCommand.Left) }
        DialArrow(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Right", arrowSize, Modifier.align(Alignment.CenterEnd)) { press(HidCommand.Right) }
        PressableCircle(
            size = okSize,
            accent = true,
            onClick = ok,
            onLongClick = okLong,
        ) {
            Text(
                "OK",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = it,
            )
        }
    }
}

@Composable
private fun DialArrow(
    icon: ImageVector,
    label: String,
    size: Dp,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    PressableCircle(
        size = size,
        modifier = modifier,
        showContainer = false,
        showBorder = false,
        onClick = onClick,
    ) {
        KeyGlyph(icon, null, label, size * 0.50f, it)
    }
}

/**
 * Vertical volume control. Tapping the top half raises the volume and the
 * bottom half lowers it; dragging up/down scrubs (one step per ~22 dp). Both
 * gestures live on the whole pill — separate pointerInput blocks so a tap and
 * a drag don't fight each other (the earlier IconButton version swallowed the
 * drag, so swiping did nothing).
 */
@Composable
private fun VolumeSlider(onStep: (Boolean) -> Unit, onTap: (Boolean) -> Unit) {
    val shape = RoundedCornerShape(34.dp)
    var pressedHalf by remember { mutableStateOf<Boolean?>(null) }
    val pressScale by animateFloatAsState(
        targetValue = if (pressedHalf != null) 0.93f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "volumeButtonScale",
    )
    val railColor by animateColorAsState(
        targetValue = if (pressedHalf != null) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        label = "volumeButtonColor",
    )
    val railBorderColor by animateColorAsState(
        targetValue = if (pressedHalf != null) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outlineVariant
        },
        label = "volumeButtonBorderColor",
    )
    Box(
        Modifier
            .width(64.dp)
            .height(172.dp)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clip(shape)
            .background(railColor, shape)
            .border(1.dp, railBorderColor, shape)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = { offset ->
                        pressedHalf = offset.y < size.height / 2f
                        try {
                            tryAwaitRelease()
                        } finally {
                            pressedHalf = null
                        }
                    },
                    onTap = { offset -> onTap(offset.y < size.height / 2f) },
                )
            }
            .pointerInput(Unit) {
                var acc = 0f
                val step = 22.dp.toPx()
                detectVerticalDragGestures(
                    onDragEnd = { acc = 0f },
                    onDragCancel = { acc = 0f },
                ) { change, dy ->
                    change.consume()
                    acc += dy
                    while (acc <= -step) { onStep(true); acc += step }   // up = louder
                    while (acc >= step) { onStep(false); acc -= step }   // down = softer
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        // Faint track hint that the pill is swipeable.
        Box(
            Modifier
                .width(4.dp)
                .height(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)),
        )
        Icon(
            Icons.Rounded.Add,
            contentDescription = "Volume up",
            Modifier.align(Alignment.TopCenter).padding(top = 18.dp).size(26.dp),
            tint = MaterialTheme.colorScheme.onSurface,
        )
        Icon(
            Icons.Rounded.Remove,
            contentDescription = "Volume down",
            Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp).size(26.dp),
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TouchpadPane(
    viewModel: AppViewModel,
    press: (HidCommand) -> Unit,
    hold: (HidCommand) -> Unit,
    ok: () -> Unit,
    okLong: () -> Unit,
) {
    val lastTouch = remember { mutableStateOf(500L to 500L) }
    val hintColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.28f)
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .glass(RoundedCornerShape(28.dp))
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { ok() }, onLongPress = { okLong() })
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val x = (offset.x * 1000 / size.width).toLong()
                            val y = (offset.y * 1000 / size.height).toLong()
                            lastTouch.value = x to y
                            viewModel.sendTouch(x, y, dev.companionremote.protocol.client.TouchPhase.Press)
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val x = (change.position.x * 1000 / size.width).toLong()
                            val y = (change.position.y * 1000 / size.height).toLong()
                            lastTouch.value = x to y
                            viewModel.sendTouch(x, y, dev.companionremote.protocol.client.TouchPhase.Hold)
                        },
                        onDragEnd = {
                            val (x, y) = lastTouch.value
                            viewModel.sendTouch(x, y, dev.companionremote.protocol.client.TouchPhase.Release)
                        },
                        onDragCancel = {
                            val (x, y) = lastTouch.value
                            viewModel.sendTouch(x, y, dev.companionremote.protocol.client.TouchPhase.Release)
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.KeyboardArrowUp, null, Modifier.align(Alignment.TopCenter).padding(8.dp).size(28.dp), tint = hintColor)
            Icon(Icons.Rounded.KeyboardArrowDown, null, Modifier.align(Alignment.BottomCenter).padding(8.dp).size(28.dp), tint = hintColor)
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, null, Modifier.align(Alignment.CenterStart).padding(8.dp).size(28.dp), tint = hintColor)
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, Modifier.align(Alignment.CenterEnd).padding(8.dp).size(28.dp), tint = hintColor)
            Text(
                LocalAppStrings.current.swipeHint,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(16.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundKey(icon = Icons.AutoMirrored.Filled.ArrowBack, label = "Back", size = 48.dp, onClick = { press(HidCommand.Menu) })
            RoundKey(
                icon = Icons.Rounded.Home,
                label = "Home (hold: Control Center)",
                size = 48.dp,
                onClick = { press(HidCommand.Home) },
                onLongClick = { hold(HidCommand.Home) },
            )
            RoundKey(painter = painterResource(R.drawable.ic_play_pause), label = "Play/Pause", size = 48.dp, onClick = { press(HidCommand.PlayPause) })
            RoundKey(icon = Icons.Rounded.Tv, label = "Guide", size = 48.dp, onClick = { press(HidCommand.Guide) })
            RoundKey(icon = Icons.Rounded.KeyboardArrowUp, label = "Channel up", size = 48.dp, onClick = { press(HidCommand.ChannelIncrement) })
        }
    }
}

/** A round glass key; supports an optional long-press and an accent tint. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RoundKey(
    label: String,
    icon: ImageVector? = null,
    painter: Painter? = null,
    size: Dp = 60.dp,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onTouchDown: (() -> Unit)? = null,
    onTouchUp: (() -> Unit)? = null,
) {
    PressableCircle(
        size = size,
        modifier = modifier,
        accent = accent,
        onClick = onClick,
        onLongClick = onLongClick,
        onTouchDown = onTouchDown,
        onTouchUp = onTouchUp,
    ) {
        KeyGlyph(icon, painter, label, size * 0.42f, it)
    }
}

/** Material press feedback: ripple, spring compression and a tonal colour shift. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PressableCircle(
    size: Dp,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    showContainer: Boolean = true,
    showBorder: Boolean = true,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onTouchDown: (() -> Unit)? = null,
    onTouchUp: (() -> Unit)? = null,
    content: @Composable (Color) -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val currentOnTouchDown by rememberUpdatedState(onTouchDown)
    val currentOnTouchUp by rememberUpdatedState(onTouchUp)
    var touchPressed by remember { mutableStateOf(false) }
    val interactionPressed by interactionSource.collectIsPressedAsState()
    val pressed = interactionPressed || touchPressed
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.90f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "remoteButtonScale",
    )
    val containerColor by animateColorAsState(
        targetValue = when {
            !showContainer && pressed -> MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
            !showContainer -> Color.Transparent
            pressed && accent -> MaterialTheme.colorScheme.primary
            pressed -> MaterialTheme.colorScheme.primaryContainer
            accent -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.surfaceContainerHigh
        },
        label = "remoteButtonColor",
    )
    val contentColor by animateColorAsState(
        targetValue = when {
            !showContainer && pressed -> MaterialTheme.colorScheme.primary
            !showContainer -> MaterialTheme.colorScheme.onSurface
            pressed && accent -> MaterialTheme.colorScheme.onPrimary
            pressed || accent -> MaterialTheme.colorScheme.onPrimaryContainer
            else -> MaterialTheme.colorScheme.onSurface
        },
        label = "remoteButtonContentColor",
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            !showBorder -> Color.Transparent
            pressed -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.outlineVariant
        },
        label = "remoteButtonBorderColor",
    )

    val gestureModifier = if (onTouchDown != null || onTouchUp != null) {
        Modifier
            .indication(interactionSource, LocalIndication.current)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        val pressInteraction = PressInteraction.Press(Offset.Zero)
                        touchPressed = true
                        interactionSource.emit(pressInteraction)
                        currentOnTouchDown?.invoke()
                        var released = false
                        try {
                            released = tryAwaitRelease()
                        } finally {
                            touchPressed = false
                            interactionSource.emit(
                                if (released) {
                                    PressInteraction.Release(pressInteraction)
                                } else {
                                    PressInteraction.Cancel(pressInteraction)
                                },
                            )
                            currentOnTouchUp?.invoke()
                        }
                    },
                )
            }
    } else {
        Modifier.combinedClickable(
            interactionSource = interactionSource,
            indication = LocalIndication.current,
            onClick = onClick,
            onLongClick = onLongClick,
        )
    }

    Box(
        modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(containerColor, CircleShape)
            .then(if (showBorder) Modifier.border(1.dp, borderColor, CircleShape) else Modifier)
            .then(gestureModifier),
        contentAlignment = Alignment.Center,
    ) {
        content(contentColor)
    }
}

/** Renders either a vector [icon] or a drawable [painter] as a key glyph. */
@Composable
private fun KeyGlyph(icon: ImageVector?, painter: Painter?, label: String, glyphSize: Dp, tint: Color) {
    val p = painter ?: icon?.let { rememberVectorPainter(it) } ?: return
    Icon(p, contentDescription = label, Modifier.size(glyphSize), tint = tint)
}
