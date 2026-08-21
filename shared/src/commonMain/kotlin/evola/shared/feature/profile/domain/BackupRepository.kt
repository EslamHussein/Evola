package evola.shared.feature.profile.domain

import evola.shared.core.common.ApiResult
import kotlinx.serialization.Serializable

/** Bumped only if the exported shape changes incompatibly - [BackupRepository.import] refuses a
 * mismatched version outright rather than guessing, so a future format change fails loudly instead
 * of silently corrupting data. */
const val BACKUP_SCHEMA_VERSION = 1

@Serializable
data class BackupSnapshot(
    val schemaVersion: Int,
    val exportedAtMillis: Long,
    val goals: List<BackupGoal>,
    val materials: List<BackupMaterial>,
    val lessons: List<BackupLesson>,
    val vocabularyItems: List<BackupVocabularyItem>,
    val vocabularyProgress: List<BackupVocabularyProgress>,
    val dailyActivity: List<BackupDailyActivity>,
    val settings: List<BackupSetting>,
)

@Serializable
data class BackupGoal(
    val id: String, val goalText: String, val title: String?, val nativeLanguage: String,
    val isActive: Boolean, val createdAt: Long, val updatedAt: Long,
)

@Serializable
data class BackupMaterial(
    val id: String, val goalId: String, val filename: String, val contentHash: String, val status: String,
    val mimeType: String, val sizeBytes: Long, val pageCount: Long?, val organizationMode: String,
    val aiInstructions: String?, val resourceType: String?, val contentText: String?,
    val inputTokens: Long, val outputTokens: Long, val createdAt: Long,
)

@Serializable
data class BackupLesson(
    val id: String, val materialId: String, val goalId: String, val number: Long, val title: String,
    val status: String, val originKind: String, val sourceLabel: String?, val sourceTextRef: String?, val createdAt: Long,
)

@Serializable
data class BackupVocabularyItem(
    val id: String, val lessonId: String, val term: String, val meaning: String, val gender: String?,
    val exampleSentence: String?, val partOfSpeech: String?, val plural: String?, val grammaticalCase: String?,
    val exampleSentenceTranslation: String?, val nativeMeaning: String?, val ipaPronunciation: String?,
    val relatedWords: String?, val difficultyRating: String?, val frequencyRating: String?,
    val memoryTip: String?, val grammarNote: String?, val aiNote: String?, val createdAt: Long,
)

@Serializable
data class BackupVocabularyProgress(
    val id: String, val vocabularyItemId: String, val status: String, val correctStreak: Long,
    val incorrectStreak: Long, val intervalIndex: Long, val nextReviewAt: Long, val lastSeenAt: Long?,
    val isBookmarked: Long, val markedDifficult: Long,
)

@Serializable
data class BackupDailyActivity(val id: String, val activityDate: String, val completed: Long)

@Serializable
data class BackupSetting(val key: String, val value: String)

/**
 * Local backup/restore over a subset of the local database - a JSON snapshot rather
 * than a raw SQLite file copy, covering the data a learner would actually mourn losing: goals,
 * materials, lessons, vocabulary (items + progress), daily activity, and settings. Grammar tables
 * and in-progress session/queue state are deliberately excluded - regenerable/re-derivable, not
 * source data, kept out to keep this repository's surface area proportionate to what it protects.
 */
interface BackupRepository {
    fun export(): String
    fun import(json: String): ApiResult<Unit>
}
