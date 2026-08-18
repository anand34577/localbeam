package dev.companionremote.app.androidtv

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.content.Context
import android.security.KeyPairGeneratorSpec
import dev.companionremote.protocol.client.HidCommand
import dev.companionremote.protocol.client.KeyboardFocusState
import java.io.DataInputStream
import java.io.IOException
import java.math.BigInteger
import java.net.InetSocketAddress
import java.net.Socket
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.Principal
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Calendar
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.KeyManager
import javax.net.ssl.SSLPeerUnverifiedException
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509KeyManager
import javax.net.ssl.X509TrustManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Android-only system buttons that do not have Apple Companion HID codes. */
enum class AndroidTvButton(internal val keyCode: Int) {
    Mute(164),
    Settings(176),
    TvInput(178),
    MediaStop(86),
    MediaRewind(89),
    MediaFastForward(90),
}

/** Package IDs understood by Android TV Remote Service app-link launches. */
object AndroidTvApps {
    const val ZEE5 = "com.graymatrix.did"
    const val YOUTUBE = "com.google.android.youtube.tv"
    const val PRIME_VIDEO = "com.amazon.amazonvideo.livingroom"
    const val JIOHOTSTAR = "in.startv.hotstar"

    val knownApps: Map<String, String> = linkedMapOf(
        YOUTUBE to "YouTube",
        "com.netflix.ninja" to "Netflix",
        "com.disney.disneyplus" to "Disney+",
        PRIME_VIDEO to "Prime Video",
        ZEE5 to "ZEE5",
        JIOHOTSTAR to "JioHotstar",
        "com.android.vending" to "Play Store",
    )
}

/** A local Android TV Remote v2 pairing session. */
class AndroidTvPairingSession(
    context: Context,
    private val host: String,
    private val port: Int,
    val alias: String,
) {
    private val appContext = context.applicationContext
    private var socket: SSLSocket? = null

    suspend fun begin() = withContext(Dispatchers.IO) {
        AndroidTvIdentity.ensure(appContext, alias)
        val connected = AndroidTvIdentity.openSocket(host, port, alias, null)
        connected.soTimeout = SOCKET_TIMEOUT_MS
        socket = connected
        val input = DataInputStream(connected.inputStream)
        write(connected, AndroidTvPairingCodec.request("atvremote", androidTvClientName(alias)))
        while (true) {
            when (AndroidTvPairingCodec.read(readFrame(input))) {
                PairingMessage.RequestAck -> write(connected, AndroidTvPairingCodec.option())
                PairingMessage.Option -> write(connected, AndroidTvPairingCodec.configuration())
                PairingMessage.ConfigurationAck -> return@withContext Unit
                PairingMessage.SecretAck -> error("Android TV sent a pairing secret too early")
            }
        }
    }

    suspend fun finish(pin: String): String = withContext(Dispatchers.IO) {
        require(pin.length == 6 && pin.all { it in "0123456789abcdefABCDEF" }) {
            "Android TV pairing code must be six hexadecimal characters"
        }
        val connected = socket ?: error("Android TV pairing session is not active")
        val clientCertificate = AndroidTvIdentity.certificate(alias)
        val serverCertificate = connected.session.peerCertificates.firstOrNull() as? X509Certificate
            ?: error("Android TV did not provide a server certificate")
        val secret = pairingSecret(clientCertificate, serverCertificate, pin)
        write(connected, AndroidTvPairingCodec.secret(secret))
        when (AndroidTvPairingCodec.read(readFrame(DataInputStream(connected.inputStream)))) {
            PairingMessage.SecretAck -> Unit
            else -> error("Android TV rejected the pairing code")
        }
        val fingerprint = AndroidTvIdentity.fingerprint(serverCertificate)
        connected.close()
        socket = null
        "$alias|$fingerprint"
    }

    fun cancel() {
        runCatching { socket?.close() }
        socket = null
        AndroidTvIdentity.delete(appContext, alias)
    }

    private fun write(socket: SSLSocket, data: ByteArray) {
        synchronized(socket.outputStream) {
            socket.outputStream.write(data)
            socket.outputStream.flush()
        }
    }

    companion object {
        private const val SOCKET_TIMEOUT_MS = 15_000

        private fun pairingSecret(
            client: X509Certificate,
            server: X509Certificate,
            pin: String,
        ): ByteArray {
            val digest = MessageDigest.getInstance("SHA-256")
            digest.update(rsaModulus(client))
            digest.update(rsaExponent(client))
            digest.update(rsaModulus(server))
            digest.update(rsaExponent(server))
            digest.update(pin.substring(2).hexBytes())
            val result = digest.digest()
            check((result[0].toInt() and 0xff) == pin.substring(0, 2).toInt(16)) {
                "Android TV pairing code does not match the certificate challenge"
            }
            return result
        }

        private fun rsaModulus(certificate: X509Certificate): ByteArray {
            val key = certificate.publicKey as? java.security.interfaces.RSAPublicKey
                ?: error("Android TV pairing requires an RSA certificate")
            return key.modulus.toString(16).evenHexBytes()
        }

        private fun rsaExponent(certificate: X509Certificate): ByteArray {
            val key = certificate.publicKey as? java.security.interfaces.RSAPublicKey
                ?: error("Android TV pairing requires an RSA certificate")
            return ("0" + key.publicExponent.toString(16)).evenHexBytes()
        }
    }
}

