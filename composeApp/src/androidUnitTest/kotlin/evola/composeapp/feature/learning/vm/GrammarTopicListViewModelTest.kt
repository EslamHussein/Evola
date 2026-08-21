package evola.composeapp.feature.learning.vm

import evola.composeapp.core.database.testAppDatabase
import evola.database.entity.GrammarProgressEntity
import evola.database.entity.GrammarTopicEntity
import evola.database.entity.GoalEntity
import evola.database.entity.LessonEntity
import evola.database.entity.MaterialEntity
import evola.shared.core.common.LOCAL_USER
import evola.shared.feature.learning.data.LocalGrammarRepository
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.orbitmvi.orbit.test.testWithInternalState
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Same convention as [evola.composeapp.feature.home.vm.HomeViewModelTest]: a real [LocalGrammarRepository]
 * backed by an in-memory Room database, driven through [GrammarTopicListViewModel] via
 * the official `org.orbit-mvi:orbit-test` DSL. Robolectric only because Room's Android database
 * builder needs a real `Context`. */
@RunWith(RobolectricTestRunner::class)
class GrammarTopicListViewModelTest {

    private suspend fun setup(): LocalGrammarRepository {
        val db = testAppDatabase()
        db.goalDao().insert(GoalEntity("g1", LOCAL_USER, "Learn German", "t", "en", 1L, 0L, 0L))
        db.materialDao().insert(MaterialEntity("m1", LOCAL_USER, "g1", "f.pdf", "h", "READY", "application/pdf", 1L, null, "auto", null, null, "txt", 0L, 0L, 0L))
        db.lessonDao().insert(LessonEntity("l1", "m1", "g1", 1L, "Lesson 1", "ready", "curriculum", null, null, 0L))
        db.grammarDao().insertTopic(GrammarTopicEntity("t1", "l1", "Akkusativ", "The accusative case", 0L))
        db.grammarDao().insertTopicProgress(GrammarProgressEntity("gp1", LOCAL_USER, "t1", "new", 0L, 0L, 0L, null))
        db.grammarDao().insertTopic(GrammarTopicEntity("t2", "l1", "Dativ", "The dative case", 1L))
        db.grammarDao().insertTopicProgress(GrammarProgressEntity("gp2", LOCAL_USER, "t2", "learning", 0L, 0L, 0L, null))
        return LocalGrammarRepository(db)
    }

    @Test
    fun `loads every grammar topic in the lesson`() = runTest {
        val viewModel = GrammarTopicListViewModel("l1", setup())
        viewModel.testWithInternalState(this, GrammarTopicListState.Loading) {
            runOnCreate()
            val loaded = assertIs<GrammarTopicListState.Loaded>(awaitInternalState())
            assertEquals(2, loaded.topics.size)
            assertEquals(setOf("Akkusativ", "Dativ"), loaded.topics.map { it.name }.toSet())
            assertTrue(loaded.topics.any { it.masteryState == "new" })
            assertTrue(loaded.topics.any { it.masteryState == "learning" })
        }
    }

    @Test
    fun `a lesson with no grammar topics still loads as an empty list, not a crash`() = runTest {
        val db = testAppDatabase()
        db.goalDao().insert(GoalEntity("g1", LOCAL_USER, "Learn German", "t", "en", 1L, 0L, 0L))
        db.materialDao().insert(MaterialEntity("m1", LOCAL_USER, "g1", "f.pdf", "h", "READY", "application/pdf", 1L, null, "auto", null, null, "txt", 0L, 0L, 0L))
        db.lessonDao().insert(LessonEntity("l1", "m1", "g1", 1L, "Lesson 1", "ready", "curriculum", null, null, 0L))

        val viewModel = GrammarTopicListViewModel("l1", LocalGrammarRepository(db))
        viewModel.testWithInternalState(this, GrammarTopicListState.Loading) {
            runOnCreate()
            val loaded = assertIs<GrammarTopicListState.Loaded>(awaitInternalState())
            assertEquals(0, loaded.topics.size)
        }
    }
}
