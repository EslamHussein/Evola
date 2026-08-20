package evola.composeapp.core.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.AVFAudio.AVSpeechBoundary
import platform.AVFAudio.AVSpeechSynthesisVoice
import platform.AVFAudio.AVSpeechSynthesizer
import platform.AVFAudio.AVSpeechUtterance
import platform.AVFAudio.AVSpeechUtteranceDefaultSpeechRate

actual class SpeechService {
    private val synthesizer = AVSpeechSynthesizer()

    actual fun speak(text: String, languageTag: String, rate: Float, voiceName: String?) {
        if (text.isBlank()) return
        val utterance = AVSpeechUtterance(string = text)
        // Falls back to the plain language-default voice if voiceName is null or no longer
        // matches an installed voice - never blocks speech.
        val matched = voiceName?.let { name ->
            @Suppress("UNCHECKED_CAST")
            (AVSpeechSynthesisVoice.speechVoices() as List<AVSpeechSynthesisVoice>).firstOrNull { it.name == name }
        }
        utterance.voice = matched ?: AVSpeechSynthesisVoice.voiceWithLanguage(languageTag)
        utterance.rate = (AVSpeechUtteranceDefaultSpeechRate * rate).coerceIn(0f, 1f)
        synthesizer.speakUtterance(utterance)
    }

    actual fun availableVoiceNames(languageTag: String): List<String> {
        val languagePrefix = languageTag.substringBefore("-")
        @Suppress("UNCHECKED_CAST")
        val voices = AVSpeechSynthesisVoice.speechVoices() as List<AVSpeechSynthesisVoice>
        return voices.filter { it.language.startsWith(languagePrefix) }.map { it.name }.sorted()
    }

    actual fun stop() {
        synthesizer.stopSpeakingAtBoundary(AVSpeechBoundary.AVSpeechBoundaryImmediate)
    }
}

@Composable
actual fun rememberSpeechService(): SpeechService = remember { SpeechService() }