/** Android TV Remote v2 TLS client. All traffic is direct to the TV's LAN IP. */
class AndroidTvRemoteClient(
    context: Context,
    private val host: String,
    private val alias: String,
    private val expectedServerFingerprint: String? = null,
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(Job() + Dispatchers.IO)
    private val writeMutex = Mutex()
    private val voiceMutex = Mutex()
    private var socket: SSLSocket? = null
    private var readerJob: Job? = null
    private var ready = CompletableDeferred<Unit>()
    @Volatile
    private var locallyClosed = false
    private var imeCounter = 0
    private var fieldCounter = 0
    private var activeFeatures = DEFAULT_FEATURES
    private var voiceSessionId = -1
    private var voiceSessionCounter = 0
    @Volatile
    private var voiceRecorder: AudioRecord? = null
    private var voiceJob: Job? = null

    private val _keyboardFocus = MutableStateFlow(KeyboardFocusState.Unknown)
    val keyboardFocus: StateFlow<KeyboardFocusState> = _keyboardFocus
    private val _keyboardText = MutableStateFlow("")
    val keyboardText: StateFlow<String> = _keyboardText
    private val _keyboardOpenRequest = MutableStateFlow(0L)
    val keyboardOpenRequest: StateFlow<Long> = _keyboardOpenRequest
    private val _currentApp = MutableStateFlow<String?>(null)
    val currentApp: StateFlow<String?> = _currentApp
    private val _voiceActive = MutableStateFlow(false)
    val voiceActive: StateFlow<Boolean> = _voiceActive

    var onDisconnected: ((Throwable) -> Unit)? = null

    suspend fun connect() = withContext(Dispatchers.IO) {
        locallyClosed = false
        activeFeatures = DEFAULT_FEATURES
        require(!expectedServerFingerprint.isNullOrBlank()) {
            "Android TV credentials are missing the server certificate fingerprint"
        }
        AndroidTvIdentity.ensure(appContext, alias)
        val connected = AndroidTvIdentity.openSocket(
            host,
            REMOTE_PORT,
            alias,
            expectedServerFingerprint,
        )
        // SOCKET_TIMEOUT_MS is only for the initial configure handshake. An
        // idle TV is still a healthy connection; using that timeout for the
        // long-lived reader caused false disconnects every 15 seconds.
        connected.soTimeout = 0
        socket = connected
        ready = CompletableDeferred()
        val input = DataInputStream(connected.inputStream)
        readerJob = scope.launch {
            try {
                while (isActive) {
                    handle(AndroidTvRemoteCodec.read(readFrame(input)))
                }
            } catch (e: Exception) {
                _voiceActive.value = false
                if (!ready.isCompleted) ready.completeExceptionally(e)
                if (!locallyClosed) onDisconnected?.invoke(e)
            }
        }
        withTimeout(SOCKET_TIMEOUT_MS.toLong()) { ready.await() }
    }

    suspend fun pressButton(command: HidCommand) {
        androidKey(command)?.let { write(AndroidTvRemoteCodec.key(it, SHORT)) }
    }

    suspend fun pressButton(command: AndroidTvButton) {
        write(AndroidTvRemoteCodec.key(command.keyCode, SHORT))
    }

    suspend fun holdButton(command: HidCommand) {
        androidKey(command)?.let {
            write(AndroidTvRemoteCodec.key(it, START_LONG))
            delay(700)
            write(AndroidTvRemoteCodec.key(it, END_LONG))
        }
    }

    suspend fun textSet(text: String) {
        // RemoteImeBatchEdit inserts at the TV's current cursor; it does not
        // replace the whole field. Mirror the editor by sending only the
        // newly appended suffix, or clear the old value with DEL before a
        // non-prefix replacement.
        val previous = _keyboardText.value
        if (text.startsWith(previous)) {
            val suffix = text.substring(previous.length)
            if (suffix.isNotEmpty()) {
                write(AndroidTvRemoteCodec.text(imeCounter, fieldCounter, suffix))
            }
        } else {
            val deleteCount = previous.codePointCount(0, previous.length)
            repeat(deleteCount) { write(AndroidTvRemoteCodec.key(KEYCODE_DELETE, SHORT)) }
            if (text.isNotEmpty()) {
                write(AndroidTvRemoteCodec.text(imeCounter, fieldCounter, text))
            }
        }
        _keyboardText.value = text
    }

    suspend fun textClear() = textSet("")

    suspend fun touchTap() = pressButton(HidCommand.Select)

    suspend fun launchApp(link: String) {
        val target = link.trim()
        require(target.isNotEmpty()) { "Android TV app link is empty" }
        // App shortcuts must send only the app-link request. A follow-up OK
        // would be interpreted by a playing app as a playback/select action.
        write(AndroidTvRemoteCodec.appLink(normalizeAndroidTvAppLink(target)))
    }

    /** Start a native Android TV voice session and stream microphone PCM. */
    suspend fun startVoice(): Boolean = voiceMutex.withLock {
        withContext(Dispatchers.IO) {
        if (appContext.checkSelfPermission(Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) return@withContext false
        if (_voiceActive.value) return@withContext true
        val minimumBuffer = AudioRecord.getMinBufferSize(
            VOICE_SAMPLE_RATE,
            VOICE_CHANNEL_CONFIG,
            VOICE_AUDIO_FORMAT,
        )
        if (minimumBuffer <= 0) return@withContext false
        val bufferSize = maxOf(minimumBuffer, VOICE_CHUNK_BYTES)
        val recorder = runCatching {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                VOICE_SAMPLE_RATE,
                VOICE_CHANNEL_CONFIG,
                VOICE_AUDIO_FORMAT,
                bufferSize,
            )
        }.getOrNull() ?: return@withContext false
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            return@withContext false
        }

        var sessionId = -1
        try {
            // Android TV opens the voice overlay after the search key. The
            // short pause matches the proven Remote v2 client flow.
            write(AndroidTvRemoteCodec.key(KEYCODE_SEARCH, SHORT))
            delay(200)
            sessionId = (++voiceSessionCounter).coerceAtLeast(1)
            write(AndroidTvRemoteCodec.voiceBegin(sessionId))
            recorder.startRecording()
            voiceSessionId = sessionId
            voiceRecorder = recorder
            _voiceActive.value = true
            voiceJob = scope.launch(Dispatchers.IO) {
                val buffer = ByteArray(VOICE_CHUNK_BYTES)
                try {
                    while (isActive) {
                        val count = recorder.read(buffer, 0, buffer.size)
                        if (count > 0) {
                            write(AndroidTvRemoteCodec.voicePayload(sessionId, buffer.copyOf(count)))
                        } else if (count == AudioRecord.ERROR_INVALID_OPERATION ||
                            count == AudioRecord.ERROR_BAD_VALUE
                        ) {
                            break
                        }
                    }
                } catch (error: Exception) {
                    if (error !is CancellationException && !locallyClosed) {
                        onDisconnected?.invoke(error)
                    }
                } finally {
                    runCatching { recorder.stop() }
                    recorder.release()
                    if (voiceSessionId == sessionId) {
                        voiceRecorder = null
                        _voiceActive.value = false
                    }
                }
            }
            true
        } catch (error: Exception) {
            runCatching { recorder.stop() }
            recorder.release()
            if (sessionId > 0) runCatching { write(AndroidTvRemoteCodec.voiceEnd(sessionId)) }
            false
        }
        }
    }

    /** Stop streaming and tell the TV to recognize the captured audio. */
    suspend fun stopVoice() = voiceMutex.withLock {
        withContext(Dispatchers.IO) {
        val sessionId = voiceSessionId
        voiceSessionId = -1
        _voiceActive.value = false
        // AudioRecord.read() is a blocking call. Stop the recorder before
        // cancelling the streaming coroutine so it can leave read() and send
        // RemoteVoiceEnd reliably, matching the working reference client.
        runCatching { voiceRecorder?.stop() }
        voiceJob?.cancelAndJoin()
        voiceJob = null
        voiceRecorder = null
        if (sessionId > 0 && !locallyClosed) {
            write(AndroidTvRemoteCodec.voiceEnd(sessionId))
        }
        }
    }

    suspend fun wake() = pressButton(HidCommand.Wake)

    suspend fun sleep() = pressButton(HidCommand.Sleep)

    /** Android TV Remote v2 cannot enumerate installed applications. */
    fun appList(): Map<String, String> = AndroidTvApps.knownApps

    fun close() {
        locallyClosed = true
        voiceSessionId = -1
        _voiceActive.value = false
        runCatching { voiceRecorder?.stop() }
        voiceJob?.cancel()
        voiceJob = null
        voiceRecorder = null
        readerJob?.cancel()
        scope.cancel()
        runCatching { socket?.close() }
        socket = null
    }

    private suspend fun handle(message: RemoteMessage) {
        when (message) {
            is RemoteMessage.Configure -> {
                // Only activate features advertised by this TV. In
                // particular, do not advertise app links to older Remote
                // Service builds that cannot handle the launch message.
                activeFeatures = if (message.supportedFeatures == 0) {
                    DEFAULT_FEATURES
                } else {
                    DEFAULT_FEATURES and message.supportedFeatures
                }
                write(AndroidTvRemoteCodec.configure(activeFeatures))
                if (!ready.isCompleted) ready.complete(Unit)
            }
            RemoteMessage.SetActive -> write(AndroidTvRemoteCodec.setActive(activeFeatures))
            is RemoteMessage.Ping -> write(AndroidTvRemoteCodec.pingResponse(message.value))
            is RemoteMessage.ImeBatch -> {
                imeCounter = message.imeCounter
                fieldCounter = message.fieldCounter
                // Some Android TV builds send the counter batch without a
                // separate ImeShow event. The counters are only meaningful
                // for an active text field, so treat them as authoritative
                // focus evidence as well.
                signalKeyboardFocus()
            }
            is RemoteMessage.ImeShow -> {
                signalKeyboardFocus()
                message.status?.let { _keyboardText.value = it.value }
            }
            is RemoteMessage.ImeKeyInject -> {
                // This packet also reports the foreground app. Only its
                // optional text-field status indicates keyboard focus; a
                // plain app-info update must not open the phone keyboard.
                message.packageName?.let { _currentApp.value = it }
                message.status?.let {
                    signalKeyboardFocus()
                    _keyboardText.value = it.value
                }
            }
            is RemoteMessage.Start -> Unit
            is RemoteMessage.Volume -> Unit
            // A rejected app link is an action-level error. Keep the control
            // socket alive so other remote buttons continue to work.
            RemoteMessage.Error -> Unit
            RemoteMessage.Unknown -> Unit
        }
    }

    private fun signalKeyboardFocus() {
        _keyboardFocus.value = KeyboardFocusState.Focused
        _keyboardOpenRequest.value += 1
    }

    private suspend fun write(data: ByteArray) = withContext(Dispatchers.IO) {
        val connected = socket ?: throw IOException("Android TV is not connected")
        writeMutex.withLock {
            connected.outputStream.write(data)
            connected.outputStream.flush()
        }
    }

    private fun androidKey(command: HidCommand): Int? = when (command) {
        HidCommand.Up -> 19
        HidCommand.Down -> 20
        HidCommand.Left -> 21
        HidCommand.Right -> 22
        HidCommand.Menu -> 4 // KEYCODE_BACK
        HidCommand.Select -> 23
        HidCommand.Home -> 3
        HidCommand.VolumeUp -> 24
        HidCommand.VolumeDown -> 25
        HidCommand.PlayPause -> 85
        HidCommand.Siri -> 231
        HidCommand.Sleep, HidCommand.Wake -> 26
        HidCommand.ChannelIncrement -> 166
        HidCommand.ChannelDecrement -> 167
        HidCommand.Guide -> 172
        HidCommand.PageUp -> 92
        HidCommand.PageDown -> 93
        HidCommand.Screensaver -> null
    }

    companion object {
        const val REMOTE_PORT = 6466
        private const val SOCKET_TIMEOUT_MS = 15_000
        private const val SHORT = 3
        private const val START_LONG = 1
        private const val END_LONG = 2
        private const val KEYCODE_DELETE = 67
        private const val KEYCODE_SEARCH = 84
        private const val VOICE_SAMPLE_RATE = 8_000
        private const val VOICE_CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val VOICE_AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val VOICE_CHUNK_BYTES = 20 * 1024
        private const val DEFAULT_FEATURES = 622

        fun deleteIdentity(context: Context, alias: String) = AndroidTvIdentity.delete(context, alias)
    }
}

