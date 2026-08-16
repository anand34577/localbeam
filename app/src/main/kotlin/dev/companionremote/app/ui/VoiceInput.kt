package dev.companionremote.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import dev.companionremote.app.i18n.LocalAppStrings
import java.util.Locale

/** Why a voice attempt couldn't proceed (mapped to a message by the caller). */
enum class VoiceError { PermissionDenied, Unavailable, Failed }

/**
 * On-device speech recognition for dictating into a TV search box. Holds the
 * [SpeechRecognizer], Compose-observable [listening]/[partial] state and the
 * on-device recognition lifecycle. Created once via [remember].
 *
 * Privacy: this controller is only constructed with Android's on-device
 * recognizer. There is no online fallback, so audio stays on the device.
 */
private class VoiceController(
    private val recognizer: SpeechRecognizer?,
    private val onResult: (String) -> Unit,
    private val onError: (VoiceError) -> Unit,
) {
    var listening by mutableStateOf(false)
        private set
    var partial by mutableStateOf("")
        private set

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}

        override fun onPartialResults(partialResults: Bundle?) {
            partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                ?.let { partial = it }
        }

        override fun onResults(results: Bundle?) {
            val best = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
            listening = false
            partial = ""
            if (best.isNotBlank()) onResult(best)
        }

        override fun onError(error: Int) {
            listening = false
            partial = ""
            // NO_MATCH / CLIENT are benign (silence, user cancel).
            if (error != SpeechRecognizer.ERROR_NO_MATCH &&
                error != SpeechRecognizer.ERROR_CLIENT
            ) {
                onError(VoiceError.Failed)
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun makeIntent() =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            // Keep dictation local; this app deliberately has no cloud speech
            // fallback or analytics path.
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }

    private fun startListening() {
        val r = recognizer ?: return
        r.setRecognitionListener(listener)
        r.startListening(makeIntent())
    }

    fun begin() {
        if (recognizer == null) {
            onError(VoiceError.Unavailable)
            return
        }
        if (listening) return
        partial = ""
        listening = true
        runCatching { startListening() }
            .onFailure {
                listening = false
                partial = ""
                onError(VoiceError.Failed)
            }
    }

    fun cancel() {
        recognizer?.cancel()
        listening = false
        partial = ""
    }

    fun destroy() = recognizer?.destroy()
}

/**
 * Sets up voice dictation and returns a lambda that starts a listening session
 * (requesting the mic permission on first use). Also emits the listening
 * overlay while active.
 */
@Composable
fun rememberVoiceInput(
    onResult: (String) -> Unit,
    onError: (VoiceError) -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val currentOnResult by rememberUpdatedState(onResult)
    val currentOnError by rememberUpdatedState(onError)

    val controller = remember {
        val recognizer = if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        ) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        } else {
            null
        }
        VoiceController(
            recognizer = recognizer,
            onResult = { currentOnResult(it) },
            onError = { currentOnError(it) },
        )
    }
    DisposableEffect(Unit) { onDispose { controller.destroy() } }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) controller.begin() else currentOnError(VoiceError.PermissionDenied)
    }

    if (controller.listening) {
        ListeningDialog(partial = controller.partial, onCancel = controller::cancel)
    }

    return {
        val granted = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) controller.begin() else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }
}

@Composable
private fun ListeningDialog(partial: String, onCancel: () -> Unit) {
    val s = LocalAppStrings.current
    Dialog(onDismissRequest = onCancel) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
        ) {
            Column(
                Modifier.padding(28.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                val transition = rememberInfiniteTransition(label = "mic")
                val pulse by transition.animateFloat(
                    initialValue = 1f,
                    targetValue = 1.25f,
                    animationSpec = infiniteRepeatable(tween(650), RepeatMode.Reverse),
                    label = "pulse",
                )
                Box(
                    Modifier.size(72.dp).scale(pulse)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.Mic,
                        contentDescription = null,
                        Modifier.size(34.dp),
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                }
                Text(
                    partial.ifBlank { s.voiceListening },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    color = if (partial.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface,
                )
                TextButton(onClick = onCancel) { Text(s.cancel) }
            }
        }
    }
}
