package evola.composeapp.feature.vocabulary.vm

import evola.composeapp.core.database.testAppDatabase
import evola.database.AppDatabase
import evola.database.entity.GoalEntity
import evola.database.entity.LessonEntity
import evola.database.entity.MaterialEntity
import evola.database.entity.VocabularyItemEntity
import evola.database.entity.VocabularyProgressEntity
import evola.shared.core.network.AnthropicClient
import evola.shared.core.common.LOCAL_USER
import evola.shared.feature.profile.data.LocalSettingsRepository
import evola.shared.feature.vocabulary.data.LocalVocabularyRepository
import evola.shared.feature.vocabulary.domain.VocabularyCard
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.orbitmvi.orbit.test.testWithInternalState
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Same convention as [evola.composeapp.feature.home.vm.HomeViewModelTest] and [VocabularyListViewModelTest]:
 * a real [LocalVocabularyRepository] backed by an in-memory Room database, driven through
 * [VocabularySessionViewModel] via the official `org.orbit-mvi:orbit-test` DSL - never a mocked
 * repository. This is the actual spaced-repetition session state machine (New card -> Practice
 * card -> grading -> next card / session-complete), so assertions follow the real SRS transitions
 * already proven in `LocalVocabularyRepositoryTest`. Robolectric because Room's Android database
 * builder needs a real `Context` - no test here mutates settings, so the repository and the
 * ViewModel's own settings dependency each get an independent in-memory Room database rather than
 * sharing one. */
@RunWith(RobolectricTestRunner::class)
class VocabularySessionViewModelTest {

    private suspend fun setup(itemCount: Int = 3): Pair<LocalVocabularyRepository, AppDatabase> {
        val db = testAppDatabase()
        db.goalDao().insert(GoalEntity("g1", LOCAL_USER, "Learn German", "t", "en", 1L, 0L, 0L))
        db.materialDao().insert(MaterialEntity("m1", LOCAL_USER, "g1", "f.pdf", "h", "READY", "application/pdf", 1L, null, "auto", null, null, "txt", 0L, 0L, 0L))
        db.lessonDao().insert(LessonEntity("l1", "m1", "g1", 1L, "Lesson 1", "ready", "curriculum", null, null, 0L))
        repeat(itemCount) { i ->
            val id = "v$i"
            db.vocabularyDao().insertItem(
                VocabularyItemEntity(id, "l1", "Wort$i", "word$i", "der", "Das Wort$i ist gut.", null, null, null, null, null, null, null, null, null, null, null, null, 0L),
            )
            db.vocabularyDao().insertProgress(VocabularyProgressEntity("p$i", LOCAL_USER, id, "unseen", 0L, 0L, 0L, 0L, null, 0L, 0L))
        }
        val anthropic = AnthropicClient(MockEngine { respond("{\"content\":[]}", HttpStatusCode.OK) }) { "sk-test" }
        val settingsRepository = LocalSettingsRepository(testAppDatabase())
        return LocalVocabularyRepository(db, anthropic, settingsRepository) to db
    }

    private fun viewModel(repository: LocalVocabularyRepository, db: AppDatabase, source: VocabSessionSource = VocabSessionSource.Lesson("l1")) =
        VocabularySessionViewModel(source, repository, LocalSettingsRepository(testAppDatabase()))