/** Android TV Remote v2 accepts URI links directly and package IDs via the
 * Play Store launch URI used by the Android TV Remote Service. */
internal fun normalizeAndroidTvAppLink(target: String): String {
    val scheme = target.substringBefore(':', missingDelimiterValue = "")
    val isUri = scheme.isNotBlank() && scheme.first().isLetter() &&
        scheme.drop(1).all { it.isLetterOrDigit() || it == '+' || it == '-' || it == '.' }
    if (isUri) return target
    // Some Android TV Remote Service versions ignore package/market links but
    // resolve the same installed apps through their HTTPS app links. Keep
    // this mapping for users who already saved the old package-based defaults.
    return ANDROID_TV_APP_LINKS[target] ?: "market://launch?id=$target"
}

private val ANDROID_TV_APP_LINKS = mapOf(
    AndroidTvApps.ZEE5 to "https://www.zee5.com/",
    AndroidTvApps.YOUTUBE to "https://www.youtube.com",
    AndroidTvApps.PRIME_VIDEO to "https://www.primevideo.com/",
    AndroidTvApps.JIOHOTSTAR to "https://www.hotstar.com/",
)

private object AndroidTvIdentity {
    private const val KEYSTORE = "AndroidKeyStore"

    fun ensure(context: Context, alias: String): X509Certificate {
        val keyStore = keyStore()
        if (!keyStore.containsAlias(alias)) {
            val start = Calendar.getInstance().time
            val end = Calendar.getInstance().apply { set(2099, Calendar.DECEMBER, 31) }.time
            val spec = KeyPairGeneratorSpec.Builder(context)
                .setAlias(alias)
                .setSubject(javax.security.auth.x500.X500Principal("CN=${androidTvClientName(alias)}"))
                // Android TV may keep more than one client certificate. A
                // unique serial prevents different phones from looking like
                // the same X.509 identity to TV firmware that keys on the
                // certificate subject/serial pair.
                .setSerialNumber(BigInteger(128, SecureRandom()).or(BigInteger.ONE))
                .setStartDate(start)
                .setEndDate(end)
                .setKeySize(2048)
                .build()
            KeyPairGenerator.getInstance("RSA", KEYSTORE).apply {
                initialize(spec)
                generateKeyPair()
            }
        }
        return certificate(alias)
    }

