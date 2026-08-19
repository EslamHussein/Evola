package evola.composeapp.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

actual class SpeechService(context: Context) {
    private var engine: TextToSpeech? = null
    private var ready = false

    init {
        engine = TextToSpeech(context.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) engine?.language = Locale.GERMANY
        }
    }

    actual fun speak(text: String, languageTag: String, rate: Float, voiceName: String?) {
        val tts = engine ?: return
        if (!ready || text.isBlank()) return
        tts.setSpeechRate(rate)
        // Falls back to whatever voice is already set (the locale default from init{}) if
        // voiceName is null or no longer matches an installed voice - never blocks speech.
        val matched = voiceName?.let { name -> tts.voices?.firstOrNull { it.name == name } }
        if (matched != null) tts.voice = matched
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "evola-speak")
    }

    actual fun availableVoiceNames(languageTag: String): List<String> {
        val tts = engine ?: return emptyList()
        if (!ready) return emptyList()
        val languagePrefix = languageTag.substringBefore("-")
        return tts.voices.orEmpty()
            .filter { it.locale.language == languagePrefix }
            .map { it.name }
            .sorted()
    }

    actual fun stop() {
        engine?.stop()
    }

    fun shutdown() {
        engine?.shutdown()
        engine = null
    }
}

@Composable
actual fun rememberSpeechService(): SpeechService {
    val context = LocalContext.current
    val service = remember { SpeechService(context) }
    DisposableEffect(Unit) {
        onDispose { service.shutdown() }
    }
    return service
}
