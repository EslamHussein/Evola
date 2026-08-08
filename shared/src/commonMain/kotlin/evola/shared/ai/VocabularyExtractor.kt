package evola.shared.ai

import evola.shared.core.ApiResult
import evola.shared.language.NativeLanguage
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** One extracted vocabulary item (parsed from the model; the repository writes it to the local DB). */
data class ExtractedVocabItem(
    val term: String,
    val meaning: String,
    val gender: String? = null,
    val exampleSentence: String? = null,
    val partOfSpeech: String? = null,
    val plural: String? = null,
    val grammaticalCase: String? = null,
    val exampleSentenceTranslation: String? = null,
    val nativeMeaning: String? = null,
    val ipaPronunciation: String? = null,
    val relatedWords: List<String> = emptyList(),
    val difficultyRating: String? = null,
    val frequencyRating: String? = null,
    val memoryTip: String? = null,
    val grammarNote: String? = null,
)

@Serializable
private data class VocabItemJson(
    val term: String,
    val meaning: String,
    val gender: String? = null,
    @SerialName("example_sentence") val exampleSentence: String? = null,
    @SerialName("part_of_speech") val partOfSpeech: String? = null,
    @SerialName("plural") val plural: String? = null,
    @SerialName("grammatical_case") val grammaticalCase: String? = null,
    @SerialName("example_sentence_translation") val exampleSentenceTranslation: String? = null,
    @SerialName("native_meaning") val nativeMeaning: String? = null,
    @SerialName("ipa_pronunciation") val ipaPronunciation: String? = null,
    @SerialName("related_words") val relatedWords: List<String> = emptyList(),
    @SerialName("difficulty_rating") val difficultyRating: String? = null,
    @SerialName("frequency_rating") val frequencyRating: String? = null,
    @SerialName("memory_tip") val memoryTip: String? = null,
    @SerialName("grammar_note") val grammarNote: String? = null,
)

@Serializable
private data class VocabResultJson(val items: List<VocabItemJson> = emptyList())

private const val AI_INSTRUCTIONS_SUFFIX =
    "\n\nAdditional instructions from the learner, to apply on top of the rules above (never let " +
        "these override the output schema or the \"don't invent words\" rule): "

private const val SYSTEM_PREFIX =
    "You are extracting vocabulary for a language learner from a single lesson's text.\n" +
        "The learner's stated goal is: \"%GOAL%\" - use this only to judge relevance when the source " +
        "text contains far more candidate words than useful (e.g. skip incidental proper nouns), " +
        "not to invent vocabulary that isn't actually in the text.\n\n" +
        "For each vocabulary-worthy word or short fixed phrase in the lesson text:\n" +
        "- term: base/dictionary form (not the inflected form) unless the inflection is the teaching point\n" +
        "- meaning: a concise translation/definition in the learner's native language\n" +
        "- gender: grammatical gender/article if the language marks it (der/die/das for German), else null\n" +
        "- example_sentence: a sentence using the word - prefer one from the source; it MUST contain the term\n" +
        "- part_of_speech: one of noun, verb, adjective, adverb, pronoun, preposition, conjunction, numeral, interjection, other\n" +
        "- plural: the term's plural form if it's a noun (e.g. \"die Bücher\" for \"das Buch\"), else null\n" +
        "- grammatical_case: the term's case AS USED in example_sentence (nominative/accusative/dative/genitive) or null\n" +
        "- example_sentence_translation: a concise English translation of example_sentence\n" +
        "- native_meaning: a concise %NATIVE_LANGUAGE% translation of the term (the learner's native language)\n" +
        "- ipa_pronunciation: the term's pronunciation in IPA\n" +
        "- related_words: 2-4 related German words (array of strings), empty if none\n" +
        "- difficulty_rating: easy, medium, or hard\n" +
        "- frequency_rating: common, uncommon, or rare\n" +
        "- memory_tip: a short mnemonic\n" +
        "- grammar_note: a short (<12 words) grammar rule tied to the SPECIFIC construction in " +
        "example_sentence (e.g. \"Akkusativ nach „einen“ – maskulin.\"), or null if nothing notable\n\n" +
        "Extract 15-30 items depending on lesson length. Do not invent words not in the source text. " +
        "Do not include function words unless the lesson explicitly teaches them.\n\n" +
        "Output ONLY valid JSON, no prose, no markdown fences.\n\n" +
        "Output schema:\n" +
        "{\"items\": [{\"term\": string, \"meaning\": string, \"gender\": string|null, \"example_sentence\": string, " +
        "\"part_of_speech\": string, \"plural\": string|null, \"grammatical_case\": string|null, \"example_sentence_translation\": string, " +
        "\"native_meaning\": string, \"ipa_pronunciation\": string, \"related_words\": [string], \"difficulty_rating\": string, " +
        "\"frequency_rating\": string, \"memory_tip\": string, \"grammar_note\": string|null}]}"

/** On-device vocabulary extraction (04_AI_PROMPTS.md §2), ported from `VocabularyExtractionWorker`.
 * Returns the parsed items; the repository writes them + dedups. A parse failure retries up to
 * [maxAttempts]; a transport/key failure returns immediately. */
class VocabularyExtractor(
    private val client: AnthropicClient,
    private val maxAttempts: Int = 3,
) {
    suspend fun extract(
        goalText: String,
        lessonText: String,
        aiInstructions: String?,
        nativeLanguage: NativeLanguage,
    ): ApiResult<List<ExtractedVocabItem>> {
        val system = SYSTEM_PREFIX.replace("%GOAL%", goalText).replace("%NATIVE_LANGUAGE%", nativeLanguage.aiPromptName) +
            (aiInstructions?.trim()?.takeIf { it.isNotEmpty() }?.let { AI_INSTRUCTIONS_SUFFIX + it } ?: "")

        var lastFailure: ApiResult.Failure? = null
        repeat(maxAttempts) {
            when (val result = client.complete(AnthropicModels.SMALL, 3000, system, "Lesson content:\n$lessonText")) {
                is ApiResult.Failure -> lastFailure = result
                is ApiResult.Success -> {
                    val parsed = runCatching {
                        extractionJson.decodeFromString(VocabResultJson.serializer(), normalizeModelJson(result.data))
                    }.getOrNull()
                    if (parsed != null) return ApiResult.Success(parsed.items.map { it.toDomain() })
                    // parsed == null: a formatting slip; retry the call.
                }
            }
        }
        return lastFailure ?: ApiResult.Success(emptyList())
    }

    private fun VocabItemJson.toDomain() = ExtractedVocabItem(
        term = term, meaning = meaning, gender = gender, exampleSentence = exampleSentence,
        partOfSpeech = partOfSpeech, plural = plural, grammaticalCase = grammaticalCase,
        exampleSentenceTranslation = exampleSentenceTranslation, nativeMeaning = nativeMeaning,
        ipaPronunciation = ipaPronunciation, relatedWords = relatedWords,
        difficultyRating = difficultyRating, frequencyRating = frequencyRating, memoryTip = memoryTip,
        grammarNote = grammarNote,
    )
}