    fun certificate(alias: String): X509Certificate {
        val certificate = keyStore().getCertificate(alias) as? X509Certificate
            ?: error("Android TV client certificate is missing")
        return certificate
    }

    fun openSocket(
        host: String,
        port: Int,
        alias: String,
        expectedFingerprint: String?,
    ): SSLSocket {
        val keyStore = keyStore()
        val baseKeyManager = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
            init(keyStore, null)
        }.keyManagers.filterIsInstance<X509KeyManager>().firstOrNull()
            ?: error("Android TV client certificate key manager is unavailable")
        val keyManagers = arrayOf<KeyManager>(object : X509KeyManager by baseKeyManager {
            override fun chooseClientAlias(
                keyType: Array<out String>?,
                issuers: Array<out Principal>?,
                socket: Socket?,
            ): String = alias
        })
        val trustManagers = arrayOf<TrustManager>(TrustAllManager)
        val sslContext = SSLContext.getInstance("TLS").apply {
            init(keyManagers, trustManagers, SecureRandom())
        }
        val socket = (sslContext.socketFactory.createSocket() as SSLSocket).apply {
            useClientMode = true
            connect(InetSocketAddress(host, port), 10_000)
            startHandshake()
        }
        if (expectedFingerprint != null) {
            val peer = socket.session.peerCertificates.firstOrNull() as? X509Certificate
                ?: throw SSLPeerUnverifiedException("Android TV did not provide a server certificate")
            check(fingerprint(peer).equals(expectedFingerprint, ignoreCase = true)) {
                "Android TV server certificate changed"
            }
        }
        return socket
    }

    fun fingerprint(certificate: X509Certificate): String =
        MessageDigest.getInstance("SHA-256").digest(certificate.encoded).toHex()

    fun delete(context: Context, alias: String) {
        runCatching { keyStore().deleteEntry(alias) }
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }

    private object TrustAllManager : X509TrustManager {
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = Unit
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) = Unit
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }
}

