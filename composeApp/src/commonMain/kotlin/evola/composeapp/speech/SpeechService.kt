package evola.composeapp.speech

import androidx.compose.runtime.Composable

/** Speaks German text aloud - the vocabulary session's audio button (previously visual-only) and
 * hands-free mode's narration both go through this. Android wraps the system
 * [android.speech.tts.TextToSpeech] engine; iOS wraps `AVSpeechSynthesizer`. German-only, matching
 * this app's German-only MVP scope - [languageTag] is always "de-DE" at every call site rather than
 * a general-purpose multi-language API surface. */
expect class SpeechService {
    /** [rate] is a multiplier around 1.0 (0.5x-2x), matching Settings' speech-rate slider.
     * [voiceName] is one of [availableVoiceNames]'s results, or null for the platform/engine
     * default - falls back to the default silently if the named voice is no longer available
     * (uninstalled, engine changed) rather than failing to speak at all. */
    fun speak(text: String, languageTag: String = "de-DE", rate: Float = 1.0f, voiceName: String? = null)
    fun stop()

    /** Every installed voice for [languageTag] the engine currently reports - queried live rather
     * than cached, since Android's system TTS engine/voice set can change between app launches
     * (a different engine installed, a voice pack removed). Empty (not an error) if the engine
     * hasn't finished initializing yet or has no matching voice - Settings' picker just shows
     * "Default" alone in that case rather than a broken empty dropdown. */
    fun availableVoiceNames(languageTag: String = "de-DE"): List<String>
}

/** Composable provider so `App.kt`/screens obtain one instance (with the Android `Context` bound
 * where needed) and share it, mirroring [evola.composeapp.di.rememberDatabaseDriverFactory]. */
@Composable
expect fun rememberSpeechService(): SpeechService