    @Test
    fun `starting a session shows the first word as a New card with correct content`() = runTest {
        val (repository, db) = setup(itemCount = 2)
        viewModel(repository, db).testWithInternalState(this, VocabularySessionUiState.Loading) {
            runOnCreate()
            val state = assertIs<VocabularySessionUiState.InProgress>(awaitInternalState())
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
        viewModel(repository, db).testWithInternalState(this, VocabularySessionUiState.Loading) {
            runOnCreate()
            val initial = assertIs<VocabularySessionUiState.InProgress>(awaitInternalState())
            val sessionId = initial.session.sessionId
            val itemId = initial.session.card.itemId

            containerHost.submitStartLearning(sessionId, itemId)
            val afterStart = assertIs<VocabularySessionUiState.InProgress>(awaitInternalState())
            assertIs<VocabularyCard.Practice>(afterStart.session.card)
            assertEquals(itemId, afterStart.session.card.itemId)
            assertEquals("introduced", db.vocabularyDao().progressForItem(LOCAL_USER, itemId)!!.status)

            val practiceSessionId = afterStart.session.sessionId

            // First correct: introduced -> learning, not graduated - the only word in the lesson
            // requeues as a Practice card again rather than exhausting the session.
            containerHost.submitSelfGrade(practiceSessionId, itemId, correct = true)
            val graded = assertIs<VocabularySessionUiState.InProgress>(awaitInternalState())
            assertEquals(true, graded.answered?.correct)
            assertTrue(graded.canUndo)
            assertIs<VocabularyCard.Practice>(graded.answered?.next?.card)
            assertEquals("learning", db.vocabularyDao().progressForItem(LOCAL_USER, itemId)!!.status)

            val firstAnswer = graded.answered!!
            containerHost.continueToNext(practiceSessionId, firstAnswer.next)
            val advanced = assertIs<VocabularySessionUiState.InProgress>(awaitInternalState())
            assertIs<VocabularyCard.Practice>(advanced.session.card)
            // A freshly advanced InProgress state carries no stale answer/undo flag.
            assertEquals(false, advanced.canUndo)
            assertEquals(null, advanced.answered)

            val secondSessionId = advanced.session.sessionId

            // Second correct: learning -> review - graduated, queue exhausted for this one-word lesson.
            containerHost.submitSelfGrade(secondSessionId, itemId, correct = true)
            val graded2 = assertIs<VocabularySessionUiState.InProgress>(awaitInternalState())
            assertEquals(true, graded2.answered?.correct)
            assertNull(graded2.answered?.next)
            assertEquals("review", db.vocabularyDao().progressForItem(LOCAL_USER, itemId)!!.status)

            val secondAnswer = graded2.answered!!
            containerHost.continueToNext(secondSessionId, secondAnswer.next)
            val summary = assertIs<VocabularySessionUiState.Summary>(awaitInternalState())
            assertEquals(1, summary.summary.wordsLearned)
            assertEquals(100.0, summary.summary.accuracy)
        }
    }

    @Test
    fun `a wrong self-grade on a due review demotes the word and does not repeat it immediately`() = runTest {
        val (repository, db) = setup(itemCount = 3)
        // v0 already due for review, so the session opens straight on its Practice card.
        db.vocabularyDao().updateProgress("review", 2L, 0L, 1L, 0L, 0L, LOCAL_USER, "v0")

        viewModel(repository, db).testWithInternalState(this, VocabularySessionUiState.Loading) {
            runOnCreate()
            val initial = assertIs<VocabularySessionUiState.InProgress>(awaitInternalState())
            val card = assertIs<VocabularyCard.Practice>(initial.session.card)
            assertEquals("v0", card.itemId)
            val sessionId = initial.session.sessionId

            containerHost.submitSelfGrade(sessionId, "v0", correct = false)
            val graded = assertIs<VocabularySessionUiState.InProgress>(awaitInternalState())
            assertEquals(false, graded.answered?.correct)
            assertEquals("learning", db.vocabularyDao().progressForItem(LOCAL_USER, "v0")!!.status)
            assertEquals(0L, db.vocabularyDao().progressForItem(LOCAL_USER, "v0")!!.intervalIndex)

            // The demoted word must not resurface as the very next card - other New cards come first.
            val nextItemId = graded.answered?.next?.card?.itemId
            assertTrue(nextItemId != null && nextItemId != "v0")
        }
    }

    @Test
    fun `starting a session on a lesson with no vocabulary yields an Error state, not a crash`() = runTest {
        val (repository, db) = setup(itemCount = 0)
        viewModel(repository, db).testWithInternalState(this, VocabularySessionUiState.Loading) {
            runOnCreate()
            val state = assertIs<VocabularySessionUiState.Error>(awaitInternalState())
            assertTrue(state.message.isNotBlank())
        }
    }
}
