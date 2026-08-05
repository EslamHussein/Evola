package evola.shared.local

import evola.shared.db.EvolaDatabase
import evola.shared.srs.MasterySrs

/** Per-lesson mastery aggregation shared by [LocalGoalsRepository] and [LocalLessonsRepository] —
 * mirrors the retired server's vocab/grammar progress computation (average of each item/topic's
 * MasterySrs-stage ratio, 0f when the lesson has none). */
internal fun EvolaDatabase.lessonVocabProgress(lessonId: String): Float {
    val items = vocabularyQueries.itemsByLesson(lessonId).executeAsList()
    if (items.isEmpty()) return 0f
    return items.mapNotNull { vocabularyQueries.progressForItem(LOCAL_USER, it.id).executeAsOneOrNull()?.mastery_state }
        .map { MasterySrs.STAGES.indexOf(it).coerceAtLeast(0) }
        .averageStageRatio()
}

internal fun EvolaDatabase.lessonGrammarProgress(lessonId: String): Float {
    val topics = grammarQueries.topicsByLesson(lessonId).executeAsList()
    if (topics.isEmpty()) return 0f
    return topics.mapNotNull { grammarQueries.progressForTopic(LOCAL_USER, it.id).executeAsOneOrNull()?.mastery_state }
        .map { MasterySrs.STAGES.indexOf(it).coerceAtLeast(0) }
        .averageStageRatio()
}

internal fun EvolaDatabase.vocabItemCount(lessonId: String): Int =
    vocabularyQueries.itemsByLesson(lessonId).executeAsList().size

internal fun EvolaDatabase.grammarTopicCount(lessonId: String): Int =
    grammarQueries.topicsByLesson(lessonId).executeAsList().size

private fun List<Int>.averageStageRatio(): Float =
    if (isEmpty()) 0f else map { it / (MasterySrs.STAGES.size - 1f) }.average().toFloat()
