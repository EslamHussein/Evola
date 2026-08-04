package evola.server

import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Trivial [VocabularyGrader] double - the batch extraction workers have no unit tests at all
 * (verified only via curl against a real Anthropic key) because they have no such seam; this one
 * exists specifically so Stage 6 (Free Production) can be exercised deterministically. */
private class FakeVocabularyGrader(var passable: Boolean = true) : VocabularyGrader {
    override suspend fun grade(term: String, userSentence: String): GradingResult =
        GradingResult(passable, if (passable) "Well done!" else "Try again.")
}

class VocabularyServiceTest {

    private val database = TestDatabase.database
    private val authService = AuthService(database, jwtSecret = "test-secret")
    private val goalService = GoalService(database)
    private val grader = FakeVocabularyGrader()
    private val vocabularyService = VocabularyService(database, grader)

    @BeforeEach
    fun clearTables() {
        grader.passable = true
        transaction(database) {
            VocabularyStageAnswersTable.deleteAll()
            VocabularyPackWordsTable.deleteAll()
            VocabularyPacksTable.deleteAll()
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
        meaningAr: String? = null,
        ipaPronunciation: String? = null,
        relatedWordsJson: String? = null,
        difficultyRating: String? = null,
        frequencyRating: String? = null,
        memoryTip: String? = null,
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
                it[this.meaningAr] = meaningAr
                it[this.ipaPronunciation] = ipaPronunciation
                it[this.relatedWords] = relatedWordsJson
                it[this.difficultyRating] = difficultyRating
                it[this.frequencyRating] = frequencyRating
                it[this.memoryTip] = memoryTip
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

    private fun packWordTerms(packId: String): List<String> = transaction(database) {
        VocabularyPackWordsTable
            .join(VocabularyItemsTable, JoinType.INNER, VocabularyPackWordsTable.vocabularyItemId, VocabularyItemsTable.id)
            .selectAll()
            .where { VocabularyPackWordsTable.packId eq UUID.fromString(packId) }
            .map { it[VocabularyItemsTable.term] }
    }

    private fun progressRow(userId: String, itemId: String) = transaction(database) {
        VocabularyProgressTable.selectAll()
            .where { (VocabularyProgressTable.userId eq UUID.fromString(userId)) and (VocabularyProgressTable.vocabularyItemId eq UUID.fromString(itemId)) }
            .single()
    }

    @Test
    fun `listVocabulary returns the Arabic-metadata fields and bookmark defaults`() = runTest {
        val (userId, goalId) = registerUserWithGoal()
        val materialId = insertMaterial(userId, goalId)
        val lessonId = insertLesson(materialId, goalId)
        insertVocabItem(
            lessonId, userId, "Hund", "dog",
            meaningAr = "كلب",
            ipaPronunciation = "hʊnt",
            relatedWordsJson = """["Katze","Tier"]""",
            difficultyRating = "easy",
            frequencyRating = "common",
            memoryTip = "Sounds like 'hunt' - dogs hunt.",
        )
        insertVocabItem(lessonId, userId, "Katze", "cat")

        val items = vocabularyService.listVocabulary(userId, lessonId.toString())!!.sortedBy { it.term }

        val hund = items.single { it.term == "Hund" }
        assertEquals("كلب", hund.meaningAr)
        assertEquals("hʊnt", hund.ipaPronunciation)
        assertEquals(listOf("Katze", "Tier"), hund.relatedWords)
        assertEquals("easy", hund.difficultyRating)
        assertEquals("common", hund.frequencyRating)
        assertEquals("Sounds like 'hunt' - dogs hunt.", hund.memoryTip)
        assertEquals(false, hund.isBookmarked)
        assertEquals(false, hund.markedDifficult)

        val katze = items.single { it.term == "Katze" }
        assertNull(katze.meaningAr)
        assertEquals(emptyList(), katze.relatedWords)
    }

    @Test
    fun `starting a session creates a pack capped at pack size from new lesson items`() = runTest {
        val (userId, goalId) = registerUserWithGoal()
        val materialId = insertMaterial(userId, goalId)
        val lessonId = insertLesson(materialId, goalId)
        repeat(8) { i -> insertVocabItem(lessonId, userId, "Wort$i", "word$i") }

        val pack = vocabularyService.startOrResumeSession(userId, lessonId.toString())
        assertNotNull(pack)
        assertEquals(1, pack.packNumber)
        assertEquals(5, pack.wordsCount)
        assertEquals(0, pack.wordIndex)
        assertEquals(0, pack.stageIndex)
    }

    @Test
    fun `session resumes the same pack instead of creating a new one`() = runTest {
        val (userId, goalId) = registerUserWithGoal()
        val materialId = insertMaterial(userId, goalId)
        val lessonId = insertLesson(materialId, goalId)
        insertVocabItem(lessonId, userId, "Hund", "dog")

        val first = vocabularyService.startOrResumeSession(userId, lessonId.toString())
        val second = vocabularyService.startOrResumeSession(userId, lessonId.toString())
        assertNotNull(first)
        assertNotNull(second)
        assertEquals(first.packId, second.packId)
    }

    @Test
    fun `pack mixes due review items from other lessons but not never-reviewed ones`() = runTest {
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

        val pack = vocabularyService.startOrResumeSession(userId, currentLesson.toString())
        assertNotNull(pack)
        val terms = packWordTerms(pack.packId)
        assertTrue("Hund" in terms)
        assertTrue("Katze" in terms)
        assertTrue("Vogel" !in terms)
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

        val pack = vocabularyService.startOrResumeSession(userId, currentLesson.toString())
        assertNotNull(pack)
        val terms = packWordTerms(pack.packId)
        assertEquals(setOf("Hund", "Katze"), terms.toSet())
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
    fun `answering every gradable stage correctly advances mastery exactly once`() = runTest {
        val (userId, goalId) = registerUserWithGoal()
        val materialId = insertMaterial(userId, goalId)
        val lessonId = insertLesson(materialId, goalId)
        val itemId = insertVocabItem(lessonId, userId, "Hund", "dog", exampleSentence = "Ich habe einen Hund.")

        val pack = vocabularyService.startOrResumeSession(userId, lessonId.toString())!!
        val packId = pack.packId

        // Stages 0-1 (Discover, Recognition) are never graded.
        var result = vocabularyService.answer(userId, packId, VocabularyAnswerRequest(itemId.toString(), 0, ""))!!
        assertNull(result.correct)
        result = vocabularyService.answer(userId, packId, VocabularyAnswerRequest(itemId.toString(), 1, "anything"))!!
        assertNull(result.correct)

        // Stages 2-4 (Reverse Recall / Partial Recall / Sentence Completion): term match.
        repeat(3) { i ->
            result = vocabularyService.answer(userId, packId, VocabularyAnswerRequest(itemId.toString(), 2 + i, "Hund"))!!
            assertEquals(true, result.correct)
        }

        // Stage 5 (Translation): a matching sentence.
        result = vocabularyService.answer(userId, packId, VocabularyAnswerRequest(itemId.toString(), 5, "Ich habe einen Hund."))!!
        assertEquals(true, result.correct)

        // Stage 6 (Free Production): fake grader passes.
        result = vocabularyService.answer(userId, packId, VocabularyAnswerRequest(itemId.toString(), 6, "Der Hund läuft."))!!
        assertEquals(true, result.correct)
        assertNotNull(result.next)
        assertTrue(result.next!!.readyToComplete)

        val progress = progressRow(userId, itemId.toString())
        assertEquals("learning", progress[VocabularyProgressTable.masteryState])
        // Exactly one MasterySrs update for the whole word, not one per gradable stage.
        assertEquals(1, progress[VocabularyProgressTable.correctStreak])
    }

    @Test
    fun `getting one gradable stage wrong marks the whole word incorrect for mastery`() = runTest {
        val (userId, goalId) = registerUserWithGoal()
        val materialId = insertMaterial(userId, goalId)
        val lessonId = insertLesson(materialId, goalId)
        val itemId = insertVocabItem(
            lessonId, userId, "Hund", "dog",
            masteryState = "learning", exampleSentence = "Ich habe einen Hund.",
        )

        val pack = vocabularyService.startOrResumeSession(userId, lessonId.toString())!!
        val packId = pack.packId

        vocabularyService.answer(userId, packId, VocabularyAnswerRequest(itemId.toString(), 0, ""))
        vocabularyService.answer(userId, packId, VocabularyAnswerRequest(itemId.toString(), 1, "anything"))
        // Stage 2 wrong on purpose.
        val wrongResult = vocabularyService.answer(userId, packId, VocabularyAnswerRequest(itemId.toString(), 2, "zzz"))!!
        assertEquals(false, wrongResult.correct)
        vocabularyService.answer(userId, packId, VocabularyAnswerRequest(itemId.toString(), 3, "Hund"))
        vocabularyService.answer(userId, packId, VocabularyAnswerRequest(itemId.toString(), 4, "Hund"))
        vocabularyService.answer(userId, packId, VocabularyAnswerRequest(itemId.toString(), 5, "Ich habe einen Hund."))
        vocabularyService.answer(userId, packId, VocabularyAnswerRequest(itemId.toString(), 6, "Der Hund läuft."))

        val progress = progressRow(userId, itemId.toString())
        assertEquals("new", progress[VocabularyProgressTable.masteryState]) // dropped one stage from "learning"
        assertEquals(0, progress[VocabularyProgressTable.correctStreak])
    }

    @Test
    fun `stage 4 auto-passes when the item has no valid sentence to blank`() = runTest {
        val (userId, goalId) = registerUserWithGoal()
        val materialId = insertMaterial(userId, goalId)
        val lessonId = insertLesson(materialId, goalId)
        val itemId = insertVocabItem(lessonId, userId, "Hund", "dog") // no exampleSentence

        val pack = vocabularyService.startOrResumeSession(userId, lessonId.toString())!!
        vocabularyService.answer(userId, pack.packId, VocabularyAnswerRequest(itemId.toString(), 0, ""))
        vocabularyService.answer(userId, pack.packId, VocabularyAnswerRequest(itemId.toString(), 1, "x"))
        vocabularyService.answer(userId, pack.packId, VocabularyAnswerRequest(itemId.toString(), 2, "Hund"))
        vocabularyService.answer(userId, pack.packId, VocabularyAnswerRequest(itemId.toString(), 3, "Hund"))
        val result = vocabularyService.answer(userId, pack.packId, VocabularyAnswerRequest(itemId.toString(), 4, "totally irrelevant"))!!
        assertEquals(true, result.correct)
    }

    @Test
    fun `stage 5 rejects an unrelated sentence`() = runTest {
        val (userId, goalId) = registerUserWithGoal()
        val materialId = insertMaterial(userId, goalId)
        val lessonId = insertLesson(materialId, goalId)
        val itemId = insertVocabItem(lessonId, userId, "Hund", "dog", exampleSentence = "Ich habe einen Hund im Garten gesehen.")

        val pack = vocabularyService.startOrResumeSession(userId, lessonId.toString())!!
        vocabularyService.answer(userId, pack.packId, VocabularyAnswerRequest(itemId.toString(), 0, ""))
        vocabularyService.answer(userId, pack.packId, VocabularyAnswerRequest(itemId.toString(), 1, "x"))
        vocabularyService.answer(userId, pack.packId, VocabularyAnswerRequest(itemId.toString(), 2, "Hund"))
        vocabularyService.answer(userId, pack.packId, VocabularyAnswerRequest(itemId.toString(), 3, "Hund"))
        vocabularyService.answer(userId, pack.packId, VocabularyAnswerRequest(itemId.toString(), 4, "Hund"))
        val result = vocabularyService.answer(userId, pack.packId, VocabularyAnswerRequest(itemId.toString(), 5, "Die Katze schläft."))!!
        assertEquals(false, result.correct)
    }

    @Test
    fun `answer rejects a stage submission that does not match the current position`() = runTest {
        val (userId, goalId) = registerUserWithGoal()
        val materialId = insertMaterial(userId, goalId)
        val lessonId = insertLesson(materialId, goalId)
        val itemId = insertVocabItem(lessonId, userId, "Hund", "dog")

        val pack = vocabularyService.startOrResumeSession(userId, lessonId.toString())!!
        // Current stage is 0, not 3 - this submission is stale/out-of-order.
        val result = vocabularyService.answer(userId, pack.packId, VocabularyAnswerRequest(itemId.toString(), 3, "Hund"))
        assertNull(result)
    }

    @Test
    fun `complete computes accuracy across all words in the pack`() = runTest {
        val (userId, goalId) = registerUserWithGoal()
        val materialId = insertMaterial(userId, goalId)
        val lessonId = insertLesson(materialId, goalId)
        insertVocabItem(lessonId, userId, "Hund", "dog", exampleSentence = "Ich habe einen Hund.")
        insertVocabItem(lessonId, userId, "Katze", "cat", exampleSentence = "Ich habe eine Katze.")

        val pack = vocabularyService.startOrResumeSession(userId, lessonId.toString())!!
        val terms = packWordTerms(pack.packId)
        assertEquals(2, terms.size)

        // First word answered entirely correctly, second entirely wrong.
        var current = pack
        listOf(true, false).forEach { correctResponses ->
            val itemId = current.word.itemId
            val termValue = current.word.term
            val sentence = current.word.exampleSentence!!
            vocabularyService.answer(userId, pack.packId, VocabularyAnswerRequest(itemId, 0, ""))
            vocabularyService.answer(userId, pack.packId, VocabularyAnswerRequest(itemId, 1, "x"))
            vocabularyService.answer(userId, pack.packId, VocabularyAnswerRequest(itemId, 2, if (correctResponses) termValue else "zzz"))
            vocabularyService.answer(userId, pack.packId, VocabularyAnswerRequest(itemId, 3, if (correctResponses) termValue else "zzz"))
            vocabularyService.answer(userId, pack.packId, VocabularyAnswerRequest(itemId, 4, if (correctResponses) termValue else "zzz"))
            vocabularyService.answer(userId, pack.packId, VocabularyAnswerRequest(itemId, 5, if (correctResponses) sentence else "unrelated"))
            grader.passable = correctResponses
            val stage6Result = vocabularyService.answer(userId, pack.packId, VocabularyAnswerRequest(itemId, 6, "some sentence"))!!
            current = stage6Result.next!!
        }

        val completed = vocabularyService.complete(userId, pack.packId)
        assertNotNull(completed)
        assertEquals(2, completed.wordsLearned)
        assertEquals(50.0, completed.accuracy)
    }

    @Test
    fun `updateFlags sets and returns bookmark and difficulty flags`() = runTest {
        val (userId, goalId) = registerUserWithGoal()
        val materialId = insertMaterial(userId, goalId)
        val lessonId = insertLesson(materialId, goalId)
        val itemId = insertVocabItem(lessonId, userId, "Hund", "dog")

        val updated = vocabularyService.updateFlags(userId, itemId.toString(), VocabularyFlagsRequest(isBookmarked = true))
        assertNotNull(updated)
        assertEquals(true, updated.isBookmarked)
        assertEquals(false, updated.markedDifficult)

        val updatedAgain = vocabularyService.updateFlags(userId, itemId.toString(), VocabularyFlagsRequest(markedDifficult = true))
        assertNotNull(updatedAgain)
        assertEquals(true, updatedAgain.isBookmarked) // untouched by this call, stays true
        assertEquals(true, updatedAgain.markedDifficult)
    }

    @Test
    fun `updateFlags for an item the user has no progress on returns null`() = runTest {
        val (userId, goalId) = registerUserWithGoal()
        val materialId = insertMaterial(userId, goalId)
        val lessonId = insertLesson(materialId, goalId)
        insertVocabItem(lessonId, userId, "Hund", "dog")

        val otherUserId = UUID.randomUUID().toString()
        assertNull(vocabularyService.updateFlags(otherUserId, UUID.randomUUID().toString(), VocabularyFlagsRequest(isBookmarked = true)))
    }
}
