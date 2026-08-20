package evola.shared.core.common

import evola.shared.db.EvolaDatabase
import evola.shared.core.common.srs.MasterySrs
import evola.shared.vocabulary.VocabularySrs

/** Per-lesson vocabulary progress: each item's current SRS status mapped to a 0..1 position on the
 * [VocabularySrs.STATUSES] ladder (unseen=0 .. mastered=1), averaged across every item in the
 * lesson — the same shape [lessonGrammarProgress]/[averageStageRatio] already uses against
 * [MasterySrs.STAGES]. 0f when the lesson has no words. */
internal fun EvolaDatabase.lessonVocabProgress(lessonId: String): Float {
    val rows = vocabularyQueries.statusByLesson(LOCAL_USER, lessonId).executeAsList()
    if (rows.isEmpty()) return 0f
    return rows.map { VocabularySrs.STATUSES.indexOf(it.status).coerceAtLeast(0) }
        .map { it / (VocabularySrs.STATUSES.size - 1f) }
        .average().toFloat()
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
