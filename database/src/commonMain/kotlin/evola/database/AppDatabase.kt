package evola.database

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import evola.database.dao.AchievementDao
import evola.database.dao.ActivityDao
import evola.database.dao.GermanNounDao
import evola.database.dao.GoalDao
import evola.database.dao.GrammarDao
import evola.database.dao.LessonDao
import evola.database.dao.MaterialDao
import evola.database.dao.SettingsDao
import evola.database.dao.VocabularyDao
import evola.database.entity.AchievementEntity
import evola.database.entity.CurriculumLessonView
import evola.database.entity.DailyActivityEntity
import evola.database.entity.ExtractionJobEntity
import evola.database.entity.GermanNounEntity
import evola.database.entity.GoalEntity
import evola.database.entity.GrammarExerciseEntity
import evola.database.entity.GrammarExtractionJobEntity
import evola.database.entity.GrammarProgressEntity
import evola.database.entity.GrammarSessionAnswerEntity
import evola.database.entity.GrammarSessionEntity
import evola.database.entity.GrammarTopicEntity
import evola.database.entity.LessonEntity
import evola.database.entity.LessonVocabularyItemEntity
import evola.database.entity.LessonVocabularyView
import evola.database.entity.MaterialEntity
import evola.database.entity.StreakFreezeDateEntity
import evola.database.entity.UserSettingEntity
import evola.database.entity.VocabularyItemEntity
import evola.database.entity.VocabularyProgressEntity
import evola.database.entity.VocabularySessionEntity
import evola.database.entity.VocabularySessionQueueEntity

@Database(
    entities = [
        GoalEntity::class,
        MaterialEntity::class,
        LessonEntity::class,
        VocabularyItemEntity::class,
        VocabularyProgressEntity::class,
        LessonVocabularyItemEntity::class,
        VocabularySessionEntity::class,
        VocabularySessionQueueEntity::class,
        GrammarTopicEntity::class,
        GrammarExerciseEntity::class,
        GrammarProgressEntity::class,
        GrammarSessionEntity::class,
        GrammarSessionAnswerEntity::class,
        GrammarExtractionJobEntity::class,
        AchievementEntity::class,
        DailyActivityEntity::class,
        StreakFreezeDateEntity::class,
        ExtractionJobEntity::class,
        UserSettingEntity::class,
        GermanNounEntity::class,
    ],
    views = [CurriculumLessonView::class, LessonVocabularyView::class],
    version = 1,
    exportSchema = true,
)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun goalDao(): GoalDao
    abstract fun materialDao(): MaterialDao
    abstract fun lessonDao(): LessonDao
    abstract fun vocabularyDao(): VocabularyDao
    abstract fun grammarDao(): GrammarDao
    abstract fun achievementDao(): AchievementDao
    abstract fun activityDao(): ActivityDao
    abstract fun settingsDao(): SettingsDao
    abstract fun germanNounDao(): GermanNounDao
}

@Suppress("KotlinNoActualForExpect")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}

internal const val DATABASE_FILE_NAME = "evola_room.db"
