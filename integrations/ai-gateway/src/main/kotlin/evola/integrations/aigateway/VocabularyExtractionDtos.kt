package evola.integrations.aigateway

data class ExtractVocabularyRequest(
    val text: String,
    val maxItems: Int = 30,
)

data class ExtractedVocabularyItem(
    val germanWord: String,
    val englishTranslation: String,
    val article: String? = null,
    val pluralForm: String? = null,
    val partOfSpeech: String? = null,
    val exampleSentence: String? = null,
    val cefrLevel: String = "UNKNOWN",
    val topic: String? = null,
    val synonyms: List<String> = emptyList(),
    val relatedWords: List<String> = emptyList(),
)
