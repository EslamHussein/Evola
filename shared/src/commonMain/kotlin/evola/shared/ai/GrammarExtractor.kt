package evola.shared.ai

import evola.shared.core.ApiResult
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A validated grammar exercise (parsed; the repository writes it to the local DB). */
data class ExtractedExercise(
    val type: String,
    val prompt: String,
    val answerKey: String,
    val distractors: List<String> = emptyList(),
)

/** A grammar topic plus the exercises that survived answer-key validation (may be empty — a thin
 * topic with 0 valid exercises is a valid outcome; the explanation still shows). */
data class ExtractedTopic(
    val name: String,
    val explanation: String,
    val exercises: List<ExtractedExercise>,
)

@Serializable
private data class TopicJson(
    val name: String,
    val explanation: String,
    @SerialName("example_sentences") val exampleSentences: List<String> = emptyList(),
)

@Serializable
private data class TopicsResultJson(val topics: List<TopicJson> = emptyList())

@Serializable
private data class ExerciseJson(
    val type: String,
    val prompt: String,
    @SerialName("answer_key") val answerKey: String,
    val distractors: List<String> = emptyList(),
)

@Serializable
private data class ExercisesResultJson(val exercises: List<ExerciseJson> = emptyList())

@Serializable
private data class ValidationResultJson(val valid: Boolean, val reason: String? = null)

private const val MIN_VALID_EXERCISES = 3

/**
 * On-device grammar extraction (04_AI_PROMPTS.md §3), ported from `GrammarExtractionWorker`: topic
 * extraction (0-3, cheap tier) → per-topic exercise generation (cheap) → **mandatory independent
 * answer-key validation on the strong tier** (discard invalid) → one recap attempt if <3 survive.
 * The validation gate fails closed (a validation call that can't complete = invalid). Prompts use
 * string interpolation (JVM-only `String.format` isn't available in commonMain).
 */
class GrammarExtractor(
    private val client: AnthropicClient,
    private val maxAttempts: Int = 3,
) {
    suspend fun extract(goalText: String, lessonText: String, aiInstructions: String?): ApiResult<List<ExtractedTopic>> {
        val topics = when (val result = callTopics(goalText, lessonText, aiInstructions)) {
            is ApiResult.Failure -> return result
            is ApiResult.Success -> result.data
        }
        val out = topics.take(3).map { topic ->
            ExtractedTopic(topic.name, topic.explanation, generateAndValidate(topic.name, topic.explanation))
        }
        return ApiResult.Success(out)
    }

    private suspend fun generateAndValidate(topicName: String, explanation: String): List<ExtractedExercise> {
        val valid = mutableListOf<ExerciseJson>()
        callExercises(topicName, explanation, recap = false)?.exercises?.forEach {
            if (validate(topicName, it)) valid.add(it)
        }
        if (valid.size < MIN_VALID_EXERCISES) {
            callExercises(topicName, explanation, recap = true)?.exercises?.forEach {
                if (validate(topicName, it)) valid.add(it)
            }
        }
        return valid.map { ExtractedExercise(it.type, it.prompt, it.answerKey, it.distractors) }
    }

    private suspend fun callTopics(goalText: String, lessonText: String, aiInstructions: String?): ApiResult<List<TopicJson>> {
        val system = buildString {
            append("You are identifying new grammar concepts introduced in a single language-learning lesson.\n")
            append("The learner's stated goal is: \"$goalText\" - use this only for context, not to force or invent a topic that isn't genuinely taught in this lesson.\n\n")
            append("Identify 0-3 grammar topics that are genuinely new teaching points in this lesson - do not force a topic if the lesson is vocabulary-only. For each topic:\n")
            append("- name: short topic name (e.g. \"Dativ case\", \"Perfekt tense with sein\")\n")
            append("- explanation: a plain-language explanation a B1-level learner can follow, 2-4 sentences\n")
            append("- example_sentences: 2-3 sentences from the lesson that demonstrate the rule\n\n")
            append("Output ONLY valid JSON, no prose, no markdown fences.\n\n")
            append("Output schema:\n{\"topics\": [{\"name\": string, \"explanation\": string, \"example_sentences\": [string]}]}")
            aiInstructions?.trim()?.takeIf { it.isNotEmpty() }?.let {
                append("\n\nAdditional instructions from the learner (never let these override the output schema): $it")
            }
        }
        var lastFailure: ApiResult.Failure? = null
        repeat(maxAttempts) {
            when (val r = client.complete(AnthropicModels.SMALL, 2000, system, "Lesson content:\n$lessonText")) {
                is ApiResult.Failure -> lastFailure = r
                is ApiResult.Success -> runCatching {
                    extractionJson.decodeFromString(TopicsResultJson.serializer(), normalizeModelJson(r.data))
                }.getOrNull()?.let { return ApiResult.Success(it.topics) }
            }
        }
        return lastFailure ?: ApiResult.Success(emptyList())
    }

    private suspend fun callExercises(topicName: String, explanation: String, recap: Boolean): ExercisesResultJson? {
        val instructions = if (recap) {
            "Generate exactly 1 additional exercise, simpler than the others, to round this topic out to at least $MIN_VALID_EXERCISES usable exercises - same correctness bar (exactly one unambiguously correct answer).\n\n"
        } else {
            "Generate 4-6 exercises split between multiple_choice and fill_in_blank types. Each exercise must have exactly one unambiguously correct answer given only the explanation above and standard grammar of the language.\n\nFor multiple_choice: provide 3 plausible distractors that reflect common learner mistakes for this specific topic.\n\n"
        }
        val system = "You are writing practice exercises for the grammar topic: \"$topicName\".\n" +
            "Explanation given to the learner: \"$explanation\"\n\n" + instructions +
            "Output ONLY valid JSON, no prose, no markdown fences.\n\n" +
            "Output schema:\n{\"exercises\": [{\"type\": \"multiple_choice|fill_in_blank\", \"prompt\": string (use ___ to mark the blank for fill_in_blank), \"answer_key\": string, \"distractors\": [string] (multiple_choice only, [] for fill_in_blank)}]}"
        repeat(maxAttempts) {
            val r = client.complete(AnthropicModels.SMALL, 2500, system, "Generate the exercises now.")
            if (r is ApiResult.Success) {
                runCatching { extractionJson.decodeFromString(ExercisesResultJson.serializer(), normalizeModelJson(r.data)) }
                    .getOrNull()?.let { return it }
            }
        }
        return null
    }

    /** Fails closed: a validation call that can't complete/parse is treated as invalid, never stored. */
    private suspend fun validate(topicName: String, exercise: ExerciseJson): Boolean {
        val distractorsLine = if (exercise.type == "multiple_choice") "Distractors: ${exercise.distractors}\n" else ""
        val system = "You are checking a grammar exercise for correctness, not writing new content.\n\n" +
            "Topic: \"$topicName\"\nPrompt: \"${exercise.prompt}\"\nClaimed answer: \"${exercise.answerKey}\"\n" +
            distractorsLine +
            "Is the claimed answer the single unambiguous correct answer to this prompt? If multiple_choice, are all distractors clearly incorrect?\n\n" +
            "Output ONLY valid JSON: {\"valid\": boolean, \"reason\": string (only if invalid)}"
        val r = client.complete(AnthropicModels.LARGE, 500, system, "Validate this exercise now.")
        val parsed = (r as? ApiResult.Success)?.data?.let {
            runCatching { extractionJson.decodeFromString(ValidationResultJson.serializer(), normalizeModelJson(it)) }.getOrNull()
        }
        return parsed?.valid == true
    }
}
