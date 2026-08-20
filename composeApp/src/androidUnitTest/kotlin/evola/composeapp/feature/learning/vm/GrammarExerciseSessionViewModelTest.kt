package evola.composeapp.feature.learning.vm

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import evola.shared.db.EvolaDatabase
import evola.shared.core.common.LOCAL_USER
import evola.shared.feature.learning.data.LocalGrammarRepository
import kotlinx.coroutines.test.runTest
import org.orbitmvi.orbit.test.testWithInternalState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** Same convention as [evola.composeapp.feature.home.vm.HomeViewModelTest]: a real [LocalGrammarRepository]
 * backed by an in-memory SQLite [EvolaDatabase], driven through [GrammarExerciseSessionViewModel]
 * via the official `org.orbit-mvi:orbit-test` DSL. */
class GrammarExerciseSessionViewModelTest {

    private fun setup(exerciseCount: Int = 2): LocalGrammarRepository {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        EvolaDatabase.Schema.create(driver)
        val db = EvolaDatabase(driver)
        db.goalsQueries.insert("g1", LOCAL_USER, "Learn German", "t", "en", 1L, 0L, 0L)
        db.materialsQueries.insert("m1", LOCAL_USER, "g1", "f.pdf", "h", "READY", "application/pdf", 1L, null, "auto", null, null, "txt", 0L)
        db.lessonsQueries.insert("l1", "m1", "g1", 1L, "Lesson 1", "ready", null, 0L)
        db.grammarQueries.insertTopic("t1", "l1", "Akkusativ", "The accusative case", 0L)
        db.grammarQueries.insertTopicProgress("gp1", LOCAL_USER, "t1", "new", 0L, 0L, 0L, null)
        repeat(exerciseCount) { i ->
            db.grammarQueries.insertExercise("e$i", "t1", "fill_in_blank", "Ich sehe ___ Hund $i", "den", null, i.toLong())
        }
        return LocalGrammarRepository(db)
    }

    @Test
    fun `opens on the first exercise`() = runTest {
        val viewModel = GrammarExerciseSessionViewModel("t1", setup(2))
        viewModel.testWithInternalState(this, GrammarExerciseSessionState.Loading) {
            runOnCreate()
            val state = assertIs<GrammarExerciseSessionState.InProgress>(awaitInternalState())
            assertEquals("e0", state.currentExercise.exerciseId)
            assertEquals(0, state.answeredCount)
        }
    }

    @Test
    fun `a correct answer advances to the next exercise and bumps answeredCount`() = runTest {
        val viewModel = GrammarExerciseSessionViewModel("t1", setup(2))
        viewModel.testWithInternalState(this, GrammarExerciseSessionState.Loading) {
            runOnCreate()
            awaitInternalState()

            containerHost.submitAnswer("e0", "den", correct = true)
            val state = assertIs<GrammarExerciseSessionState.InProgress>(awaitInternalState())
            assertEquals("e1", state.currentExercise.exerciseId)
            assertEquals(1, state.answeredCount)
        }
    }

    @Test
    fun `answering every exercise completes the session with a real accuracy summary`() = runTest {
        val viewModel = GrammarExerciseSessionViewModel("t1", setup(2))
        viewModel.testWithInternalState(this, GrammarExerciseSessionState.Loading) {
            runOnCreate()
            awaitInternalState()

            containerHost.submitAnswer("e0", "den", correct = true)
            awaitInternalState()

            containerHost.submitAnswer("e1", "wrong", correct = false)
            val summary = assertIs<GrammarExerciseSessionState.Summary>(awaitInternalState())
            assertEquals(2, summary.exercisesCompleted)
            assertEquals(50.0, summary.accuracy)
        }
    }

    @Test
    fun `a topic with zero exercises yields Empty, not a crash`() = runTest {
        val viewModel = GrammarExerciseSessionViewModel("t1", setup(0))
        viewModel.testWithInternalState(this, GrammarExerciseSessionState.Loading) {
            runOnCreate()
            assertIs<GrammarExerciseSessionState.Empty>(awaitInternalState())
        }
    }
}
