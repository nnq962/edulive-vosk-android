package com.example.vosk

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.vosk.ui.theme.VoskTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    
    // Speech recognizer instance
    private lateinit var speechRecognizer: VoskSpeechRecognizer
    
    // UI State
    private val uiState = mutableStateOf(UiState())
    
    data class UiState(
        val isModelReady: Boolean = false,
        val recognitionText: String = "Initializing model...",
        val isRecognizingFile: Boolean = false,
        val isRecognizingMic: Boolean = false,
        val isPaused: Boolean = false,
        val hasError: Boolean = false
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize speech recognizer
        speechRecognizer = VoskSpeechRecognizer(this)

        // Permission launcher
        val requestPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                initializeSpeechRecognizer()
            } else {
                updateUiState(
                    recognitionText = "❌ Microphone permission denied",
                    hasError = true
                )
            }
        }

        // Check permissions
        val permissionCheck = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        )
        
        if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
            initializeSpeechRecognizer()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }

        setContent {
            VoskTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    SpeechRecognitionScreen(
                        modifier = Modifier.padding(innerPadding),
                        uiState = uiState.value,
                        onStartFileRecognition = { speechRecognizer.startFileRecognition() },
                        onStartMicRecognition = { speechRecognizer.startMicRecognition() },
                        onPauseMic = { paused -> speechRecognizer.pauseMicRecognition(paused) },
                        onStopFileRecognition = { speechRecognizer.stopFileRecognition() },
                        onStopMicRecognition = { speechRecognizer.stopMicRecognition() }
                    )
                }
            }
        }
    }

    private fun initializeSpeechRecognizer() {
        speechRecognizer.initialize(object : VoskSpeechRecognizer.SpeechRecognitionCallback {
            override fun onModelInitialized() {
                updateUiState(
                    isModelReady = true,
                    recognitionText = "✅ Model loaded! Ready to recognize speech.",
                    hasError = false
                )
            }

            override fun onModelError(error: String) {
                updateUiState(
                    recognitionText = "❌ Model Error: $error",
                    hasError = true
                )
            }

            override fun onRecognitionStart(isFromFile: Boolean) {
                if (isFromFile) {
                    updateUiState(
                        isRecognizingFile = true,
                        recognitionText = "🎵 Recognizing from audio file..."
                    )
                } else {
                    updateUiState(
                        isRecognizingMic = true,
                        recognitionText = "🎤 Listening... Say something!"
                    )
                }
            }

            override fun onRecognitionStop() {
                updateUiState(
                    isRecognizingFile = false,
                    isRecognizingMic = false,
                    isPaused = false
                )
            }

            override fun onPartialResult(result: String) {
                val currentText = uiState.value.recognitionText
                val newText = if (currentText.contains("Partial:") || currentText.contains("Result:")) {
                    "$currentText\n🔄 Partial: $result"
                } else {
                    "🔄 Partial: $result"
                }
                updateUiState(recognitionText = newText)
            }

            override fun onFinalResult(result: String) {
                val currentText = uiState.value.recognitionText
                val newText = "$currentText\n✨ Result: $result"
                updateUiState(recognitionText = newText)
            }

            override fun onRecognitionError(error: String) {
                updateUiState(
                    recognitionText = "❌ Recognition Error: $error",
                    hasError = true,
                    isRecognizingFile = false,
                    isRecognizingMic = false
                )
            }

            override fun onRecognitionTimeout() {
                val currentText = uiState.value.recognitionText
                updateUiState(
                    recognitionText = "$currentText\n⏰ Recognition timeout",
                    isRecognizingFile = false,
                    isRecognizingMic = false
                )
            }
        })
    }

    private fun updateUiState(
        isModelReady: Boolean = uiState.value.isModelReady,
        recognitionText: String = uiState.value.recognitionText,
        isRecognizingFile: Boolean = uiState.value.isRecognizingFile,
        isRecognizingMic: Boolean = uiState.value.isRecognizingMic,
        isPaused: Boolean = uiState.value.isPaused,
        hasError: Boolean = uiState.value.hasError
    ) {
        uiState.value = UiState(
            isModelReady = isModelReady,
            recognitionText = recognitionText,
            isRecognizingFile = isRecognizingFile,
            isRecognizingMic = isRecognizingMic,
            isPaused = isPaused,
            hasError = hasError
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer.cleanup()
    }
}

@Composable
fun SpeechRecognitionScreen(
    modifier: Modifier = Modifier,
    uiState: MainActivity.UiState,
    onStartFileRecognition: () -> Unit,
    onStartMicRecognition: () -> Unit,
    onPauseMic: (Boolean) -> Unit,
    onStopFileRecognition: () -> Unit,
    onStopMicRecognition: () -> Unit
) {
    // Scroll state for auto-scroll
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    
    // Auto-scroll when text changes
    LaunchedEffect(uiState.recognitionText) {
        if (scrollState.maxValue > 0) {
            coroutineScope.launch {
                scrollState.animateScrollTo(scrollState.maxValue)
            }
        }
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Title
        Text(
            text = "Vosk Speech Recognition",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )

        // Status Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (uiState.hasError) {
                    MaterialTheme.colorScheme.errorContainer
                } else if (uiState.isModelReady) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.secondaryContainer
                }
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = when {
                        uiState.hasError -> "❌ Error"
                        uiState.isModelReady -> "✅ Ready"
                        else -> "🔄 Loading"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = when {
                        uiState.hasError -> MaterialTheme.colorScheme.onErrorContainer
                        uiState.isModelReady -> MaterialTheme.colorScheme.onPrimaryContainer
                        else -> MaterialTheme.colorScheme.onSecondaryContainer
                    }
                )
            }
        }

        // Control Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // File Recognition Button
            Button(
                onClick = if (uiState.isRecognizingFile) onStopFileRecognition else onStartFileRecognition,
                enabled = uiState.isModelReady,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (uiState.isRecognizingFile) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
            ) {
                Text(if (uiState.isRecognizingFile) "🛑 Stop File" else "🎵 File Audio")
            }

            // Mic Recognition Button
            Button(
                onClick = if (uiState.isRecognizingMic) onStopMicRecognition else onStartMicRecognition,
                enabled = uiState.isModelReady,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (uiState.isRecognizingMic) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.secondary
                    }
                )
            ) {
                Text(if (uiState.isRecognizingMic) "🛑 Stop Mic" else "🎤 Microphone")
            }
        }

        // Pause Switch (only for mic)
        if (uiState.isRecognizingMic) {
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (uiState.isPaused) "▶️ Pause Recognition" else "⏸️ Pause Recognition",
                            style = MaterialTheme.typography.bodyLarge
                    )
                    }
                    Switch(
                        checked = uiState.isPaused,
                        onCheckedChange = onPauseMic
                    )
                }
            }
        }

        // Results Display
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Recognition Results",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                HorizontalDivider()
                
                Text(
                    text = uiState.recognitionText,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(top = 8.dp),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Start
                )
            }
        }
    }
}