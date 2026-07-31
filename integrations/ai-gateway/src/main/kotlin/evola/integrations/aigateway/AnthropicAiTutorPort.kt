package evola.integrations.aigateway

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.models.messages.Message
import com.anthropic.models.messages.MessageCreateParams
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private const val EXERCISE_SYSTEM_PROMPT = "You are a concise German language tutor. Respond with exactly one short " +
    "German example sentence that naturally uses the given word. No translation, no quotes, no explanation."

private const val JSON_ONLY_INSTRUCTION = "Respond with ONLY a single valid JSON object matching the schema below. " +
    "No markdown code fences, no prose before or after, no trailing commentary."

private val LENIENT_JSON = Json { ignoreUnknownKeys = true }

@Serializable
private data class PracticeExerciseJson(
    val promptText: String,
    val correctAnswer: String,
    val hint: String? = null,
    val explanation: String? = null,
    val options: List<String>? = null,
    val matchPairs: List<MatchPair>? = null,
)

private val TRUE_FALSE_OPTIONS = listOf("True", "False")

@Serializable
private data class EvaluationJson(
    val isCorrect: Boolean,
    val correctedText: String? = null,
    val feedback: String,
    val qualityScore: Int,
)

@Serializable
private data class SpeakingTurnJson(
    val correctedReply: String? = null,
    val correctionExplanation: String? = null,
    val aiNextLine: String,
    val scenarioComplete: Boolean = false,
)

@Serializable
private data class ResourceAnalysisJson(
    val language: String,
    val cefrLevel: String,
    val topics: List<String>,
    val summary: String,
)

