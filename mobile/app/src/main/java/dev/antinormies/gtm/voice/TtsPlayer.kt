package dev.antinormies.gtm.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

class TtsPlayer(context: Context) : TextToSpeech.OnInitListener {
    private val engine = TextToSpeech(context.applicationContext, this)
    private var ready = false
    var muted: Boolean = false

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            ready = true
            val id = Locale("id", "ID")
            if (engine.isLanguageAvailable(id) >= TextToSpeech.LANG_AVAILABLE) {
                engine.language = id
            } else {
                engine.language = Locale.US
            }
        }
    }

    fun speak(text: String) {
        if (!ready || muted || text.isBlank()) return
        engine.speak(text.take(220), TextToSpeech.QUEUE_FLUSH, null, "gtm-confirm")
    }

    fun shutdown() {
        ready = false
        engine.stop()
        engine.shutdown()
    }
}
