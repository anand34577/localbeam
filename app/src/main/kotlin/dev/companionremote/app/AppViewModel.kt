package dev.companionremote.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.companionremote.app.androidtv.AndroidTvButton
import dev.companionremote.app.androidtv.AndroidTvPairingSession
import dev.companionremote.app.androidtv.AndroidTvRemoteClient
import dev.companionremote.app.data.CredentialsRepository
import dev.companionremote.app.data.HapticStrength
import dev.companionremote.app.data.SavedDeviceEndpoint
import dev.companionremote.app.data.SettingsRepository
import dev.companionremote.app.data.ThemeMode
import dev.companionremote.app.discovery.AndroidTvDiscovery
import dev.companionremote.app.discovery.AtvDiscovery
import dev.companionremote.app.discovery.DiscoveredAtv
import dev.companionremote.app.discovery.TvPlatform
import dev.companionremote.app.i18n.AppLanguage
import dev.companionremote.app.i18n.AppStrings
import dev.companionremote.app.i18n.EnglishStrings
import dev.companionremote.app.i18n.currentSystemLanguage
import dev.companionremote.app.i18n.resolveStrings
import dev.companionremote.app.theme.ThemeVariant
import dev.companionremote.protocol.client.CompanionClient
import dev.companionremote.protocol.client.HidCommand
import dev.companionremote.protocol.client.KeyboardFocusState
import dev.companionremote.protocol.client.TouchPhase
import kotlinx.coroutines.channels.Channel
import dev.companionremote.protocol.companion.CompanionConnection
import dev.companionremote.protocol.hap.HapCredentials
import dev.companionremote.protocol.hap.PairSetup
import dev.companionremote.protocol.hap.PairVerify
import dev.companionremote.protocol.transport.SocketTransport
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/** Which screen is showing. */
sealed interface Screen {
    data object Startup : Screen
    data object DeviceList : Screen
    data object Settings : Screen
    data class Pairing(val device: DiscoveredAtv) : Screen
    data class Remote(val device: DiscoveredAtv) : Screen
}

enum class ConnectionState { Connecting, Connected, Disconnected }

/** Per-device pairing check triggered by the refresh button in Settings. */
enum class DeviceVerify { Idle, Checking, Ok, Failed }

data class PairingUi(
    val awaitingPin: Boolean = false,
    val working: Boolean = true,
    val error: String? = null,
)

