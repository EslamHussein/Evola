package evola.shared.materials

import kotlinx.serialization.Serializable

enum class MaterialStatus { UPLOADED, PROCESSING, READY, UNSUPPORTED_CONTENT, FAILED }

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
)

/** Mirrors the DB's `lesson_status` enum ("pending" | "ready" | "failed"). M4 only ever creates
 * lessons as "pending" - M6/M7 are what later flip a lesson to "ready" once it has real
 * vocabulary/grammar content. */
@Serializable
data class Lesson(
    val id: String,
    val materialId: String,
    val goalId: String,
    val number: Int,
    val title: String,
    val status: String,
)

@Serializable
data class MaterialDetail(
    val material: Material,
    val lessons: List<Lesson>,
)
