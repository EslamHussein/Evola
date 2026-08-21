package evola.shared.feature.profile.data

import evola.database.AppDatabase
import evola.database.entity.DailyActivityEntity
import evola.database.entity.GoalEntity
import evola.database.entity.LessonEntity
import evola.database.entity.MaterialEntity
import evola.database.entity.UserSettingEntity
import evola.database.entity.VocabularyItemEntity
import evola.database.entity.VocabularyProgressEntity
import evola.database.inTransaction
import evola.shared.core.common.ApiResult
import evola.shared.core.common.DataError
import evola.shared.core.common.LOCAL_USER
import evola.shared.core.common.localJson
import evola.shared.core.common.nowMillis
import evola.shared.feature.profile.domain.BACKUP_SCHEMA_VERSION
import evola.shared.feature.profile.domain.BackupDailyActivity
import evola.shared.feature.profile.domain.BackupGoal
import evola.shared.feature.profile.domain.BackupLesson
import evola.shared.feature.profile.domain.BackupMaterial
import evola.shared.feature.profile.domain.BackupRepository
import evola.shared.feature.profile.domain.BackupSetting
import evola.shared.feature.profile.domain.BackupSnapshot
import evola.shared.feature.profile.domain.BackupVocabularyItem
import evola.shared.feature.profile.domain.BackupVocabularyProgress
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerializationException

/** Single-user: user is always [LOCAL_USER]. A JSON snapshot rather than a raw SQLite file copy -
 * sidesteps WAL/open-handle issues a live file copy would hit. [export]/[import] stay the
 * synchronous interface shape [BackupRepository] already had under SQLDelight (used at existing
 * non-suspend call sites); both bridge into Room's suspend-only DAOs via `runBlocking`, same
 * pattern as [LocalSettingsRepository.current]. */
class LocalBackupRepository(private val db: AppDatabase) : BackupRepository {

    override fun export(): String = runBlocking {
        val snapshot = BackupSnapshot(
            schemaVersion = BACKUP_SCHEMA_VERSION,
            exportedAtMillis = nowMillis(),
            goals = db.goalDao().selectAllForUser(LOCAL_USER).map {
                BackupGoal(it.id, it.goalText, it.title, it.nativeLanguage, it.isActive == 1L, it.createdAt, it.updatedAt)
            },
            materials = db.materialDao().selectAllForUser(LOCAL_USER).map {
                BackupMaterial(
                    it.id, it.goalId, it.filename, it.contentHash, it.status, it.mimeType, it.sizeBytes, it.pageCount,
                    it.organizationMode, it.aiInstructions, it.resourceType, it.contentText, it.inputTokens, it.outputTokens, it.createdAt,
                )
            },
            lessons = db.lessonDao().selectAllForUser(LOCAL_USER).map {
                BackupLesson(it.id, it.materialId, it.goalId, it.number, it.title, it.status, it.originKind, it.sourceLabel, it.sourceTextRef, it.createdAt)
            },
            vocabularyItems = db.vocabularyDao().selectAllItemsForUser(LOCAL_USER).map {
                BackupVocabularyItem(
                    it.id, it.lessonId, it.term, it.meaning, it.gender, it.exampleSentence, it.partOfSpeech, it.plural,
                    it.grammaticalCase, it.exampleSentenceTranslation, it.nativeMeaning, it.ipaPronunciation, it.relatedWords,
                    it.difficultyRating, it.frequencyRating, it.memoryTip, it.grammarNote, it.aiNote, it.createdAt,
                )
            },
            vocabularyProgress = db.vocabularyDao().selectAllProgressForUser(LOCAL_USER).map {
                BackupVocabularyProgress(
                    it.id, it.vocabularyItemId, it.status, it.correctStreak, it.incorrectStreak,
                    it.intervalIndex, it.nextReviewAt, it.lastSeenAt, it.isBookmarked, it.markedDifficult,
                )
            },
            dailyActivity = db.activityDao().selectAllForUser(LOCAL_USER).map {
                BackupDailyActivity(it.id, it.activityDate, it.completed)
            },
            settings = db.settingsDao().selectAllForUser(LOCAL_USER).map { BackupSetting(it.key, it.value) },
        )
        localJson.encodeToString(BackupSnapshot.serializer(), snapshot)
    }

    override fun import(json: String): ApiResult<Unit> = runBlocking {
        val snapshot = try {
            localJson.decodeFromString(BackupSnapshot.serializer(), json)
        } catch (e: SerializationException) {
            return@runBlocking ApiResult.Failure(DataError.Http(422, "This file isn't a valid Evola backup."))
        }
        if (snapshot.schemaVersion != BACKUP_SCHEMA_VERSION) {
            return@runBlocking ApiResult.Failure(DataError.Http(422, "This backup is from a different app version and can't be restored."))
        }

        db.inTransaction {
            // Wipe in dependency order, then reload - see Backup.sq's own comment on why this
            // doesn't rely on ON DELETE CASCADE.
            db.vocabularyDao().deleteAllProgressForUser(LOCAL_USER)
            db.vocabularyDao().deleteAllItemsForUser(LOCAL_USER)
            db.lessonDao().deleteAllForUser(LOCAL_USER)
            db.materialDao().deleteAllForUser(LOCAL_USER)
            db.goalDao().deleteAllForUser(LOCAL_USER)
            db.activityDao().deleteAllForUser(LOCAL_USER)
            db.settingsDao().deleteAllForUser(LOCAL_USER)

            snapshot.goals.forEach {
                db.goalDao().insert(GoalEntity(it.id, LOCAL_USER, it.goalText, it.title, it.nativeLanguage, if (it.isActive) 1L else 0L, it.createdAt, it.updatedAt))
            }
            snapshot.materials.forEach {
                db.materialDao().insert(
                    MaterialEntity(
                        it.id, LOCAL_USER, it.goalId, it.filename, it.contentHash, it.status, it.mimeType, it.sizeBytes, it.pageCount,
                        it.organizationMode, it.aiInstructions, it.resourceType, it.contentText, it.inputTokens, it.outputTokens, it.createdAt,
                    ),
                )
            }
            snapshot.lessons.forEach {
                db.lessonDao().insert(LessonEntity(it.id, it.materialId, it.goalId, it.number, it.title, it.status, it.originKind, it.sourceLabel, it.sourceTextRef, it.createdAt))
            }
            snapshot.vocabularyItems.forEach {
                db.vocabularyDao().insertItem(
                    VocabularyItemEntity(
                        it.id, it.lessonId, it.term, it.meaning, it.gender, it.exampleSentence, it.partOfSpeech, it.plural, it.grammaticalCase,
                        it.exampleSentenceTranslation, it.nativeMeaning, it.ipaPronunciation, it.relatedWords, it.difficultyRating,
                        it.frequencyRating, it.memoryTip, it.grammarNote, it.aiNote, it.createdAt,
                    ),
                )
            }
            snapshot.vocabularyProgress.forEach {
                db.vocabularyDao().insertProgress(
                    VocabularyProgressEntity(
                        it.id, LOCAL_USER, it.vocabularyItemId, it.status, it.correctStreak, it.incorrectStreak,
                        it.intervalIndex, it.nextReviewAt, it.lastSeenAt, it.isBookmarked, it.markedDifficult,
                    ),
                )
            }
            snapshot.dailyActivity.forEach { db.activityDao().upsert(DailyActivityEntity(it.id, LOCAL_USER, it.activityDate, it.completed)) }
            snapshot.settings.forEach { db.settingsDao().upsert(UserSettingEntity(LOCAL_USER, it.key, it.value)) }
        }
        ApiResult.Success(Unit)
    }
}
