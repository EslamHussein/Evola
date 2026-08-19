package evola.shared.local

import evola.shared.core.ApiResult
import evola.shared.core.DataError
import evola.shared.db.EvolaDatabase
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException

/** Bumped only if the exported shape changes incompatibly - [BackupRepository.import] refuses a
 * mismatched version outright rather than guessing, so a future format change fails loudly instead
 * of silently corrupting data. */
private const val BACKUP_SCHEMA_VERSION = 1

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
 * Local backup/restore over a subset of [EvolaDatabase] - a JSON snapshot rather than a raw SQLite
 * file copy, covering the data a learner would actually mourn losing: goals, materials, lessons,
 * vocabulary (items + progress), daily activity, and settings. Grammar tables and in-progress
 * session/queue state are deliberately excluded - regenerable/re-derivable, not source data, kept
 * out to keep this repository's surface area proportionate to what it protects.
 */
interface BackupRepository {
    fun export(): String
    fun import(json: String): ApiResult<Unit>
}

/** Single-user: user is always [LOCAL_USER]. A JSON snapshot rather than a raw SQLite file copy -
 * sidesteps WAL/open-handle issues a live file copy would hit. */
class LocalBackupRepository(private val db: EvolaDatabase) : BackupRepository {

    override fun export(): String {
        val snapshot = BackupSnapshot(
            schemaVersion = BACKUP_SCHEMA_VERSION,
            exportedAtMillis = nowMillis(),
            goals = db.backupQueries.selectAllGoals(LOCAL_USER).executeAsList().map {
                BackupGoal(it.id, it.goal_text, it.title, it.native_language, it.is_active == 1L, it.created_at, it.updated_at)
            },
            materials = db.backupQueries.selectAllMaterials(LOCAL_USER).executeAsList().map {
                BackupMaterial(
                    it.id, it.goal_id, it.filename, it.content_hash, it.status, it.mime_type, it.size_bytes, it.page_count,
                    it.organization_mode, it.ai_instructions, it.resource_type, it.content_text, it.input_tokens, it.output_tokens, it.created_at,
                )
            },
            lessons = db.backupQueries.selectAllLessons(LOCAL_USER).executeAsList().map {
                BackupLesson(it.id, it.material_id, it.goal_id, it.number, it.title, it.status, it.origin_kind, it.source_label, it.source_text_ref, it.created_at)
            },
            vocabularyItems = db.backupQueries.selectAllVocabularyItems(LOCAL_USER).executeAsList().map {
                BackupVocabularyItem(
                    it.id, it.lesson_id, it.term, it.meaning, it.gender, it.example_sentence, it.part_of_speech, it.plural,
                    it.grammatical_case, it.example_sentence_translation, it.native_meaning, it.ipa_pronunciation, it.related_words,
                    it.difficulty_rating, it.frequency_rating, it.memory_tip, it.grammar_note, it.ai_note, it.created_at,
                )
            },
            vocabularyProgress = db.backupQueries.selectAllVocabularyProgress(LOCAL_USER).executeAsList().map {
                BackupVocabularyProgress(
                    it.id, it.vocabulary_item_id, it.status, it.correct_streak, it.incorrect_streak,
                    it.interval_index, it.next_review_at, it.last_seen_at, it.is_bookmarked, it.marked_difficult,
                )
            },
            dailyActivity = db.backupQueries.selectAllDailyActivity(LOCAL_USER).executeAsList().map {
                BackupDailyActivity(it.id, it.activity_date, it.completed)
            },
            settings = db.backupQueries.selectAllUserSettings(LOCAL_USER).executeAsList().map { BackupSetting(it.key, it.value_) },
        )
        return localJson.encodeToString(BackupSnapshot.serializer(), snapshot)
    }

    override fun import(json: String): ApiResult<Unit> {
        val snapshot = try {
            localJson.decodeFromString(BackupSnapshot.serializer(), json)
        } catch (e: SerializationException) {
            return ApiResult.Failure(DataError.Http(422, "This file isn't a valid Evola backup."))
        }
        if (snapshot.schemaVersion != BACKUP_SCHEMA_VERSION) {
            return ApiResult.Failure(DataError.Http(422, "This backup is from a different app version and can't be restored."))
        }

        db.transaction {
            // Wipe in dependency order, then reload - see Backup.sq's own comment on why this
            // doesn't rely on ON DELETE CASCADE.
            db.backupQueries.deleteAllVocabularyProgress(LOCAL_USER)
            db.backupQueries.deleteAllVocabularyItemsForUser(LOCAL_USER)
            db.backupQueries.deleteAllLessonsForUser(LOCAL_USER)
            db.backupQueries.deleteAllMaterials(LOCAL_USER)
            db.backupQueries.deleteAllGoals(LOCAL_USER)
            db.backupQueries.deleteAllDailyActivity(LOCAL_USER)
            db.backupQueries.deleteAllUserSettings(LOCAL_USER)

            snapshot.goals.forEach {
                db.backupQueries.restoreGoal(it.id, LOCAL_USER, it.goalText, it.title, it.nativeLanguage, if (it.isActive) 1L else 0L, it.createdAt, it.updatedAt)
            }
            snapshot.materials.forEach {
                db.backupQueries.restoreMaterial(
                    it.id, LOCAL_USER, it.goalId, it.filename, it.contentHash, it.status, it.mimeType, it.sizeBytes, it.pageCount,
                    it.organizationMode, it.aiInstructions, it.resourceType, it.contentText, it.inputTokens, it.outputTokens, it.createdAt,
                )
            }
            snapshot.lessons.forEach {
                db.backupQueries.restoreLesson(it.id, it.materialId, it.goalId, it.number, it.title, it.status, it.originKind, it.sourceLabel, it.sourceTextRef, it.createdAt)
            }
            snapshot.vocabularyItems.forEach {
                db.backupQueries.restoreVocabularyItem(
                    it.id, it.lessonId, it.term, it.meaning, it.gender, it.exampleSentence, it.partOfSpeech, it.plural, it.grammaticalCase,
                    it.exampleSentenceTranslation, it.nativeMeaning, it.ipaPronunciation, it.relatedWords, it.difficultyRating,
                    it.frequencyRating, it.memoryTip, it.grammarNote, it.aiNote, it.createdAt,
                )
            }
            snapshot.vocabularyProgress.forEach {
                db.backupQueries.restoreVocabularyProgress(
                    it.id, LOCAL_USER, it.vocabularyItemId, it.status, it.correctStreak, it.incorrectStreak,
                    it.intervalIndex, it.nextReviewAt, it.lastSeenAt, it.isBookmarked, it.markedDifficult,
                )
            }
            snapshot.dailyActivity.forEach { db.backupQueries.restoreDailyActivity(it.id, LOCAL_USER, it.activityDate, it.completed) }
            snapshot.settings.forEach { db.backupQueries.restoreUserSetting(LOCAL_USER, it.key, it.value) }
        }
        return ApiResult.Success(Unit)
    }
}
