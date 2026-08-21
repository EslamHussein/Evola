package evola.shared.core.common

import evola.database.AppDatabase
import evola.shared.core.common.srs.MasterySrs
import evola.shared.feature.vocabulary.domain.VocabularySrs

/** Room equivalents of the SQLDelight-era `LocalProgress.kt` helpers (since removed) - same shape,
 * against the Room DAOs every repository now uses. */
internal suspend fun AppDatabase.lessonVocabProgressRoom(lessonId: String): Float {
    val rows = vocabularyDao().statusByLesson(LOCAL_USER, lessonId)
    if (rows.isEmpty()) return 0f
    return rows.map { VocabularySrs.STATUSES.indexOf(it.status).coerceAtLeast(0) }
        .map { it / (VocabularySrs.STATUSES.size - 1f) }
        .average().toFloat()
}

internal suspend fun AppDatabase.lessonGrammarProgressRoom(lessonId: String): Float {
    val topics = grammarDao().topicsByLesson(lessonId)
    if (topics.isEmpty()) return 0f
    return topics.mapNotNull { grammarDao().progressForTopic(LOCAL_USER, it.id)?.masteryState }
        .map { MasterySrs.STAGES.indexOf(it).coerceAtLeast(0) }
        .averageStageRatioRoom()
}

internal suspend fun AppDatabase.vocabItemCountRoom(lessonId: String): Int =
    vocabularyDao().itemsByLesson(lessonId).size

internal suspend fun AppDatabase.grammarTopicCountRoom(lessonId: String): Int =
    grammarDao().topicsByLesson(lessonId).size

private fun List<Int>.averageStageRatioRoom(): Float =
    if (isEmpty()) 0f else map { it / (MasterySrs.STAGES.size - 1f) }.average().toFloat()
