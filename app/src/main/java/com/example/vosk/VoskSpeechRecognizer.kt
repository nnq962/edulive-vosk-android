package com.example.vosk

import android.content.Context
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.SpeechStreamService
import org.vosk.android.StorageService
import java.io.IOException

/**
 * Lớp quản lý toàn bộ logic Speech Recognition sử dụng Vosk
 * Tách biệt hoàn toàn khỏi UI để dễ maintain và test
 */
class VoskSpeechRecognizer(private val context: Context) {

    companion object {
        // Cấu hình model và sample rate
        private const val MODEL_NAME = "model-en-us"
        private const val MODEL_FOLDER = "model"
        private const val SAMPLE_RATE = 16000.0f
        
        // Cấu hình từ điển cho file recognition
        private const val FILE_GRAMMAR = "[\"one zero zero zero one\", " +
                "\"oh zero one two three four five six seven eight nine\", \"[unk]\"]"
        
        // File audio demo
        private const val DEMO_AUDIO_FILE = "10001-90210-01803.wav"
        private const val AUDIO_HEADER_SKIP = 44L
    }

    // Các interface callback để communicate với UI
    interface SpeechRecognitionCallback {
        fun onModelInitialized()
        fun onModelError(error: String)
        fun onRecognitionStart(isFromFile: Boolean)
        fun onRecognitionStop()
        fun onPartialResult(result: String)
        fun onFinalResult(result: String)
        fun onRecognitionError(error: String)
        fun onRecognitionTimeout()
    }

    // Enum để track trạng thái
    enum class RecognitionState {
        INITIALIZING,
        READY,
        RECOGNIZING_FILE,
        RECOGNIZING_MIC,
        STOPPED,
        ERROR
    }

    // Properties
    private var model: Model? = null
    private var speechService: SpeechService? = null
    private var speechStreamService: SpeechStreamService? = null
    private var callback: SpeechRecognitionCallback? = null
    private var currentState = RecognitionState.INITIALIZING
    private var isPaused = false

    // Getter cho state
    val state: RecognitionState get() = currentState
    val isModelReady: Boolean get() = model != null && currentState == RecognitionState.READY
    val isRecognizing: Boolean get() = currentState == RecognitionState.RECOGNIZING_FILE || 
                                               currentState == RecognitionState.RECOGNIZING_MIC

    /**
     * Khởi tạo Vosk Speech Recognizer
     */
    fun initialize(callback: SpeechRecognitionCallback) {
        this.callback = callback
        currentState = RecognitionState.INITIALIZING
        
        // Set log level
        LibVosk.setLogLevel(LogLevel.INFO)
        
        // Unpack model
        StorageService.unpack(
            context,
            MODEL_NAME,
            MODEL_FOLDER,
            { loadedModel ->
                try {
                    model = loadedModel
                    currentState = RecognitionState.READY
                    callback.onModelInitialized()
                } catch (e: IOException) {
                    handleError("Failed to initialize model: ${e.message}")
                }
            },
            { exception ->
                handleError("Failed to unpack model: ${exception.message}")
            }
        )
    }

    /**
     * Bắt đầu nhận dạng từ file audio
     */
    fun startFileRecognition() {
        if (!isModelReady) {
            handleError("Model not ready")
            return
        }

        if (speechStreamService != null) {
            stopFileRecognition()
            return
        }

        try {
            currentState = RecognitionState.RECOGNIZING_FILE
            callback?.onRecognitionStart(isFromFile = true)

            val recognizer = Recognizer(model!!, SAMPLE_RATE, FILE_GRAMMAR)
            val audioInputStream = context.assets.open(DEMO_AUDIO_FILE)
            
            if (audioInputStream.skip(AUDIO_HEADER_SKIP) != AUDIO_HEADER_SKIP) {
                throw IOException("Audio file too short")
            }

            speechStreamService = SpeechStreamService(recognizer, audioInputStream, SAMPLE_RATE)
            speechStreamService?.start(createRecognitionListener())
            
        } catch (e: IOException) {
            handleError("File recognition error: ${e.message}")
        }
    }

