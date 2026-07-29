package evola.vocabulary.infrastructure

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

internal object VocabularyItemsTable : Table("vocabulary_items") {
    val id = uuid("id")
    val germanWord = varchar("german_word", 255)
    val englishTranslation = varchar("english_translation", 255)
    val cefrLevel = varchar("cefr_level", 10)
    val partOfSpeech = varchar("part_of_speech", 50).nullable()
    val createdAt = timestamp("created_at")
    override val primaryKey = PrimaryKey(id)
}

internal object LearnerVocabularyStateTable : Table("learner_vocabulary_state") {
    val id = uuid("id")
    val learnerId = uuid("learner_id")
    val vocabularyItemId = uuid("vocabulary_item_id")
    val easinessFactor = double("easiness_factor")
    val intervalDays = integer("interval_days")
    val repetitions = integer("repetitions")
    val nextReviewAt = timestamp("next_review_at")
    val lastReviewedAt = timestamp("last_reviewed_at").nullable()
    val createdAt = timestamp("created_at")
    override val primaryKey = PrimaryKey(id)
}

internal object ReviewHistoryTable : Table("review_history") {
    val id = uuid("id")
    val learnerVocabularyStateId = uuid("learner_vocabulary_state_id")
    val reviewedAt = timestamp("reviewed_at")
    val learnerAnswer = text("learner_answer")
    val wasCorrect = bool("was_correct")
    val qualityScore = integer("quality_score")
    val efBefore = double("ef_before")
    val efAfter = double("ef_after")
    val intervalBefore = integer("interval_before")
    val intervalAfter = integer("interval_after")
    override val primaryKey = PrimaryKey(id)
}
