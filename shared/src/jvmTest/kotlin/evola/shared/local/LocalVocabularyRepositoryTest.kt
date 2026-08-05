package evola.shared.local

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import evola.shared.core.ApiResult
import evola.shared.db.EvolaDatabase
import evola.shared.vocabulary.VocabularyPack
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocalVocabularyRepositoryTest {

    private val passingGrader = VocabularyFreeProductionGrader { _, _ -> VocabGradingResult(true, "gut") }

    private fun setup(itemCount: Int = 3): Pair<LocalVocabularyRepository, EvolaDatabase> {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        EvolaDatabase.Schema.create(driver)
        val db = EvolaDatabase(driver)
        db.goalsQueries.insert("g1", LOCAL_USER, "Learn German", "t", 1L, 0L, 0L)
        db.materialsQueries.insert("m1", LOCAL_USER, "g1", "f.pdf", "h", "READY", "application/pdf", 1L, null, "auto", null, null, "txt", 0L)
        db.lessonsQueries.insert("l1", "m1", "g1", 1L, "Lesson 1", "ready", null, 0L)
        repeat(itemCount) { i ->
            val id = "v$i"
            db.vocabularyQueries.insertItem(
                id, "l1", "Wort$i", "word$i", "der", "Das Wort$i ist gut.",
                null, null, null, null, null, null, null, null, null, 0L,
            )
            db.vocabularyQueries.insertProgress("p$i", LOCAL_USER, id, "new", 0L, 0L, 0L, null, 0L, 0L)
        }
        return LocalVocabularyRepository(db, passingGrader) to db
    }

    /** Craft a correct response for the current stage of [pack]'s current word. */
    private fun correctResponse(db: EvolaDatabase, pack: VocabularyPack): String {
        val item = db.vocabularyQueries.itemById(pack.word.itemId).executeAsOne()
        return when (pack.stageIndex) {
            0, 1 -> "" // ungraded
            2, 3, 4 -> item.term
            5 -> item.example_sentence ?: ""
            else -> "Ein Satz."
        }
    }

    @Test
    fun `session assembles a pack capped at 5 words`() = runTest {
        val (repo, _) = setup(itemCount = 8)
        val pack = (repo.startOrResumeSession("l1") as ApiResult.Success).data
        assertEquals(5, pack.wordsCount)
        assertEquals(0, pack.stageIndex)
        assertEquals(0, pack.wordIndex)
    }

    @Test
    fun `answering all 7 stages of a word advances to the next word`() = runTest {
        val (repo, db) = setup(itemCount = 3)
        var pack = (repo.startOrResumeSession("l1") as ApiResult.Success).data
        val firstItem = pack.word.itemId
        repeat(7) { stage ->
            val result = (repo.answer(pack.packId, pack.word.itemId, pack.stageIndex, correctResponse(db, pack)) as ApiResult.Success).data
            pack = result.next!!
        }
        assertTrue(pack.word.itemId != firstItem || pack.readyToComplete)
        assertEquals(1, pack.wordIndex)
    }

    @Test
    fun `full correct pack marks mastery advanced and completes`() = runTest {
        val (repo, db) = setup(itemCount = 2)
        var pack = (repo.startOrResumeSession("l1") as ApiResult.Success).data
        while (!pack.readyToComplete) {
            val result = (repo.answer(pack.packId, pack.word.itemId, pack.stageIndex, correctResponse(db, pack)) as ApiResult.Success).data
            pack = result.next!!
        }
        val summary = (repo.complete(pack.packId, "2026-08-05") as ApiResult.Success).data
        assertEquals(2, summary.wordsLearned)
        assertEquals(100.0, summary.accuracy)
        // every word answered correctly → mastery advanced off "new"
        assertEquals("learning", db.vocabularyQueries.progressForItem(LOCAL_USER, "v0").executeAsOne().mastery_state)
        assertTrue(db.activityQueries.forDate(LOCAL_USER, "2026-08-05").executeAsOneOrNull() != null)
    }

    @Test
    fun `updateFlags toggles bookmark`() = runTest {
        val (repo, _) = setup(itemCount = 1)
        val updated = (repo.updateFlags("v0", isBookmarked = true, markedDifficult = null) as ApiResult.Success).data
        assertTrue(updated.isBookmarked)
        assertTrue(!updated.markedDifficult)
    }
}