@Serializable
private data class ExtractedVocabularyItemJson(
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

/**
 * Anthropic-backed [AiTutorPort]. Exercise generation and resource analysis (cheap, bounded,
 * structured tasks) default to Haiku. Free-form answer evaluation and speaking-turn correction —
 * the two tasks that need real reasoning about grammatical correctness — default to a stronger
 * tier (see [DEFAULT_EVALUATION_MODEL]/[DEFAULT_SPEAKING_MODEL]), per ADR-0001-mvp's per-operation
 * cost-tiering principle. Both are fully overridable back to Haiku via env vars if cost matters more.
 */
class AnthropicAiTutorPort(
    private val client: AnthropicClient,
    private val exerciseModel: String,
    private val evaluationModel: String,
    private val speakingModel: String,
) : AiTutorPort {

    override suspend fun generateExercise(request: GenerateExerciseRequest): GeneratedExercise =
        withContext(Dispatchers.IO) {
            val params = MessageCreateParams.builder()
                .model(exerciseModel)
                .maxTokens(300L)
                .system(EXERCISE_SYSTEM_PROMPT)
                .addUserMessage(buildExercisePrompt(request))
                .build()

            val content = extractText(client.messages().create(params))
                ?: error("Anthropic returned no text content for word '${request.germanWord}'")

            GeneratedExercise(content = content, modelUsed = exerciseModel)
        }

    override suspend fun generatePracticeExercise(request: GeneratePracticeExerciseRequest): GeneratedPracticeExercise =
        withContext(Dispatchers.IO) {
            val params = MessageCreateParams.builder()
                .model(exerciseModel)
                .maxTokens(700L)
                .system(practiceSystemPrompt(request.kind))
                .addUserMessage(buildPracticePrompt(request))
                .build()

            val raw = extractText(client.messages().create(params))
                ?: return@withContext fallbackPracticeExercise("(no content returned)", exerciseModel)

            runCatching { LENIENT_JSON.decodeFromString(PracticeExerciseJson.serializer(), raw) }
                .map {
                    GeneratedPracticeExercise(
                        promptText = it.promptText,
                        correctAnswer = it.correctAnswer,
                        hint = it.hint,
                        explanation = it.explanation,
                        options = if (request.kind == PracticeExerciseKind.TRUE_FALSE) TRUE_FALSE_OPTIONS else it.options,
                        matchPairs = it.matchPairs,
                        modelUsed = exerciseModel,
                    )
                }
                .getOrElse { fallbackPracticeExercise(raw, exerciseModel) }
        }

    override suspend fun evaluateFreeformAnswer(request: EvaluateFreeformAnswerRequest): FreeformEvaluationResult =
        withContext(Dispatchers.IO) {
            val params = MessageCreateParams.builder()
                .model(evaluationModel)
                .maxTokens(500L)
                .system(evaluationSystemPrompt())
                .addUserMessage(buildEvaluationPrompt(request))
                .build()

            val raw = extractText(client.messages().create(params))
                ?: return@withContext fallbackEvaluation("(no content returned)", evaluationModel)

            runCatching { LENIENT_JSON.decodeFromString(EvaluationJson.serializer(), raw) }
                .map {
                    FreeformEvaluationResult(
                        isCorrect = it.isCorrect,
                        correctedText = it.correctedText,
                        feedback = it.feedback,
                        qualityScore = it.qualityScore.coerceIn(0, 5),
                        modelUsed = evaluationModel,
                    )
                }
                .getOrElse { fallbackEvaluation(raw, evaluationModel) }
        }

    override suspend fun runSpeakingTurn(request: RunSpeakingTurnRequest): SpeakingTurnResult =
        withContext(Dispatchers.IO) {
            val builder = MessageCreateParams.builder()
                .model(speakingModel)
                .maxTokens(600L)
                .system(speakingSystemPrompt(request.scenarioDescription, request.cefrLevel))

            for (turn in request.conversationHistory) {
                if (turn.speaker == "AI") builder.addAssistantMessage(turn.text) else builder.addUserMessage(turn.text)
            }
            builder.addUserMessage(request.learnerLatestReply)

            val raw = extractText(client.messages().create(builder.build()))
                ?: return@withContext fallbackSpeakingTurn("(no content returned)", speakingModel)

            runCatching { LENIENT_JSON.decodeFromString(SpeakingTurnJson.serializer(), raw) }
                .map {
                    SpeakingTurnResult(
                        correctedReply = it.correctedReply,
                        correctionExplanation = it.correctionExplanation,
                        aiNextLine = it.aiNextLine,
                        scenarioComplete = it.scenarioComplete,
                        modelUsed = speakingModel,
                    )
                }
                .getOrElse { fallbackSpeakingTurn(raw, speakingModel) }
        }

    override suspend fun analyzeResource(request: AnalyzeResourceRequest): ResourceAnalysisResult =
        withContext(Dispatchers.IO) {
            val params = MessageCreateParams.builder()
                .model(exerciseModel)
                .maxTokens(600L)
                .system(
                    "You are a language-learning assistant analyzing a piece of learning material. " +
                        "Identify the language it's written for learning, its estimated CEFR level, 2-5 " +
                        "main topics, and a one-sentence summary. $JSON_ONLY_INSTRUCTION Schema: " +
                        "{\"language\": string, \"cefrLevel\": string (A1|A2|B1|B2|C1|C2|UNKNOWN), " +
                        "\"topics\": string[], \"summary\": string}",
                )
                .addUserMessage(
                    (request.fileNameHint?.let { "Filename: $it\n" } ?: "") + "Content:\n${request.text}",
                )
                .build()

            val raw = extractText(client.messages().create(params))
                ?: return@withContext ResourceAnalysisResult("unknown", "UNKNOWN", emptyList(), "(no content returned)", exerciseModel)

            runCatching { LENIENT_JSON.decodeFromString(ResourceAnalysisJson.serializer(), raw) }
                .map { ResourceAnalysisResult(it.language, it.cefrLevel, it.topics, it.summary, exerciseModel) }
                .getOrElse { ResourceAnalysisResult("unknown", "UNKNOWN", emptyList(), raw, exerciseModel) }
        }

    override suspend fun generateLearningContent(request: GenerateLearningContentRequest): GeneratedLearningContent =
        withContext(Dispatchers.IO) {
            val goalInstruction = when (request.goal) {
                "VOCABULARY" -> "Generate 5 vocabulary flashcards (term, meaning, example sentence) from this " +
                    "material's key words, plus 2 short practice exercises."
                "GRAMMAR" -> "Extract 1-2 grammar concepts present in this material, explain the rule, give " +
                    "examples, and create 2 practice exercises."
                "SPEAKING" -> "Create a short conversation scenario and 3 discussion questions based on this " +
                    "material's topic, suitable for a role-play practice session."
                "EXAM_PREPARATION" -> "Create 3 exam-style questions (reading comprehension / short answer) " +
                    "based on this material, similar to Goethe/TELC exam formats."
                else -> "Create a short lesson summarizing the key learning points of this material."
            }
            val params = MessageCreateParams.builder()
                .model(exerciseModel)
                .maxTokens(1200L)
                .system(
                    "You are a German language tutor turning uploaded material into a ready-to-read lesson. " +
                        "$goalInstruction Target level: ${request.expectedLevel}. Respond with plain text only " +
                        "(no markdown headers, use simple line breaks) — this will be sent directly as a chat message.",
                )
                .addUserMessage(
                    "Topics: ${request.topics.joinToString(", ")}\n\nMaterial:\n${request.text}",
                )
                .build()

            val content = extractText(client.messages().create(params)) ?: "(no content returned)"
            GeneratedLearningContent(content = content, modelUsed = exerciseModel)
        }

    override suspend fun extractVocabulary(request: ExtractVocabularyRequest): List<ExtractedVocabularyItem> =
        withContext(Dispatchers.IO) {
            val params = MessageCreateParams.builder()
                .model(exerciseModel)
                .maxTokens(2000L)
                .system(
                    "You are a language-learning assistant extracting vocabulary from learning material. " +
                        "Identify up to ${request.maxItems} distinct German vocabulary words/phrases worth " +
                        "learning from this text, with as much detail as you can determine for each. " +
                        "$JSON_ONLY_INSTRUCTION Schema: a JSON array of objects: [{\"germanWord\": string, " +
                        "\"englishTranslation\": string, \"article\": string|null (der|die|das), " +
                        "\"pluralForm\": string|null, \"partOfSpeech\": string|null, \"exampleSentence\": string|null, " +
                        "\"cefrLevel\": string (A1|A2|B1|B2|C1|C2|UNKNOWN), \"topic\": string|null, " +
                        "\"synonyms\": string[], \"relatedWords\": string[]}]",
                )
                .addUserMessage("Content:\n${request.text}")
                .build()

            val raw = extractText(client.messages().create(params)) ?: return@withContext emptyList()

            runCatching { LENIENT_JSON.decodeFromString(ListSerializer(ExtractedVocabularyItemJson.serializer()), raw) }
                .map { items ->
                    items.map {
                        ExtractedVocabularyItem(
                            germanWord = it.germanWord,
                            englishTranslation = it.englishTranslation,
                            article = it.article,
                            pluralForm = it.pluralForm,
                            partOfSpeech = it.partOfSpeech,
                            exampleSentence = it.exampleSentence,
                            cefrLevel = it.cefrLevel,
                            topic = it.topic,
                            synonyms = it.synonyms,
                            relatedWords = it.relatedWords,
                        )
                    }
                }
                .getOrElse { emptyList() }
        }

    private fun extractText(response: Message): String? =
        response.content()
            .asSequence()
            .mapNotNull { block -> block.text().orElse(null)?.text() }
            .firstOrNull()
            ?.trim()

    private fun buildExercisePrompt(request: GenerateExerciseRequest): String =
        "German word: \"${request.germanWord}\" (English: \"${request.englishTranslation}\"), " +
            "CEFR level ${request.cefrLevel}."

    private fun practiceSystemPrompt(kind: PracticeExerciseKind): String {
        val schema = "{\"promptText\": string, \"correctAnswer\": string, \"hint\": string|null, \"explanation\": string|null, " +
            "\"options\": string[]|null, \"matchPairs\": {\"left\": string, \"right\": string}[]|null}"
        val task = when (kind) {
            PracticeExerciseKind.MULTIPLE_CHOICE ->
                "Create a multiple-choice German grammar exercise (e.g. choosing the correct article). Put the 3-4 " +
                    "candidate answers as plain text (no letter prefixes) in the \"options\" array, and set " +
                    "correctAnswer to the exact text of the correct option. Leave matchPairs null."
            PracticeExerciseKind.SCENARIO_CLOZE ->
                "Create a short real-life situational scenario (2-3 sentences) in German, with one sentence ending " +
                    "in a blank (______) for the target word, and correctAnswer being the missing word. Leave " +
                    "options and matchPairs null."
            PracticeExerciseKind.CLOZE_SENTENCE ->
                "Create one natural German sentence using the target word, with the target word replaced by a " +
                    "blank (______) in promptText, and correctAnswer being the missing word. Leave options and " +
                    "matchPairs null."
            PracticeExerciseKind.GRAMMAR_CHOICE ->
                "Create a 'choose the correct sentence' exercise for the given grammar topic: put 2-3 candidate " +
                    "sentences (one grammatically correct, the rest with a common mistake) as plain text in the " +
                    "\"options\" array, correctAnswer being the exact text of the correct option, and explanation " +
                    "describing the mistake in the wrong option(s). Leave matchPairs null."
            PracticeExerciseKind.PLURAL_FORM ->
                "Ask the learner for the plural form of the target German word, with correctAnswer being the " +
                    "correct plural form (with article). Leave options and matchPairs null."
            PracticeExerciseKind.TRANSLATE_TO_ENGLISH ->
                "Ask the learner to translate the target German word into English, with promptText showing the " +
                    "German word and correctAnswer being its English translation. Leave options and matchPairs null."
            PracticeExerciseKind.TRUE_FALSE ->
                "State a true-or-false fact about the target word or grammar topic (e.g. its meaning, article, or " +
                    "a usage rule), with correctAnswer being exactly \"True\" or \"False\" and explanation clarifying " +
                    "why. Leave options and matchPairs null (the caller fills in the True/False options itself)."
            PracticeExerciseKind.WORD_MATCHING ->
                "Create a word-matching exercise: pick 4-6 German words related to the target word or topic and " +
                    "pair each with its correct English translation in the \"matchPairs\" array (objects with " +
                    "\"left\" = the German word, \"right\" = its English translation). Set promptText to a short " +
                    "instruction (\"Match each German word to its English translation.\") and correctAnswer to an " +
                    "empty string. Leave options null."
        }
        return "You are a German language tutor creating practice exercises. $task $JSON_ONLY_INSTRUCTION Schema: $schema"
    }

    private fun buildPracticePrompt(request: GeneratePracticeExerciseRequest): String {
        val parts = mutableListOf("CEFR level: ${request.cefrLevel}", "Difficulty tier: ${request.difficultyTier}")
        request.germanWord?.let { parts += "Target German word: \"$it\"" }
        request.englishTranslation?.let { parts += "English translation: \"$it\"" }
        request.grammarTopic?.let { parts += "Grammar topic: $it" }
        if (request.topics.isNotEmpty()) parts += "Related topics: ${request.topics.joinToString(", ")}"
        request.sourceExcerpt?.let { parts += "Source material excerpt: \"$it\"" }
        return parts.joinToString("\n")
    }

    private fun evaluationSystemPrompt(): String =
        "You are a German language tutor evaluating a learner's free-form answer for vocabulary correctness, " +
            "correct article, correct spelling, and grammatical correctness. $JSON_ONLY_INSTRUCTION " +
            "Schema: {\"isCorrect\": boolean, \"correctedText\": string|null, \"feedback\": string, " +
            "\"qualityScore\": integer 0-5 (5=perfect, 3-4=correct with minor issues, 0-2=incorrect)}"

    private fun buildEvaluationPrompt(request: EvaluateFreeformAnswerRequest): String =
        "Exercise kind: ${request.exerciseKind}\nPrompt given to learner: \"${request.prompt}\"\n" +
            "Target word/topic: \"${request.targetWordOrTopic}\"\nCEFR level: ${request.cefrLevel}\n" +
            "Learner's answer: \"${request.learnerAnswer}\""

    private fun speakingSystemPrompt(scenarioDescription: String, cefrLevel: String): String =
        "You are simulating a real-life German conversation for language practice. Scenario: $scenarioDescription " +
            "Target CEFR level: $cefrLevel. After the learner's reply, correct any grammar/word-order mistakes, " +
            "explain the correction briefly, and continue the conversation with your next line in German. " +
            "$JSON_ONLY_INSTRUCTION Schema: {\"correctedReply\": string|null (null if no correction needed), " +
            "\"correctionExplanation\": string|null, \"aiNextLine\": string (your next line in German), " +
            "\"scenarioComplete\": boolean (true once the conversation reaches a natural end)}"

    private fun fallbackPracticeExercise(raw: String, model: String) =
        GeneratedPracticeExercise(promptText = raw, correctAnswer = "", modelUsed = model)

    private fun fallbackEvaluation(raw: String, model: String) =
        FreeformEvaluationResult(isCorrect = false, correctedText = null, feedback = raw, qualityScore = 2, modelUsed = model)

    private fun fallbackSpeakingTurn(raw: String, model: String) =
        SpeakingTurnResult(correctedReply = null, correctionExplanation = null, aiNextLine = raw, scenarioComplete = false, modelUsed = model)

    companion object {
        /** Cheapest current Claude tier — verify against Anthropic's pricing page at deploy time. */
        const val DEFAULT_EXERCISE_MODEL = "claude-haiku-4-5"

        /** Stronger tier for tasks needing real reasoning about grammatical correctness (confirmed with user). */
        const val DEFAULT_EVALUATION_MODEL = "claude-sonnet-5"
        const val DEFAULT_SPEAKING_MODEL = "claude-sonnet-5"

        fun create(
            apiKey: String,
            exerciseModel: String = DEFAULT_EXERCISE_MODEL,
            evaluationModel: String = DEFAULT_EVALUATION_MODEL,
            speakingModel: String = DEFAULT_SPEAKING_MODEL,
        ): AnthropicAiTutorPort {
            val client = AnthropicOkHttpClient.builder().apiKey(apiKey).build()
            return AnthropicAiTutorPort(client, exerciseModel, evaluationModel, speakingModel)
        }
    }
}