    /**
     * Dừng nhận dạng từ file
     */
    fun stopFileRecognition() {
        speechStreamService?.stop()
        speechStreamService = null
        currentState = RecognitionState.READY
        callback?.onRecognitionStop()
    }

    /**
     * Bắt đầu nhận dạng từ microphone
     */
    fun startMicRecognition() {
        if (!isModelReady) {
            handleError("Model not ready")
            return
        }

        if (speechService != null) {
            stopMicRecognition()
            return
        }

        try {
            currentState = RecognitionState.RECOGNIZING_MIC
            isPaused = false
            callback?.onRecognitionStart(isFromFile = false)

            val recognizer = Recognizer(model!!, SAMPLE_RATE)
            speechService = SpeechService(recognizer, SAMPLE_RATE)
            speechService?.startListening(createRecognitionListener())
            
        } catch (e: IOException) {
            handleError("Mic recognition error: ${e.message}")
        }
    }

    /**
     * Dừng nhận dạng từ microphone
     */
    fun stopMicRecognition() {
        speechService?.stop()
        speechService = null
        isPaused = false
        currentState = RecognitionState.READY
        callback?.onRecognitionStop()
    }

    /**
     * Tạm dừng/tiếp tục nhận dạng microphone
     */
    fun pauseMicRecognition(pause: Boolean) {
        if (currentState == RecognitionState.RECOGNIZING_MIC) {
            isPaused = pause
            speechService?.setPause(pause)
        }
    }

    /**
     * Dừng tất cả services và cleanup
     */
    fun cleanup() {
        stopFileRecognition()
        stopMicRecognition()
        
        speechService?.shutdown()
        speechService = null
        speechStreamService = null
        model = null
        callback = null
        currentState = RecognitionState.STOPPED
    }

    /**
     * Tạo RecognitionListener để handle callbacks từ Vosk
     */
    private fun createRecognitionListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onPartialResult(hypothesis: String?) {
                hypothesis?.let { 
                    callback?.onPartialResult(it)
                }
            }

            override fun onResult(hypothesis: String?) {
                hypothesis?.let { 
                    callback?.onFinalResult(it)
                }
            }

            override fun onFinalResult(hypothesis: String?) {
                hypothesis?.let { 
                    callback?.onFinalResult(it)
                }
                
                when (currentState) {
                    RecognitionState.RECOGNIZING_FILE -> stopFileRecognition()
                    RecognitionState.RECOGNIZING_MIC -> {
                        // Mic recognition continues until manually stopped
                    }
                    else -> {}
                }
            }

            override fun onError(exception: Exception?) {
                handleError(exception?.message ?: "Unknown recognition error")
            }

            override fun onTimeout() {
                callback?.onRecognitionTimeout()
                when (currentState) {
                    RecognitionState.RECOGNIZING_FILE -> stopFileRecognition()
                    RecognitionState.RECOGNIZING_MIC -> stopMicRecognition()
                    else -> {}
                }
            }
        }
    }

    /**
     * Handle lỗi và notify callback
     */
    private fun handleError(message: String) {
        currentState = RecognitionState.ERROR
        callback?.onRecognitionError(message)
        
        // Cleanup on error
        speechService?.stop()
        speechStreamService?.stop()
        speechService = null
        speechStreamService = null
        isPaused = false
    }

    /**
     * Kiểm tra xem có đang trong quá trình nhận dạng mic hay không
     */
    fun isMicRecognizing(): Boolean {
        return currentState == RecognitionState.RECOGNIZING_MIC
    }

    /**
     * Kiểm tra xem có đang trong quá trình nhận dạng file hay không
     */
    fun isFileRecognizing(): Boolean {
        return currentState == RecognitionState.RECOGNIZING_FILE
    }

    /**
     * Kiểm tra trạng thái pause
     */
    fun isPaused(): Boolean {
        return isPaused
    }
}