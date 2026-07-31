package evola.tutoring.infrastructure

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.date
import org.jetbrains.exposed.sql.javatime.timestamp

internal object LearnerTutoringProfileTable : Table("learner_tutoring_profile") {
    val learnerId = uuid("learner_id")
    val activeLearningMode = varchar("active_learning_mode", 30)
    val updatedAt = timestamp("updated_at")
    override val primaryKey = PrimaryKey(learnerId)
}

internal object TutoringSessionsTable : Table("tutoring_sessions") {
    val id = uuid("id")
    val learnerId = uuid("learner_id")
    val mode = varchar("mode", 30)
    val focusVocabularyItemId = uuid("focus_vocabulary_item_id").nullable()
    val focusGrammarTopic = text("focus_grammar_topic").nullable()
    val status = varchar("status", 20)
    val startedAt = timestamp("started_at")
    val completedAt = timestamp("completed_at").nullable()
    override val primaryKey = PrimaryKey(id)
}

internal object DialogueTurnsTable : Table("dialogue_turns") {
    val id = uuid("id")
    val sessionId = uuid("session_id")
    val turnIndex = integer("turn_index")
    val role = varchar("role", 20)
    val exerciseKind = varchar("exercise_kind", 30).nullable()
    val content = text("content")
    val correctAnswer = text("correct_answer").nullable()
    val explanation = text("explanation").nullable()
    val wasCorrect = bool("was_correct").nullable()
    val createdAt = timestamp("created_at")
    override val primaryKey = PrimaryKey(id)
}

internal object TutoringWordContentTable : Table("tutoring_word_content") {
    val id = uuid("id")
    val vocabularyItemId = uuid("vocabulary_item_id")
    val kind = varchar("kind", 30)
    val difficultyTier = varchar("difficulty_tier", 20).nullable()
    val promptText = text("prompt_text")
    val correctAnswer = text("correct_answer")
    val hint = text("hint").nullable()
    val explanation = text("explanation").nullable()
    val options = text("options").nullable()
    val matchPairs = text("match_pairs").nullable()
    val modelUsed = varchar("model_used", 100)
    val timesServed = integer("times_served")
    val lastServedAt = timestamp("last_served_at").nullable()
    val createdAt = timestamp("created_at")
    override val primaryKey = PrimaryKey(id)
}

internal object TutoringGrammarContentTable : Table("tutoring_grammar_content") {
    val id = uuid("id")
    val grammarTopic = text("grammar_topic")
    val difficultyTier = varchar("difficulty_tier", 20)
    val promptText = text("prompt_text")
    val correctAnswer = text("correct_answer")
    val explanation = text("explanation").nullable()
    val options = text("options").nullable()
    val modelUsed = varchar("model_used", 100)
    val timesServed = integer("times_served")
    val lastServedAt = timestamp("last_served_at").nullable()
    val createdAt = timestamp("created_at")
    override val primaryKey = PrimaryKey(id)
}

internal object DailySessionPlansTable : Table("daily_session_plans") {
    val id = uuid("id")
    val learnerId = uuid("learner_id")
    val planDate = date("plan_date")
    val dueReviewCount = integer("due_review_count")
    val weakVocabularyItemIds = text("weak_vocabulary_item_ids")
    val grammarFocusTopic = text("grammar_focus_topic").nullable()
    val speakingScenarioTitle = text("speaking_scenario_title").nullable()
    val createdAt = timestamp("created_at")
    override val primaryKey = PrimaryKey(id)
}

internal object LearningSessionRunsTable : Table("learning_session_runs") {
    val id = uuid("id")
    val learnerId = uuid("learner_id")
    val budgetType = varchar("budget_type", 30)
    val budgetValue = integer("budget_value")
    val startedAt = timestamp("started_at")
    val endedAt = timestamp("ended_at").nullable()
    val questionsAsked = integer("questions_asked")
    val correctCount = integer("correct_count")
    val incorrectCount = integer("incorrect_count")
    val touchedVocabularyItemIds = text("touched_vocabulary_item_ids")
    val allowedKinds = text("allowed_kinds")
    val difficultyOverride = text("difficulty_override").nullable()
    override val primaryKey = PrimaryKey(id)
}
