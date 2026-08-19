package evola.composeapp.lessons

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import evola.shared.ai.AnthropicClient
import evola.shared.db.EvolaDatabase
import evola.shared.local.LOCAL_USER
import evola.shared.local.LocalSettingsRepository
import evola.shared.local.LocalVocabularyRepository
import evola.shared.vocabulary.VocabularyCard
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import pro.respawn.flowmvi.test.subscribeAndTest
import pro.respawn.flowmvi.test.wait
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Same convention as [evola.composeapp.main.HomeContainerTest] and [VocabularyListContainerTest]:
 * a real [LocalVocabularyRepository] backed by an in-memory SQLite [EvolaDatabase], driven through
 * [VocabularySessionContainer.store] via the official `pro.respawn.flowmvi:test` DSL - never a
 * mocked repository. This is the actual spaced-repetition session state machine (New card -> Practice
 * card -> grading -> next card / session-complete), so assertions follow the real SRS transitions
 * already proven in `LocalVocabularyRepositoryTest`. */
class VocabularySessionContainerTest {

    private fun setup(itemCount: Int = 3): Pair<LocalVocabularyRepository, EvolaDatabase> {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        EvolaDatabase.Schema.create(driver)
        val db = EvolaDatabase(driver)
        db.goalsQueries.insert("g1", LOCAL_USER, "Learn German", "t", "en", 1L, 0L, 0L)
        db.materialsQueries.insert("m1", LOCAL_USER, "g1", "f.pdf", "h", "READY", "application/pdf", 1L, null, "auto", null, null, "txt", 0L)
        db.lessonsQueries.insert("l1", "m1", "g1", 1L, "Lesson 1", "ready", null, 0L)
        repeat(itemCount) { i ->
            val id = "v$i"
            db.vocabularyQueries.insertItem(
                id, "l1", "Wort$i", "word$i", "der", "Das Wort$i ist gut.",
                null, null, null, null, null, null, null, null, null, null, null, 0L,
            )
            db.vocabularyQueries.insertProgress("p$i", LOCAL_USER, id, "unseen", 0L, 0L, 0L, 0L, null, 0L, 0L)
        }
        val anthropic = AnthropicClient(MockEngine { respond("{\"content\":[]}", HttpStatusCode.OK) }) { "sk-test" }
        val settingsRepository = LocalSettingsRepository(db)
        return LocalVocabularyRepository(db, anthropic, settingsRepository) to db
    }

    private fun container(repository: LocalVocabularyRepository, source: VocabSessionSource = VocabSessionSource.Lesson("l1")) =
        VocabularySessionContainer(source, repository)

    @Test
    fun `starting a session shows the first word as a New card with correct content`() = runTest {
        val (repository, _) = setup(itemCount = 2)
        container(repository).store.subscribeAndTest {
            val state = assertIs<VocabularySessionUiState.InProgress>(states.value)
            val card = assertIs<VocabularyCard.New>(state.session.card)
            assertEquals("v0", card.itemId)
            assertEquals("Wort0", card.term)
            assertEquals("word0", card.meaning)
            assertEquals(2, state.session.totalWords)
        }
    }

