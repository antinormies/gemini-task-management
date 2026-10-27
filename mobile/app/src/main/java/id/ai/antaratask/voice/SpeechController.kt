package id.ai.antaratask.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

class SpeechController(
    private val context: Context,
    private val onPartial: (String) -> Unit,
    private val onFinal: (String) -> Unit,
    private val onError: (String) -> Unit,
    private val onReady: () -> Unit
) : RecognitionListener {
    private var recognizer: SpeechRecognizer? = null
    private var delivered = false

    private fun create(): SpeechRecognizer {
        val existing = recognizer
        if (existing != null) return existing
        val created = if (SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
            runCatching { SpeechRecognizer.createOnDeviceSpeechRecognizer(context) }
                .getOrElse { SpeechRecognizer.createSpeechRecognizer(context) }
        } else {
            SpeechRecognizer.createSpeechRecognizer(context)
        }
        created.setRecognitionListener(this)
        recognizer = created
        return created
    }

    fun start(languageTag: String) {
        delivered = false
        val recognizer = create()
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }
        runCatching { recognizer.startListening(intent) }
            .onFailure { onError(it.message ?: "Could not start speech recognition.") }
    }

    fun stop() {
        runCatching { recognizer?.stopListening() }
    }

    fun cancel() {
        delivered = true
        runCatching { recognizer?.cancel() }
        onReady()
    }

    fun destroy() {
        runCatching { recognizer?.destroy() }
        recognizer = null
    }

    override fun onReadyForSpeech(params: Bundle?) = onReady()

    override fun onBeginningOfSpeech() = Unit

    override fun onRmsChanged(rmsdB: Float) = Unit

    override fun onBufferReceived(buffer: ByteArray?) = Unit

    override fun onEndOfSpeech() = Unit

    override fun onError(error: Int) {
        if (delivered) return
        delivered = true
        onError(messageFor(error))
    }

    override fun onResults(results: Bundle?) {
        if (delivered) return
        delivered = true
        val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
            ?.trim()
            .orEmpty()
        if (text.isBlank()) onError("I did not catch that.") else onFinal(text)
    }

    override fun onPartialResults(partialResults: Bundle?) {
        val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
            ?.trim()
            .orEmpty()
        if (text.isNotBlank()) onPartial(text)
    }

    override fun onEvent(eventType: Int, params: Bundle?) = Unit

    private fun messageFor(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error. Try again."
        SpeechRecognizer.ERROR_CLIENT -> "Speech recognition was interrupted."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required."
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech service is offline. Check your connection."
        SpeechRecognizer.ERROR_NO_MATCH -> "I did not catch that. Try again."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer is busy. Wait a moment."
        SpeechRecognizer.ERROR_SERVER -> "Speech service returned an error."
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "I heard silence. Try again."
        else -> "Speech recognition failed. Try again."
    }
}
