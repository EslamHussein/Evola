package evola.tutoring.domain

import evola.core.kernel.LearnerId
import evola.core.kernel.LearningSessionRunId
import evola.core.kernel.VocabularyItemId
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LearningSessionRunTest {

    @Test
    fun `word count budget is exhausted once questionsAsked reaches the budget`() {
        val start = LearningSessionRun.start(
            LearningSessionRunId.new(), LearnerId.new(), SessionBudgetType.WORD_COUNT, budgetValue = 3,
        )
        var run = start
        assertFalse(run.isBudgetExhausted())
        repeat(2) { run = run.recordAnswer(wasCorrect = true, vocabularyItemId = VocabularyItemId.new()) }
        assertFalse(run.isBudgetExhausted())
        run = run.recordAnswer(wasCorrect = true, vocabularyItemId = VocabularyItemId.new())
        assertTrue(run.isBudgetExhausted())
    }

    @Test
    fun `duration budget is exhausted once enough time has elapsed`() {
        val run = LearningSessionRun.start(
            LearningSessionRunId.new(), LearnerId.new(), SessionBudgetType.DURATION_MINUTES, budgetValue = 10,
        )
        assertFalse(run.isBudgetExhausted(now = run.startedAt.plus(5, ChronoUnit.MINUTES)))
        assertTrue(run.isBudgetExhausted(now = run.startedAt.plus(10, ChronoUnit.MINUTES)))
        assertTrue(run.isBudgetExhausted(now = run.startedAt.plus(15, ChronoUnit.MINUTES)))
    }

    @Test
    fun `recordAnswer tracks distinct touched words and correct-incorrect counts`() {
        val start = LearningSessionRun.start(LearningSessionRunId.new(), LearnerId.new(), SessionBudgetType.WORD_COUNT, 5)
        val wordA = VocabularyItemId.new()
        val wordB = VocabularyItemId.new()
        val run = start.recordAnswer(true, wordA).recordAnswer(false, wordB).recordAnswer(true, wordA)

        assertTrue(run.questionsAsked == 3)
        assertTrue(run.correctCount == 2)
        assertTrue(run.incorrectCount == 1)
        assertTrue(run.touchedVocabularyItemIds.size == 2)
    }
}