    @Test
    fun `two correct self-grades graduate the word to review and finish the session`() = runTest {
        val (repository, db) = setup(itemCount = 1)
        container(repository).store.subscribeAndTest {
            val initial = assertIs<VocabularySessionUiState.InProgress>(states.value)
            val sessionId = initial.session.sessionId
            val itemId = initial.session.card.itemId

            VocabularySessionIntent.SubmitStartLearning(sessionId, itemId) resultsIn {
                wait()
                val afterStart = assertIs<VocabularySessionUiState.InProgress>(states.value)
                assertIs<VocabularyCard.Practice>(afterStart.session.card)
                assertEquals(itemId, afterStart.session.card.itemId)
                assertEquals("introduced", db.vocabularyQueries.progressForItem(LOCAL_USER, itemId).executeAsOne().status)
            }

            val practiceSessionId = (states.value as VocabularySessionUiState.InProgress).session.sessionId

            // First correct: introduced -> learning, not graduated - the only word in the lesson
            // requeues as a Practice card again rather than exhausting the session.
            VocabularySessionIntent.SubmitSelfGrade(practiceSessionId, itemId, correct = true) resultsIn {
                wait()
                val graded = assertIs<VocabularySessionUiState.InProgress>(states.value)
                assertEquals(true, graded.answered?.correct)
                assertTrue(graded.canUndo)
                assertIs<VocabularyCard.Practice>(graded.answered?.next?.card)
                assertEquals("learning", db.vocabularyQueries.progressForItem(LOCAL_USER, itemId).executeAsOne().status)
            }

            val firstAnswer = (states.value as VocabularySessionUiState.InProgress).answered!!
            VocabularySessionIntent.ContinueToNext(practiceSessionId, firstAnswer.next) resultsIn {
                wait()
                val advanced = assertIs<VocabularySessionUiState.InProgress>(states.value)
                assertIs<VocabularyCard.Practice>(advanced.session.card)
                // A freshly advanced InProgress state carries no stale answer/undo flag.
                assertEquals(false, advanced.canUndo)
                assertEquals(null, advanced.answered)
            }

            val secondSessionId = (states.value as VocabularySessionUiState.InProgress).session.sessionId

            // Second correct: learning -> review - graduated, queue exhausted for this one-word lesson.
            VocabularySessionIntent.SubmitSelfGrade(secondSessionId, itemId, correct = true) resultsIn {
                wait()
                val graded = assertIs<VocabularySessionUiState.InProgress>(states.value)
                assertEquals(true, graded.answered?.correct)
                assertNull(graded.answered?.next)
                assertEquals("review", db.vocabularyQueries.progressForItem(LOCAL_USER, itemId).executeAsOne().status)
            }

            val secondAnswer = (states.value as VocabularySessionUiState.InProgress).answered!!
            VocabularySessionIntent.ContinueToNext(secondSessionId, secondAnswer.next) resultsIn {
                wait()
                val summary = assertIs<VocabularySessionUiState.Summary>(states.value)
                assertEquals(1, summary.summary.wordsLearned)
                assertEquals(100.0, summary.summary.accuracy)
            }
        }
    }

    @Test
    fun `a wrong self-grade on a due review demotes the word and does not repeat it immediately`() = runTest {
        val (repository, db) = setup(itemCount = 3)
        // v0 already due for review, so the session opens straight on its Practice card.
        db.vocabularyQueries.updateProgress("review", 2L, 0L, 1L, 0L, 0L, LOCAL_USER, "v0")

        container(repository).store.subscribeAndTest {
            val initial = assertIs<VocabularySessionUiState.InProgress>(states.value)
            val card = assertIs<VocabularyCard.Practice>(initial.session.card)
            assertEquals("v0", card.itemId)
            val sessionId = initial.session.sessionId

            VocabularySessionIntent.SubmitSelfGrade(sessionId, "v0", correct = false) resultsIn {
                wait()
                val graded = assertIs<VocabularySessionUiState.InProgress>(states.value)
                assertEquals(false, graded.answered?.correct)
                assertEquals("learning", db.vocabularyQueries.progressForItem(LOCAL_USER, "v0").executeAsOne().status)
                assertEquals(0L, db.vocabularyQueries.progressForItem(LOCAL_USER, "v0").executeAsOne().interval_index)

                // The demoted word must not resurface as the very next card - other New cards come first.
                val nextItemId = graded.answered?.next?.card?.itemId
                assertTrue(nextItemId != null && nextItemId != "v0")
            }
        }
    }

    @Test
    fun `starting a session on a lesson with no vocabulary yields an Error state, not a crash`() = runTest {
        val (repository, _) = setup(itemCount = 0)
        container(repository).store.subscribeAndTest {
            val state = assertIs<VocabularySessionUiState.Error>(states.value)
            assertTrue(state.message.isNotBlank())
        }
    }
}
