package evola.shared.local

import evola.shared.db.EvolaDatabase
import evola.shared.srs.MasterySrs

private const val GRADABLE_STAGES = 5 // stages 2..6 (Reverse/Partial Recall, Sentence, Translation, Free Production)

/** Per-lesson vocabulary progress = **partial credit**: each word contributes the fraction of its
 * 5 gradable stages ever answered correctly (so real practice moves the bar immediately, instead of
 * requiring a flawless all-5-stages pass for any credit). Averaged across every item in the lesson;
 * 0f when the lesson has no words. The [MasterySrs] mastery ladder still drives SRS scheduling — it
 * just no longer gates the visible progress. */
internal fun EvolaDatabase.lessonVocabProgress(lessonId: String): Float {
    val rows = vocabularyQueries.gradedProgressByLesson(lessonId).executeAsList()
    if (rows.isEmpty()) return 0f
    return rows.map { (it.correct_stages.toFloat() / GRADABLE_STAGES).coerceIn(0f, 1f) }.average().toFloat()
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