data class DeviceListUi(
    val devices: List<DiscoveredAtv> = emptyList(),
    val pairedNames: Set<String> = emptySet(),
    val scanning: Boolean = false,
)

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val discovery = AtvDiscovery(application)
    private val androidTvDiscovery = AndroidTvDiscovery(application)
    private val credentialsRepository = CredentialsRepository(application)
    private val settingsRepository = SettingsRepository(application)

    val screen = MutableStateFlow<Screen>(Screen.Startup)
    val deviceList = MutableStateFlow(DeviceListUi())
    val pairing = MutableStateFlow(PairingUi())
    val connectionState = MutableStateFlow(ConnectionState.Disconnected)
    val connectionError = MutableStateFlow<String?>(null)

    /** Language choice (persisted); drives the UI strings. */
    val language = MutableStateFlow(AppLanguage.System)

    /** Theme mode (persisted). */
    val themeMode = MutableStateFlow(ThemeMode.System)

    /** Accent direction (persisted); all variants remain semantic Material 3 palettes. */
    val themeVariant = MutableStateFlow(ThemeVariant.LocalBeam)

    /** Button-press vibration feedback (persisted). */
    val hapticEnabled = MutableStateFlow(true)
    val hapticStrength = MutableStateFlow(HapticStrength.Medium)

    /** Whether the first-run remote tutorial has already been shown. */
    val introSeen = MutableStateFlow(false)

    // Where to return when leaving Settings (device list or the remote).
    private var settingsReturnTo: Screen = Screen.DeviceList
    private var startupRoutingPending = true

    /** Paired device names, shown in Settings for management. */
    val pairedDevices = MutableStateFlow<List<String>>(emptyList())
    /** Paired device opened automatically on app start. */
    val defaultDeviceName = MutableStateFlow<String?>(null)

    /** Name of the TV currently being controlled (for "in use"). */
    val activeDeviceName = MutableStateFlow<String?>(null)

    /** Per-device pairing-check state, keyed by device name. */
    val deviceVerify = MutableStateFlow<Map<String, DeviceVerify>>(emptyMap())

    // Current strings, used for error messages produced in the ViewModel.
    private var strings: AppStrings = EnglishStrings

    /** Keyboard focus state on the TV (drives auto-open of the soft keyboard). */
    val keyboardFocus = MutableStateFlow(KeyboardFocusState.Unknown)

    /** The phone-side edit buffer mirrored to the TV text field. */
    val keyboardText = MutableStateFlow("")

    /** Set while the remote screen is active. */
    var client: CompanionClient? = null
        private set
    private var androidClient: AndroidTvRemoteClient? = null
    private var pairSetup: PairSetup? = null
    private var pairingConnection: CompanionConnection? = null
    private var androidPairing: AndroidTvPairingSession? = null
    private var reconnectJob: Job? = null
    private var keyboardFocusJob: Job? = null
    private var androidKeyboardTextJob: Job? = null
    private var textSyncJob: Job? = null
    private var connectionGeneration = 0L
    private var lastAndroidAppLaunchAt = 0L

    /** Launchable apps (bundle id → name); null until loaded. */
    val apps = MutableStateFlow<List<Pair<String, String>>?>(null)
    val appsError = MutableStateFlow<String?>(null)

    // Touch events must reach the device in order: single consumer channel.
    private val touchEvents = Channel<Triple<Long, Long, TouchPhase>>(capacity = 256)
    private var touchJob: Job? = null
    private var lastHoldSentAt = 0L

    init {
        viewModelScope.launch {
            settingsRepository.language.collect { lang ->
                language.value = lang
                strings = resolveStrings(lang, currentSystemLanguage())
            }
        }
        viewModelScope.launch {
            settingsRepository.themeMode.collect { themeMode.value = it }
        }
        viewModelScope.launch {
            settingsRepository.themeVariant.collect { themeVariant.value = it }
        }
        viewModelScope.launch {
            settingsRepository.hapticEnabled.collect { hapticEnabled.value = it }
        }
        viewModelScope.launch {
            settingsRepository.hapticStrength.collect { hapticStrength.value = it }
        }
        viewModelScope.launch {
            settingsRepository.introSeen.collect { introSeen.value = it }
        }
        viewModelScope.launch {
            settingsRepository.defaultDeviceName.collect { defaultDeviceName.value = it }
        }
        viewModelScope.launch {
            restoreSavedEndpoints()
            maybeOpenStartupRemote()
            if (screen.value is Screen.Startup && !hasPotentialStartupRemote()) {
                screen.value = Screen.DeviceList
            }
            startScan()
        }
    }

    // Settings

    fun setLanguage(lang: AppLanguage) {
        viewModelScope.launch { settingsRepository.setLanguage(lang) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun setThemeVariant(variant: ThemeVariant) {
        viewModelScope.launch { settingsRepository.setThemeVariant(variant) }
    }

    fun setHapticEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setHapticEnabled(enabled) }
    }

    fun setHapticStrength(strength: HapticStrength) {
        viewModelScope.launch { settingsRepository.setHapticStrength(strength) }
    }

    fun markIntroSeen() {
        viewModelScope.launch { settingsRepository.setIntroSeen(true) }
    }

    fun setDefaultDevice(name: String) {
        if (name !in pairedDevices.value) return
        defaultDeviceName.value = name
        viewModelScope.launch { settingsRepository.setDefaultDeviceName(name) }
    }

    fun openSettings() {
        startupRoutingPending = false
        settingsReturnTo = screen.value
        viewModelScope.launch {
            pairedDevices.value = credentialsRepository.pairedDeviceNames().sorted()
            deviceVerify.value = emptyMap()
            screen.value = Screen.Settings
        }
    }

    /** Re-check that a paired device is reachable and its pairing still valid. */
    fun verifyDevice(name: String) {
        // The device we're actively controlling is trivially verified.
        if (name == activeDeviceName.value && connectionState.value == ConnectionState.Connected) {
            setVerify(name, DeviceVerify.Ok)
            return
        }
        viewModelScope.launch {
            setVerify(name, DeviceVerify.Checking)
            val stored = credentialsRepository.load(name)
            if (stored == null) {
                setVerify(name, DeviceVerify.Failed)
                return@launch
            }
            val ok = if (stored.startsWith(ANDROID_CREDENTIAL_PREFIX)) {
                val credential = parseAndroidCredential(stored)
                val target = androidTvDiscovery.resolveByName(name)
                    ?: credentialsRepository.loadEndpoint(name)?.asDiscovered(name)
                credential != null && target != null && runCatching {
                    val probe = AndroidTvRemoteClient(
                        getApplication(),
                        target.host,
                        credential.alias,
                        credential.fingerprint,
                    )
                    try {
                        probe.connect()
                        true
                    } finally {
                        probe.close()
                    }
                }.getOrDefault(false)
            } else {
                val creds = HapCredentials.parse(stored)
                val target = discovery.resolveByName(name)
                    ?: credentialsRepository.loadEndpoint(name)?.asDiscovered(name)
                target != null && runCatching {
                    val transport = SocketTransport.connect(target.host, target.port)
                    val conn = CompanionConnection(transport)
                    try {
                        conn.start()
                        PairVerify(conn, creds).verify()
                        true
                    } finally {
                        conn.close()
                    }
                }.getOrDefault(false)
            }
            setVerify(name, if (ok) DeviceVerify.Ok else DeviceVerify.Failed)
        }
    }

    private fun setVerify(name: String, state: DeviceVerify) {
        deviceVerify.value = deviceVerify.value + (name to state)
    }

    fun closeSettings() {
        // Return to wherever Settings was opened from (device list or remote).
        screen.value = settingsReturnTo
    }

    fun forgetDeviceByName(name: String) {
        viewModelScope.launch {
            credentialsRepository.load(name)?.let(::parseAndroidCredential)?.let {
                AndroidTvRemoteClient.deleteIdentity(getApplication(), it.alias)
            }
            credentialsRepository.delete(name)
            pairedDevices.value = credentialsRepository.pairedDeviceNames().sorted()
            deviceList.value = deviceList.value.copy(
                devices = deviceList.value.devices.filterNot { it.name == name },
                pairedNames = deviceList.value.pairedNames - name,
            )
            if (defaultDeviceName.value == name) {
                val replacement = pairedDevices.value.firstOrNull()
                defaultDeviceName.value = replacement
                settingsRepository.setDefaultDeviceName(replacement)
            }
        }
    }

    fun startScan() {
        if (deviceList.value.scanning) return
        viewModelScope.launch {
            val pairedNames = credentialsRepository.pairedDeviceNames().toSet()
            deviceList.value = deviceList.value.copy(scanning = true, pairedNames = pairedNames)
            kotlinx.coroutines.coroutineScope {
                launch {
                    runCatching {
                        discovery.scan(durationMs = 6_000) { addDiscoveredDevice(it) }
                    }
                }
                launch {
                    runCatching {
                        androidTvDiscovery.scan(durationMs = 6_000) { addDiscoveredDevice(it) }
                    }
                }
            }
            deviceList.value = deviceList.value.copy(scanning = false)
            maybeOpenStartupRemote()
            if (screen.value is Screen.Startup) screen.value = Screen.DeviceList
        }
    }

    private suspend fun restoreSavedEndpoints() {
        val names = credentialsRepository.endpointDeviceNames()
        val restored = names.mapNotNull { name ->
            credentialsRepository.loadEndpoint(name)?.asDiscovered(name)
        }
        val pairedNames = credentialsRepository.pairedDeviceNames().toSet()
        val current = deviceList.value
        val merged = current.devices + restored.filter { saved ->
            current.devices.none { it.name == saved.name && it.platform == saved.platform }
        }
        deviceList.value = current.copy(devices = merged, pairedNames = pairedNames)
    }

    private fun addDiscoveredDevice(device: DiscoveredAtv) {
        val current = deviceList.value
        val sameEndpoint = current.devices.firstOrNull {
            it.platform == device.platform && it.host == device.host
        }
        if (sameEndpoint != null) {
            if (sameEndpoint.direct && !device.direct) {
                // A discovered result can refresh a manually saved Apple TV
                // port while retaining the user's stable device name.
                val refreshed = sameEndpoint.copy(
                    port = device.port,
                    pairingPort = device.pairingPort ?: sameEndpoint.pairingPort,
                    model = device.model ?: sameEndpoint.model,
                )
                if (refreshed != sameEndpoint) {
                    deviceList.value = current.copy(
                        devices = current.devices.map {
                            if (it === sameEndpoint) refreshed else it
                        },
                    )
                    viewModelScope.launch {
                        credentialsRepository.saveEndpoint(
                            refreshed.name,
                            SavedDeviceEndpoint(
                                refreshed.platform,
                                refreshed.host,
                                refreshed.port,
                                refreshed.pairingPort,
                            ),
                        )
                    }
                }
            }
            return
        }
        if (current.devices.none { it.name == device.name && it.platform == device.platform }) {
            deviceList.value = current.copy(devices = current.devices + device)
        }
    }

    fun addManualDevice(
        host: String,
        port: Int,
        platform: TvPlatform = TvPlatform.AppleTv,
        displayName: String? = null,
    ) {
        val cleanHost = host.trim().removePrefix("[").removeSuffix("]")
        if (cleanHost.isBlank() || port !in 1..65_535) return
        val device = DiscoveredAtv(
            name = displayName?.trim()?.takeIf { it.isNotBlank() } ?: manualDeviceName(cleanHost, port),
            host = cleanHost,
            port = port,
            model = null,
            platform = platform,
            pairingPort = if (platform == TvPlatform.AndroidTv) 6467 else null,
            direct = true,
        )
        addDiscoveredDevice(device)
        selectDevice(device)
    }

    fun selectDevice(device: DiscoveredAtv) {
        startupRoutingPending = false
        viewModelScope.launch {
            credentialsRepository.saveEndpoint(
                device.name,
                SavedDeviceEndpoint(device.platform, device.host, device.port, device.pairingPort),
            )
            val stored = credentialsRepository.load(device.name)
            if (device.platform == TvPlatform.AndroidTv) {
                val credential = stored?.let(::parseAndroidCredential)
                if (credential != null) {
                    ensureDefaultForSinglePairedDevice(device.name)
                    openAndroidRemote(device, credential)
                } else {
                    beginPairing(device)
                }
            } else if (stored != null) {
                ensureDefaultForSinglePairedDevice(device.name)
                openRemote(device, HapCredentials.parse(stored))
            } else {
                beginPairing(device)
            }
        }
    }

    fun forgetDevice(device: DiscoveredAtv) {
        viewModelScope.launch {
            credentialsRepository.load(device.name)?.let(::parseAndroidCredential)?.let {
                AndroidTvRemoteClient.deleteIdentity(getApplication(), it.alias)
            }
            credentialsRepository.delete(device.name)
            val remainingPaired = credentialsRepository.pairedDeviceNames().sorted()
            pairedDevices.value = remainingPaired
            deviceList.value = deviceList.value.copy(
                devices = deviceList.value.devices.filterNot {
                    it.name == device.name && it.platform == device.platform
                },
                pairedNames = remainingPaired.toSet(),
            )
            if (defaultDeviceName.value == device.name) {
                val replacement = remainingPaired.firstOrNull()
                defaultDeviceName.value = replacement
                settingsRepository.setDefaultDeviceName(replacement)
            }
        }
    }

    // Pairing

    private suspend fun beginPairing(device: DiscoveredAtv) {
        screen.value = Screen.Pairing(device)
        pairing.value = PairingUi(working = true)
        if (device.platform == TvPlatform.AndroidTv) {
            val alias = "androidtv_${UUID.randomUUID().toString().replace("-", "")}"
            val session = AndroidTvPairingSession(
                getApplication(),
                device.host,
                device.pairingPort ?: 6467,
                alias,
            )
            androidPairing = session
            try {
                session.begin()
                pairing.value = PairingUi(awaitingPin = true, working = false)
            } catch (e: Exception) {
                session.cancel()
                androidPairing = null
                pairing.value = PairingUi(working = false, error = friendlyError(e))
            }
            return
        }
        try {
            val transport = SocketTransport.connect(device.host, device.port)
            val connection = CompanionConnection(transport)
            connection.start()
            pairingConnection = connection
            val setup = PairSetup(connection, name = "LocalBeam")
            setup.startPairing()
            pairSetup = setup
            pairing.value = PairingUi(awaitingPin = true, working = false)
        } catch (e: Exception) {
            pairing.value = PairingUi(working = false, error = friendlyError(e))
        }
    }

    fun submitPin(pin: String) {
        val device = (screen.value as? Screen.Pairing)?.device ?: return
        if (device.platform == TvPlatform.AndroidTv) {
            val session = androidPairing ?: return
            viewModelScope.launch {
                pairing.value = pairing.value.copy(working = true, error = null)
                try {
                    val credential = session.finish(pin)
                    credentialsRepository.save(device.name, ANDROID_CREDENTIAL_PREFIX + credential)
                    ensureDefaultForSinglePairedDevice(device.name)
                    androidPairing = null
                    openAndroidRemote(device, parseAndroidCredential(ANDROID_CREDENTIAL_PREFIX + credential)!!)
                } catch (e: Exception) {
                    session.cancel()
                    androidPairing = null
                    pairing.value = PairingUi(working = false, error = friendlyError(e))
                }
            }
            return
        }
        val setup = pairSetup ?: return
        viewModelScope.launch {
            pairing.value = pairing.value.copy(working = true, error = null)
            try {
                val credentials = setup.finishPairing(pin)
                credentialsRepository.save(device.name, credentials.toString())
                ensureDefaultForSinglePairedDevice(device.name)
                pairingConnection?.close()
                pairingConnection = null
                pairSetup = null
                openRemote(device, credentials)
            } catch (e: Exception) {
                pairing.value = PairingUi(working = false, error = friendlyError(e))
                pairingConnection?.close()
                pairingConnection = null
                pairSetup = null
            }
        }
    }

    fun cancelPairing() {
        pairingConnection?.close()
        pairingConnection = null
        pairSetup = null
        androidPairing?.cancel()
        androidPairing = null
        screen.value = Screen.DeviceList
    }

    // Remote / connection lifecycle

    private fun openRemote(device: DiscoveredAtv, credentials: HapCredentials) {
        activeDeviceName.value = device.name
        apps.value = null
        appsError.value = null
        keyboardFocus.value = KeyboardFocusState.Unknown
        keyboardText.value = ""
        screen.value = Screen.Remote(device)
        connect(device, credentials)
    }

    private fun openAndroidRemote(device: DiscoveredAtv, credential: AndroidTvCredential) {
        activeDeviceName.value = device.name
        apps.value = null
        appsError.value = null
        keyboardFocus.value = KeyboardFocusState.Unknown
        keyboardText.value = ""
        screen.value = Screen.Remote(device)
        connectAndroid(device, credential)
    }

    private fun connect(device: DiscoveredAtv, credentials: HapCredentials) {
        val generation = ++connectionGeneration
        reconnectJob?.cancel()
        client?.close()
        client = null
        androidClient?.close()
        androidClient = null
        reconnectJob = viewModelScope.launch {
            connectionState.value = ConnectionState.Connecting
            connectionError.value = null
            // Transient "ws error" right after opening the app is common (the
            // ATV's port rotates, Wi-Fi just woke, etc). Retry a few times,
            // half a second apart, staying in the Connecting state; only
            // surface the error + manual Reconnect after all attempts fail.
            var lastError: Exception? = null
            repeat(RECONNECT_ATTEMPTS) { attempt ->
                if (attempt > 0) delay(RECONNECT_DELAY_MS)
                // The Companion port changes across reboots: re-resolve first,
                // falling back to the last known host/port (manual entry).
                var candidate: CompanionClient? = null
                try {
                    val target = if (device.direct) {
                        device
                    } else {
                        discovery.resolveByName(device.name) ?: device
                    }
                    val transport = SocketTransport.connect(target.host, target.port)
                    val connection = CompanionConnection(transport)
                    val newClient = CompanionClient(connection, credentials)
                    candidate = newClient
                    connection.onDisconnected = { error ->
                        viewModelScope.launch {
                            handleRemoteFailure(error, newClient, generation)
                        }
                    }
                    // Publish before the handshake completes so an EOF during
                    // verification cannot be mistaken for a healthy session.
                    client = newClient
                    newClient.connect()
                    if (generation != connectionGeneration) {
                        candidate?.close()
                        return@launch
                    }
                    check(client === newClient) { "connection closed during verification" }
                    candidate = null
                    connectionState.value = ConnectionState.Connected
                    observeKeyboard(newClient)
                    consumeTouchEvents(newClient, generation)
                    return@launch
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    // Pair-verify can fail after the socket has been opened.
                    // Always close that attempt before retrying.
                    candidate?.close()
                    client = null
                    lastError = e
                }
            }
            if (generation != connectionGeneration) return@launch
            connectionState.value = ConnectionState.Disconnected
            connectionError.value = friendlyError(lastError ?: java.io.IOException("connect failed"))
        }
    }

    /** Rename a saved TV without losing its encrypted credentials or endpoint. */
    fun renameDeviceByName(oldName: String, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isBlank() || trimmed == oldName) return
        viewModelScope.launch {
            if (!credentialsRepository.rename(oldName, trimmed)) return@launch
            pairedDevices.value = credentialsRepository.pairedDeviceNames().sorted()
            deviceList.value = deviceList.value.copy(
                devices = deviceList.value.devices.map { device ->
                    if (device.name == oldName) device.copy(name = trimmed) else device
                },
                pairedNames = deviceList.value.pairedNames
                    .map { if (it == oldName) trimmed else it }
                    .toSet(),
            )
            if (activeDeviceName.value == oldName) activeDeviceName.value = trimmed
            if (defaultDeviceName.value == oldName) {
                defaultDeviceName.value = trimmed
                settingsRepository.setDefaultDeviceName(trimmed)
            }
            val renamedReturn = when (val current = settingsReturnTo) {
                is Screen.Pairing -> if (current.device.name == oldName) {
                    Screen.Pairing(current.device.copy(name = trimmed))
                } else current
                is Screen.Remote -> if (current.device.name == oldName) {
                    Screen.Remote(current.device.copy(name = trimmed))
                } else current
                else -> current
            }
            settingsReturnTo = renamedReturn
            screen.value = when (val current = screen.value) {
                is Screen.Pairing -> if (current.device.name == oldName) {
                    Screen.Pairing(current.device.copy(name = trimmed))
                } else current
                is Screen.Remote -> if (current.device.name == oldName) {
                    Screen.Remote(current.device.copy(name = trimmed))
                } else current
                else -> current
            }
        }
    }

    private fun connectAndroid(device: DiscoveredAtv, credential: AndroidTvCredential) {
        val generation = ++connectionGeneration
        reconnectJob?.cancel()
        client?.close()
        client = null
        androidClient?.close()
        androidClient = null
        reconnectJob = viewModelScope.launch {
            connectionState.value = ConnectionState.Connecting
            connectionError.value = null
            var lastError: Exception? = null
            repeat(RECONNECT_ATTEMPTS) { attempt ->
                if (attempt > 0) delay(RECONNECT_DELAY_MS)
                var candidate: AndroidTvRemoteClient? = null
                try {
                    val target = if (device.direct) {
                        device
                    } else {
                        androidTvDiscovery.resolveByName(device.name) ?: device
                    }
                    val newClient = AndroidTvRemoteClient(
                        getApplication(),
                        target.host,
                        credential.alias,
                        credential.fingerprint,
                    )
                    candidate = newClient
                    newClient.onDisconnected = { error ->
                        viewModelScope.launch {
                            handleAndroidDisconnect(error, newClient, generation)
                        }
                    }
                    // Publish before the first configure/ping response so a
                    // fast remote close cannot leave a stale Connected label.
                    androidClient = newClient
                    newClient.connect()
                    if (generation != connectionGeneration) {
                        newClient.close()
                        return@launch
                    }
                    check(androidClient === newClient) { "connection closed during verification" }
                    connectionState.value = ConnectionState.Connected
                    observeAndroidKeyboard(newClient)
                    return@launch
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    // The client is local to this attempt until connect succeeds.
                    // Close it explicitly so a failed TLS handshake cannot leak its socket.
                    candidate?.close()
                    androidClient?.close()
                    androidClient = null
                    lastError = e
                }
            }
            if (generation != connectionGeneration) return@launch
            connectionState.value = ConnectionState.Disconnected
            connectionError.value = friendlyError(lastError ?: java.io.IOException("connect failed"))
        }
    }

    /** Opens the saved default, or the only paired TV, on a fresh app start. */
    private suspend fun maybeOpenStartupRemote() {
        if (!startupRoutingPending) return
        if (screen.value !is Screen.DeviceList && screen.value !is Screen.Startup) return
        val pairedNames = credentialsRepository.pairedDeviceNames().sorted()
        if (pairedNames.isEmpty()) return

        val savedDefault = settingsRepository.defaultDeviceName.first()
        val targetName = when {
            savedDefault != null && savedDefault in pairedNames -> savedDefault
            pairedNames.size == 1 -> pairedNames.single()
            else -> return
        }
        val device = deviceList.value.devices.firstOrNull { it.name == targetName } ?: return
        val stored = credentialsRepository.load(targetName) ?: return

        startupRoutingPending = false
        if (pairedNames.size == 1 && savedDefault != targetName) {
            defaultDeviceName.value = targetName
            settingsRepository.setDefaultDeviceName(targetName)
        }
        if (device.platform == TvPlatform.AndroidTv) {
            parseAndroidCredential(stored)?.let { openAndroidRemote(device, it) }
        } else {
            openRemote(device, HapCredentials.parse(stored))
        }
    }

    private suspend fun hasPotentialStartupRemote(): Boolean {
        val pairedNames = credentialsRepository.pairedDeviceNames().sorted()
        if (pairedNames.isEmpty()) return false
        val savedDefault = settingsRepository.defaultDeviceName.first()
        return pairedNames.size == 1 || (savedDefault != null && savedDefault in pairedNames)
    }

    private suspend fun ensureDefaultForSinglePairedDevice(name: String) {
        if (credentialsRepository.pairedDeviceNames().size != 1) return
        if (settingsRepository.defaultDeviceName.first() != null) return
        defaultDeviceName.value = name
        settingsRepository.setDefaultDeviceName(name)
    }

    fun reconnect() {
        val device = (screen.value as? Screen.Remote)?.device ?: return
        connectionState.value = ConnectionState.Connecting
        connectionError.value = null
        viewModelScope.launch {
            try {
                val stored = credentialsRepository.load(device.name)
                    ?: error("Saved TV credentials are missing")
                if (device.platform == TvPlatform.AndroidTv) {
                    val credential = parseAndroidCredential(stored)
                        ?: error("Saved Android TV credentials are invalid")
                    connectAndroid(device, credential)
                } else {
                    connect(device, HapCredentials.parse(stored))
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                connectionState.value = ConnectionState.Disconnected
                connectionError.value = friendlyError(e)
            }
        }
    }

    /** Called when the remote screen returns to the foreground. */
    fun onForeground() {
        if (screen.value is Screen.Remote && connectionState.value == ConnectionState.Disconnected) {
            reconnect()
        }
    }

    fun closeRemote() {
        startupRoutingPending = false
        reconnectJob?.cancel()
        connectionGeneration++
        val current = client
        client = null
        val currentAndroid = androidClient
        androidClient = null
        connectionState.value = ConnectionState.Disconnected
        keyboardFocus.value = KeyboardFocusState.Unknown
        keyboardText.value = ""
        activeDeviceName.value = null
        viewModelScope.launch { runCatching { current?.disconnect() } }
        currentAndroid?.close()
        screen.value = Screen.DeviceList
    }

    /** Run a remote-control action, flipping to Disconnected on I/O errors. */
    fun withClient(block: suspend (CompanionClient) -> Unit) {
        val current = client ?: return
        val generation = connectionGeneration
        viewModelScope.launch {
            try {
                block(current)
            } catch (e: Exception) {
                handleRemoteFailure(e, current, generation)
            }
        }
    }

    private fun handleRemoteFailure(error: Throwable, source: Any? = null, generation: Long? = null) {
        if (generation != null && generation != connectionGeneration) return
        if (source != null && source !== client && source !== androidClient) return
        val current = client
        client = null
        val currentAndroid = androidClient
        androidClient = null
        touchJob?.cancel()
        keyboardFocusJob?.cancel()
        androidKeyboardTextJob?.cancel()
        textSyncJob?.cancel()
        keyboardFocus.value = KeyboardFocusState.Unknown
        keyboardText.value = ""
        current?.close()
        currentAndroid?.close()
        connectionState.value = ConnectionState.Disconnected
        connectionError.value = friendlyError(error as? Exception ?: Exception(error))
    }

    /**
     * Some Android TV Remote Service builds close the control socket while
     * handling an app-link launch. Reconnect that short-lived transport event
     * instead of exposing the raw EOFException or leaving the UI stale.
     */
    private fun handleAndroidDisconnect(
        error: Throwable,
        source: AndroidTvRemoteClient,
        generation: Long,
    ) {
        val elapsed = System.currentTimeMillis() - lastAndroidAppLaunchAt
        val isExpectedAppLaunchClose =
            generation == connectionGeneration &&
                source === androidClient &&
                elapsed in 0..APP_LAUNCH_RECOVERY_WINDOW_MS &&
                error is java.io.IOException
        handleRemoteFailure(error, source, generation)
        if (isExpectedAppLaunchClose) reconnect()
    }

    fun pressButton(command: HidCommand) {
        val currentAndroid = androidClient
        if (currentAndroid != null) {
            viewModelScope.launch {
                runCatching { currentAndroid.pressButton(command) }
                    .onFailure { handleRemoteFailure(it, currentAndroid, connectionGeneration) }
            }
        } else {
            withClient { it.pressButton(command) }
        }
    }

    fun pressAndroidButton(command: AndroidTvButton) {
        val currentAndroid = androidClient ?: return
        viewModelScope.launch {
            runCatching { currentAndroid.pressButton(command) }
                .onFailure { handleRemoteFailure(it, currentAndroid, connectionGeneration) }
        }
    }

    fun holdButton(command: HidCommand) {
        val currentAndroid = androidClient
        if (currentAndroid != null) {
            viewModelScope.launch {
                runCatching { currentAndroid.holdButton(command) }
                    .onFailure { handleRemoteFailure(it, currentAndroid, connectionGeneration) }
            }
        } else {
            withClient { it.holdButton(command) }
        }
    }

    // Keyboard (M6): mirror the phone's edit buffer to the TV field

    /** Refresh the TV-side text session without requiring a fresh focus event. */
    fun requestKeyboardFocus() {
        val currentAndroid = androidClient
        if (currentAndroid != null) {
            keyboardFocus.value = currentAndroid.keyboardFocus.value
            return
        }
        val current = client ?: return
        val generation = connectionGeneration
        viewModelScope.launch {
            runCatching { current.textGet() }
                .onSuccess { value ->
                    if (value != null) {
                        keyboardText.value = value
                        keyboardFocus.value = KeyboardFocusState.Focused
                    }
                }
                .onFailure { handleRemoteFailure(it, current, generation) }
        }
    }

    private fun observeKeyboard(newClient: CompanionClient) {
        keyboardFocusJob?.cancel()
        androidKeyboardTextJob?.cancel()
        keyboardFocusJob = viewModelScope.launch {
            newClient.keyboardFocus.collect { state ->
                keyboardFocus.value = state
                if (state == KeyboardFocusState.Focused) {
                    // Pre-fill the edit buffer with what's already in the field
                    runCatching { newClient.textGet() }
                        .onSuccess { value -> value?.let { keyboardText.value = it } }
                        .onFailure { handleRemoteFailure(it, newClient, connectionGeneration) }
                }
            }
        }
    }

    private fun observeAndroidKeyboard(newClient: AndroidTvRemoteClient) {
        keyboardFocusJob?.cancel()
        androidKeyboardTextJob?.cancel()
        keyboardFocusJob = viewModelScope.launch {
            newClient.keyboardFocus.collect { keyboardFocus.value = it }
        }
        androidKeyboardTextJob = viewModelScope.launch {
            newClient.keyboardText.collect { keyboardText.value = it }
        }
    }

    /**
     * Called on every phone-side keystroke. The whole current string is sent
     * (replace semantics) after a short debounce — the simplest reliable way
     * to keep both sides in sync.
     */
    fun onKeyboardTextChanged(text: String) {
        keyboardText.value = text
        textSyncJob?.cancel()
        textSyncJob = viewModelScope.launch {
            delay(250)
            val currentAndroid = androidClient
            if (currentAndroid != null) {
                runCatching { currentAndroid.textSet(text) }
                    .onFailure { handleRemoteFailure(it, currentAndroid, connectionGeneration) }
                return@launch
            }
            val current = client ?: return@launch
            val generation = connectionGeneration
            runCatching { current.textSet(text) }
                .onFailure { handleRemoteFailure(it, current, generation) }
        }
    }

    fun clearKeyboardText() {
        keyboardText.value = ""
        textSyncJob?.cancel()
        val currentAndroid = androidClient
        if (currentAndroid != null) {
            viewModelScope.launch {
                runCatching { currentAndroid.textClear() }
                    .onFailure { handleRemoteFailure(it, currentAndroid, connectionGeneration) }
            }
        } else {
            withClient { it.textClear() }
        }
    }

    /**
     * Voice dictation result: replace the focused TV field with [text]
     * immediately (no debounce). A no-op on the TV side when nothing is
     * focused — the UI nudges the user to focus a field first.
     */
    fun dictateText(text: String) {
        if (text.isBlank()) return
        keyboardText.value = text
        textSyncJob?.cancel()
        val currentAndroid = androidClient
        if (currentAndroid != null) {
            viewModelScope.launch {
                runCatching { currentAndroid.textSet(text) }
                    .onFailure { handleRemoteFailure(it, currentAndroid, connectionGeneration) }
            }
        } else {
            withClient { it.textSet(text) }
        }
    }

    // Touchpad (M7)

    private fun consumeTouchEvents(newClient: CompanionClient, generation: Long) {
        touchJob?.cancel()
        touchJob = viewModelScope.launch {
            for ((x, y, phase) in touchEvents) {
                runCatching { newClient.touchEvent(x, y, phase) }
                    .onFailure { handleRemoteFailure(it, newClient, generation) }
            }
        }
    }

    /** Queue a touch event; Hold events are throttled to ~16 ms like pyatv. */
    fun sendTouch(x: Long, y: Long, phase: TouchPhase) {
        if (phase == TouchPhase.Hold) {
            val now = System.currentTimeMillis()
            if (now - lastHoldSentAt < 16) return
            lastHoldSentAt = now
        }
        touchEvents.trySend(Triple(x, y, phase))
    }

    fun touchTap() {
        val currentAndroid = androidClient
        if (currentAndroid != null) {
            viewModelScope.launch {
                runCatching { currentAndroid.touchTap() }
                    .onFailure { handleRemoteFailure(it, currentAndroid, connectionGeneration) }
            }
        } else {
            withClient { it.tap() }
        }
    }

    // Apps (M7)

    fun loadApps(force: Boolean = false) {
        if (apps.value != null && !force) return
        appsError.value = null
        val currentAndroid = androidClient
        if (currentAndroid != null) {
            apps.value = currentAndroid.appList().toList()
                .sortedBy { it.second.lowercase() }
            return
        }
        withClient { current ->
            try {
                apps.value = current.appList().toList().sortedBy { it.second.lowercase() }
            } catch (e: Exception) {
                appsError.value = e.message
                throw e
            }
        }
    }

    fun launchApp(bundleId: String) {
        val currentAndroid = androidClient
        if (currentAndroid != null) {
            lastAndroidAppLaunchAt = System.currentTimeMillis()
            viewModelScope.launch {
                runCatching { currentAndroid.launchApp(bundleId) }
                    .onFailure { handleAndroidDisconnect(it, currentAndroid, connectionGeneration) }
            }
        } else {
            withClient { it.launchApp(bundleId) }
        }
    }

    fun wake() {
        val currentAndroid = androidClient
        if (currentAndroid != null) {
            viewModelScope.launch {
                runCatching { currentAndroid.wake() }
                    .onFailure { handleRemoteFailure(it, currentAndroid, connectionGeneration) }
            }
        } else {
            withClient { it.wake() }
        }
    }

    fun sleep() {
        val currentAndroid = androidClient
        if (currentAndroid != null) {
            viewModelScope.launch {
                runCatching { currentAndroid.sleep() }
                    .onFailure { handleRemoteFailure(it, currentAndroid, connectionGeneration) }
            }
        } else {
            withClient { it.sleep() }
        }
    }

    private fun friendlyError(e: Exception): String = when {
        e.message?.contains("proof mismatch") == true -> strings.wrongPin
        e.message?.contains("ECONNREFUSED") == true || e is java.net.ConnectException -> strings.atvUnreachable
        e is java.net.SocketTimeoutException -> strings.connectionTimedOut
        e is java.io.EOFException || e.message?.contains("EOF", ignoreCase = true) == true -> strings.connectionLost
        else -> e.message ?: e.javaClass.simpleName
    }

    override fun onCleared() {
        pairingConnection?.close()
        androidPairing?.cancel()
        client?.close()
        androidClient?.close()
    }

    private companion object {
        const val ANDROID_CREDENTIAL_PREFIX = "androidtv|"
        const val RECONNECT_ATTEMPTS = 3
        const val RECONNECT_DELAY_MS = 500L
        const val APP_LAUNCH_RECOVERY_WINDOW_MS = 5_000L
    }
}

private data class AndroidTvCredential(val alias: String, val fingerprint: String?)

private fun SavedDeviceEndpoint.asDiscovered(name: String): DiscoveredAtv = DiscoveredAtv(
    name = name,
    host = host,
    port = port,
    model = null,
    platform = platform,
    pairingPort = pairingPort,
    direct = true,
)

private fun manualDeviceName(host: String, port: Int): String {
    val displayHost = if (host.contains(':') && !host.startsWith('[')) "[$host]" else host
    return "$displayHost:$port"
}

private fun parseAndroidCredential(value: String): AndroidTvCredential? {
    if (!value.startsWith("androidtv|")) return null
    val parts = value.removePrefix("androidtv|").split('|', limit = 2)
    if (parts[0].isBlank()) return null
    return AndroidTvCredential(parts[0], parts.getOrNull(1)?.takeIf { it.isNotBlank() })
}
