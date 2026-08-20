package evola.shared.feature.vocabulary.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Reword's pre-built category picker, adapted to this app's architecture: Evola's real content
 * model is lesson-scoped and AI-extracted from a learner's own materials (see docs/ROADMAP.md's
 * "Explicitly not done" note on why a pre-loaded-deck model was originally skipped), so this is a
 * bundled asset - not a competing content pipeline - rather than hand-authored Kotlin data. The
 * bundled sets today are the official German-Arabic glossaries for "Das Leben" A1/A2 (Cornelsen),
 * loaded from JSON resources (see [decodeStarterLevels]) rather than a fake/approximated wordlist -
 * the app never invents level assignments that aren't backed by a real source. Each [StarterLesson]
 * becomes one ordinary lesson (via [VocabularyRepository.createStarterLesson]), indistinguishable
 * afterwards from any other lesson. A1/A2 aren't mutually exclusive, and neither are a level's own
 * lessons - the picker is a plain multi-select across every [StarterLesson] in every [StarterLevel]. */
@Serializable
data class StarterWord(val term: String, val meaning: String, val nativeMeaning: String? = null)

@Serializable
data class StarterLesson(val id: String, val title: String, val words: List<StarterWord>)

@Serializable
data class StarterLevel(val id: String, val title: String, val subtitle: String, val lessons: List<StarterLesson>)

/** Parses one or more bundled JSON assets (one per level, e.g. `files/starter_a1.json`) into
 * [StarterLevel]s - a malformed/unreadable individual file is skipped rather than failing the whole
 * picker, so one bad asset can't take down onboarding. */
fun decodeStarterLevels(jsonTexts: List<String>): List<StarterLevel> =
    jsonTexts.mapNotNull { text -> runCatching { Json.decodeFromString<StarterLevel>(text) }.getOrNull() }
