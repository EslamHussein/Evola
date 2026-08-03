package evola.server

import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VocabularyServiceTest {

    private val database = TestDatabase.database
    private val authService = AuthService(database, jwtSecret = "test-secret")
    private val goalService = GoalService(database)
    private val vocabularyService = VocabularyService(database)

    @BeforeEach
    fun clearTables() {
        transaction(database) {
            VocabularySessionItemsTable.deleteAll()
            VocabularySessionsTable.deleteAll()
            VocabularyProgressTable.deleteAll()
            VocabularyItemsTable.deleteAll()
            VocabularyExtractionJobsTable.deleteAll()
            LessonsTable.deleteAll()
            MaterialsTable.deleteAll()
            GoalsTable.deleteAll()
            RefreshTokensTable.deleteAll()
            UsersTable.deleteAll()
        }
    }

    private suspend fun registerUserWithGoal(email: String = "vocabtester@example.com"): Pair<String, String> {
        val registered = authService.register(RegisterRequest("Vocab Tester", email, "Passw0rd!"))
        val userId = (registered as RegisterOutcome.Created).tokens.user.id
        val goal = goalService.createGoal(userId, CreateGoalRequest("Pass the German B1 exam"))
        val goalId = (goal as CreateGoalOutcome.Created).goal.id
        return userId to goalId
    }

    private fun insertMaterial(userId: String, goalId: String): UUID {
        val materialId = UUID.randomUUID()
        transaction(database) {
            MaterialsTable.insert {
                it[id] = materialId
                it[this.userId] = UUID.fromString(userId)
                it[this.goalId] = UUID.fromString(goalId)
                it[filename] = "book.pdf"
                it[contentHash] = "hash-$materialId"
                it[status] = "READY"
                it[fileRef] = "/tmp/$materialId.pdf"
                it[mimeType] = MIME_PDF
                it[sizeBytes] = 1024L
                it[pageCount] = 10
                it[createdAt] = Instant.now()
            }
        }
        return materialId
    }

    private fun insertLesson(materialId: UUID, goalId: String, number: Int = 1, title: String = "Lesson"): UUID {
        val lessonId = UUID.randomUUID()
        transaction(database) {
            LessonsTable.insert {
                it[id] = lessonId
                it[this.materialId] = materialId
                it[this.goalId] = UUID.fromString(goalId)
                it[this.number] = number
                it[this.title] = title
                it[status] = "ready"
                it[createdAt] = Instant.now()
            }
        }
        return lessonId
    }

    private fun insertVocabItem(
        lessonId: UUID,
        userId: String,
        term: String,
        meaning: String,
        masteryState: String = "new",
        lastReviewedAt: Instant? = null,
        nextReviewAt: Instant = Instant.now(),
        exampleSentence: String? = null,
        partOfSpeech: String? = null,
        exampleSentenceTranslation: String? = null,
    ): UUID {
        val itemId = UUID.randomUUID()
        transaction(database) {
            VocabularyItemsTable.insert {
                it[id] = itemId
                it[this.lessonId] = lessonId
                it[this.term] = term
                it[this.meaning] = meaning
                it[this.exampleSentence] = exampleSentence
                it[this.partOfSpeech] = partOfSpeech
                it[this.exampleSentenceTranslation] = exampleSentenceTranslation
                it[createdAt] = Instant.now()
            }
            VocabularyProgressTable.insert {
                it[id] = UUID.randomUUID()
                it[this.userId] = UUID.fromString(userId)
                it[vocabularyItemId] = itemId
                it[this.masteryState] = masteryState
                it[correctStreak] = 0
                it[intervalIndex] = 0
                it[this.nextReviewAt] = nextReviewAt
                it[this.lastReviewedAt] = lastReviewedAt
            }
        }
        return itemId
    }

    @Test
    fun `session includes new items from this lesson`() = runTest {
        val (userId, goalId) = registerUserWithGoal()
        val materialId = insertMaterial(userId, goalId)
        val lessonId = insertLesson(materialId, goalId)
        insertVocabItem(lessonId, userId, "Hund", "dog")
        insertVocabItem(lessonId, userId, "Katze", "cat")

        val session = vocabularyService.startOrResumeSession(userId, lessonId.toString())
        assertNotNull(session)
        assertEquals(2, session.items.size)
        assertTrue(session.hasLessonVocabulary)
    }

    @Test
    fun `session resumes instead of creating a new one`() = runTest {
        val (userId, goalId) = registerUserWithGoal()
        val materialId = insertMaterial(userId, goalId)
        val lessonId = insertLesson(materialId, goalId)
        insertVocabItem(lessonId, userId, "Hund", "dog")

        val first = vocabularyService.startOrResumeSession(userId, lessonId.toString())
        val second = vocabularyService.startOrResumeSession(userId, lessonId.toString())
        assertNotNull(first)
        assertNotNull(second)
        assertEquals(first.sessionId, second.sessionId)
    }

    @Test
    fun `session mixes in due review items from other lessons but not never-reviewed ones`() = runTest {
        val (userId, goalId) = registerUserWithGoal()
        val materialId = insertMaterial(userId, goalId)
        val currentLesson = insertLesson(materialId, goalId, number = 2, title = "Current")
        val otherLesson = insertLesson(materialId, goalId, number = 1, title = "Other")

        insertVocabItem(currentLesson, userId, "Hund", "dog")
        insertVocabItem(
            otherLesson, userId, "Katze", "cat",
            masteryState = "learning",
            lastReviewedAt = Instant.now().minusSeconds(86_400 * 5),
            nextReviewAt = Instant.now().minusSeconds(3600),
        )
        // Never reviewed - should NOT count as "due" despite next_review_at defaulting to now.
        insertVocabItem(otherLesson, userId, "Vogel", "bird")

        val session = vocabularyService.startOrResumeSession(userId, currentLesson.toString())
        assertNotNull(session)
        val terms = session.items.map { it.term }
        assertTrue("Hund" in terms)
        assertTrue("Katze" in terms)
        assertTrue("Vogel" !in terms)
    }

    @Test
    fun `lesson with zero vocabulary reports hasLessonVocabulary false`() = runTest {
        val (userId, goalId) = registerUserWithGoal()
        val materialId = insertMaterial(userId, goalId)
        val lessonId = insertLesson(materialId, goalId)

        val session = vocabularyService.startOrResumeSession(userId, lessonId.toString())
        assertNotNull(session)
        assertTrue(!session.hasLessonVocabulary)
    }

    @Test
    fun `answering incorrectly floors mastery at new and resurfaces the item later`() = runTest {
        val (userId, goalId) = registerUserWithGoal()
        val materialId = insertMaterial(userId, goalId)
        val lessonId = insertLesson(materialId, goalId)
        insertVocabItem(lessonId, userId, "Hund", "dog")

        val session = vocabularyService.startOrResumeSession(userId, lessonId.toString())
        assertNotNull(session)
        val item = session.items.single()

        val result = vocabularyService.answer(userId, session.sessionId, VocabularyAnswerRequest(item.itemId, "wrong", correct = false))
        assertNotNull(result)
        assertEquals("new", result.masteryState)
        assertTrue(result.resurfaced)

        // Resurfaced as a fresh, unanswered occurrence in the (still-open) session.
        val remaining = vocabularyService.startOrResumeSession(userId, lessonId.toString())
        assertNotNull(remaining)
        assertEquals(1, remaining.items.size)
    }

    @Test
    fun `answering correctly advances mastery and does not resurface`() = runTest {
        val (userId, goalId) = registerUserWithGoal()
        val materialId = insertMaterial(userId, goalId)
        val lessonId = insertLesson(materialId, goalId)
        insertVocabItem(lessonId, userId, "Hund", "dog")

        val session = vocabularyService.startOrResumeSession(userId, lessonId.toString())
        assertNotNull(session)
        val item = session.items.single()

        val result = vocabularyService.answer(userId, session.sessionId, VocabularyAnswerRequest(item.itemId, "dog", correct = true))
        assertNotNull(result)
        assertEquals("learning", result.masteryState)
        assertTrue(!result.resurfaced)
    }

    @Test
    fun `complete computes accuracy from answered items only`() = runTest {
        val (userId, goalId) = registerUserWithGoal()
        val materialId = insertMaterial(userId, goalId)
        val lessonId = insertLesson(materialId, goalId)
        insertVocabItem(lessonId, userId, "Hund", "dog")
        insertVocabItem(lessonId, userId, "Katze", "cat")

        val session = vocabularyService.startOrResumeSession(userId, lessonId.toString())
        assertNotNull(session)
        assertEquals(2, session.items.size)

        vocabularyService.answer(userId, session.sessionId, VocabularyAnswerRequest(session.items[0].itemId, "dog", correct = true))
        vocabularyService.answer(userId, session.sessionId, VocabularyAnswerRequest(session.items[1].itemId, "wrong", correct = false))

        val completed = vocabularyService.complete(userId, session.sessionId)
        assertNotNull(completed)
        assertEquals(2, completed.itemsCount)
        assertEquals(50.0, completed.accuracy)
    }

    @Test
    fun `session for a lesson the user does not own returns null`() = runTest {
        val (userId, goalId) = registerUserWithGoal()
        val materialId = insertMaterial(userId, goalId)
        val lessonId = insertLesson(materialId, goalId)

        val otherUserId = UUID.randomUUID().toString()
        assertNull(vocabularyService.startOrResumeSession(otherUserId, lessonId.toString()))
    }

    @Test
    fun `falls back to mastered items across the goal when nothing new or due exists`() = runTest {
        val (userId, goalId) = registerUserWithGoal()
        val materialId = insertMaterial(userId, goalId)
        val currentLesson = insertLesson(materialId, goalId, number = 2)
        val otherLesson = insertLesson(materialId, goalId, number = 1)

        insertVocabItem(
            currentLesson, userId, "Hund", "dog",
            masteryState = "mastered", lastReviewedAt = Instant.now(), nextReviewAt = Instant.now().plusSeconds(86_400 * 30),
        )
        insertVocabItem(
            otherLesson, userId, "Katze", "cat",
            masteryState = "mastered", lastReviewedAt = Instant.now(), nextReviewAt = Instant.now().plusSeconds(86_400 * 30),
        )

        val session = vocabularyService.startOrResumeSession(userId, currentLesson.toString())
        assertNotNull(session)
        assertEquals(2, session.items.size)
    }

    @Test
    fun `fill-blank-eligible items can be assigned the fill_blank drill with a correctly blanked sentence`() = runTest {
        val (userId, goalId) = registerUserWithGoal()
        val materialId = insertMaterial(userId, goalId)
        val lessonId = insertLesson(materialId, goalId)
        // 12 eligible items (drill type is a 1-in-3 random draw per item) makes the odds of zero
        // fill_blank draws astronomically small (~(2/3)^12) without needing a seeded Random.
        repeat(12) { i ->
            insertVocabItem(
                lessonId, userId, "Hund$i", "dog",
                exampleSentence = "Ich habe einen Hund$i.",
                partOfSpeech = "noun",
                exampleSentenceTranslation = "I have a dog.",
            )
        }

        val session = vocabularyService.startOrResumeSession(userId, lessonId.toString())
        assertNotNull(session)
        val fillBlankItem = session.items.firstOrNull { it.drillType == "fill_blank" }
        assertNotNull(fillBlankItem, "expected at least one fill_blank item across 12 eligible draws")
        assertEquals("Ich habe einen ___.", fillBlankItem.sentenceWithBlank)
        assertEquals("noun", fillBlankItem.partOfSpeech)
        assertEquals("I have a dog.", fillBlankItem.sentenceTranslation)
    }

    @Test
    fun `items missing example sentence or translation are never assigned the fill_blank drill`() = runTest {
        val (userId, goalId) = registerUserWithGoal()
        val materialId = insertMaterial(userId, goalId)
        val lessonId = insertLesson(materialId, goalId)
        repeat(12) { i -> insertVocabItem(lessonId, userId, "Hund$i", "dog") }

        val session = vocabularyService.startOrResumeSession(userId, lessonId.toString())
        assertNotNull(session)
        assertTrue(session.items.none { it.drillType == "fill_blank" })
    }
}
