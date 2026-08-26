package com.zakir.vestra.ui.components

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.zakir.vestra.ui.theme.VestraColors
import java.util.Locale

/**
 * Microphone voice dictation button that streams speech-to-text directly into prompt input
 * with a dynamic waveform animation.
 */
@Composable
fun VoiceDictationButton(
    onTranscription: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val context = LocalContext.current
    var isListening by remember { mutableStateOf(false) }
    var speechRecognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }
    var rmsRmsLevel by remember { mutableFloatStateOf(0f) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { isGranted ->
        if (isGranted) {
            startSpeechListening(
                context = context,
                onListeningState = { isListening = it },
                onRmsChanged = { rmsRmsLevel = it },
                onResult = { text ->
                    onTranscription(text)
                    isListening = false
                },
                setRecognizer = { speechRecognizer = it },
            )
        } else {
            Toast.makeText(context, "Microphone permission required for voice dictation", Toast.LENGTH_SHORT).show()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                speechRecognizer?.destroy()
            } catch (_: Exception) {}
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseAlpha",
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(if (isListening) VestraColors.Accent.copy(alpha = pulseAlpha) else VestraColors.GlassFill)
            .border(
                1.dp,
                if (isListening) VestraColors.Accent else VestraColors.GlassBorder,
                RoundedCornerShape(50),
            )
            .clickable(enabled = enabled) {
                if (isListening) {
                    try {
                        speechRecognizer?.stopListening()
                    } catch (_: Exception) {}
                    isListening = false
                } else {
                    val hasPermission = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.RECORD_AUDIO,
                    ) == PackageManager.PERMISSION_GRANTED

                    if (hasPermission) {
                        startSpeechListening(
                            context = context,
                            onListeningState = { isListening = it },
                            onRmsChanged = { rmsRmsLevel = it },
                            onResult = { text ->
                                onTranscription(text)
                                isListening = false
                            },
                            setRecognizer = { speechRecognizer = it },
                        )
                    } else {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }
            }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = if (isListening) Icons.Outlined.Mic else Icons.Outlined.Mic,
                contentDescription = if (isListening) "Stop recording" else "Voice prompt dictation",
                tint = if (isListening) Color.White else VestraColors.Accent,
                modifier = Modifier.size(16.dp),
            )
            if (isListening) {
                // Waveform bars
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    repeat(3) { index ->
                        Box(
                            modifier = Modifier
                                .width(2.5.dp)
                                .height((8 + (index * 4) + (rmsRmsLevel * 1.5f).toInt()).coerceIn(6, 18).dp)
                                .clip(CircleShape)
                                .background(Color.White),
                        )
                    }
                }
                Text(
                    "Listening…",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = Color.White,
                )
            }
        }
    }
}

private fun startSpeechListening(
    context: Context,
    onListeningState: (Boolean) -> Unit,
    onRmsChanged: (Float) -> Unit,
    onResult: (String) -> Unit,
    setRecognizer: (SpeechRecognizer) -> Unit,
) {
    if (!SpeechRecognizer.isRecognitionAvailable(context)) {
        Toast.makeText(context, "Speech recognition not available on device", Toast.LENGTH_SHORT).show()
        return
    }

    try {
        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        setRecognizer(recognizer)

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }

        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                onListeningState(true)
            }

            override fun onBeginningOfSpeech() {}

            override fun onRmsChanged(rmsdB: Float) {
                onRmsChanged(rmsdB.coerceAtLeast(0f))
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                onListeningState(false)
            }

            override fun onError(error: Int) {
                onListeningState(false)
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    onResult(matches[0])
                }
                onListeningState(false)
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    onResult(matches[0])
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        recognizer.startListening(intent)
        onListeningState(true)
    } catch (e: Exception) {
        onListeningState(false)
        Toast.makeText(context, "Could not start voice input: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