private fun readFrame(input: DataInputStream): ByteArray {
    var length = 0
    var shift = 0
    while (shift < 32) {
        val byte = input.readUnsignedByte()
        length = length or ((byte and 0x7f) shl shift)
        if (byte and 0x80 == 0) break
        shift += 7
    }
    require(length in 0..1_000_000) { "Invalid Android TV frame length" }
    return ByteArray(length).also { input.readFully(it) }
}

private fun String.hexBytes(): ByteArray {
    require(length % 2 == 0) { "Hex value must have an even number of digits" }
    return ByteArray(length / 2) { index ->
        substring(index * 2, index * 2 + 2).toInt(16).toByte()
    }
}

private fun String.evenHexBytes(): ByteArray = (if (length % 2 == 0) this else "0$this").hexBytes()

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it.toInt() and 0xff) }

/**
 * Android TV may use the pairing client name as part of its registered
 * controller identity. Derive a stable, per-pairing name from the unique
 * Android Keystore alias instead of presenting every phone as "LocalBeam".
 */
private fun androidTvClientName(alias: String): String {
    val suffix = alias
        .removePrefix("androidtv_")
        .filter { it.isLetterOrDigit() }
        .takeLast(8)
        .ifBlank { "client" }
    return "LocalBeam-$suffix"
}
