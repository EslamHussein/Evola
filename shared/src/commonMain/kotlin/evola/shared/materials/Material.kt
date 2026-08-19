package evola.shared.materials

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class MaterialStatus { UPLOADED, PROCESSING, READY, UNSUPPORTED_CONTENT, FAILED }

/** A material stops polling once it reaches one of these - shared by every poll-until-terminal
 * ViewModel (MaterialDetail, Processing) so the definition of "done" can't drift between them. */
val MATERIAL_TERMINAL_STATUSES = setOf(MaterialStatus.READY, MaterialStatus.FAILED, MaterialStatus.UNSUPPORTED_CONTENT)

@Serializable
data class Material(
    val id: String,
    val userId: String,
    val goalId: String,
    val filename: String,
    val contentHash: String,
    val status: MaterialStatus,
    val mimeType: String,
    val sizeBytes: Long,
    val pageCount: Int? = null,
    @SerialName("input_tokens") val inputTokens: Long = 0,
    @SerialName("output_tokens") val outputTokens: Long = 0,
    @SerialName("lessons_ready") val lessonsReady: Int = 0,
    @SerialName("lessons_total") val lessonsTotal: Int = 0,
)

/** Mirrors the DB's `lesson_status` enum ("pending" | "ready" | "failed"). M4 only ever creates
 * lessons as "pending" - M6/M7 are what later flip a lesson to "ready" once it has real
 * vocabulary/grammar content. Grammar/reading/exercises counts stay honestly 0 until M7/M8/M9
 * populate them - not a fake placeholder, an accurate "not built yet" signal (see Resource
 * Details' meta-stat row in the design handoff). */
@Serializable
data class Lesson(
    val id: String,
    val materialId: String,
    val goalId: String,
    val number: Int,
    val title: String,
    val status: String,
    @SerialName("vocab_count") val vocabCount: Int = 0,
    @SerialName("vocab_progress") val vocabProgress: Float = 0f,
    @SerialName("grammar_count") val grammarCount: Int = 0,
    @SerialName("grammar_progress") val grammarProgress: Float = 0f,
    @SerialName("reading_count") val readingCount: Int = 0,
    @SerialName("exercises_count") val exercisesCount: Int = 0,
) {
    /** Per 01_PRODUCT_SPEC.md §1.7: average of vocab/grammar progress, or vocab alone when the
     * lesson has no grammar topics yet - branches on grammarCount (a real topic count), not on
     * grammarProgress == 0f, which is indistinguishable from "a real topic sitting at 0% mastery". */
    val completionPct: Float get() = if (grammarCount == 0) vocabProgress else (vocabProgress + grammarProgress) / 2f
}

@Serializable
data class MaterialDetail(
    val material: Material,
    val lessons: List<Lesson>,
)
