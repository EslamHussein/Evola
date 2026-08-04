package evola.server

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
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

class GrammarServiceTest {

    private val database = TestDatabase.database
    private val authService = AuthService(database, jwtSecret = "test-secret")
    private val goalService = GoalService(database)
    private val grammarService = GrammarService(database)

    @BeforeEach
    fun clearTables() {
        transaction(database) {
            GrammarSessionAnswersTable.deleteAll()
            GrammarSessionsTable.deleteAll()
            GrammarProgressTable.deleteAll()
            GrammarExercisesTable.deleteAll()
            GrammarTopicsTable.deleteAll()
            LessonsTable.deleteAll()
            MaterialsTable.deleteAll()
            GoalsTable.deleteAll()
            RefreshTokensTable.deleteAll()
            UsersTable.deleteAll()
        }
    }

    private suspend fun registerUserWithGoal(email: String = "grammartester@example.com"): Pair<String, String> {
        val registered = authService.register(RegisterRequest("Grammar Tester", email, "Passw0rd!"))
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

    private fun insertGrammarTopic(lessonId: UUID, name: String = "Modalverben", explanation: String = "Modal verbs express ability or necessity."): UUID {
        val topicId = UUID.randomUUID()
        transaction(database) {
            GrammarTopicsTable.insert {
                it[id] = topicId
                it[this.lessonId] = lessonId
                it[this.name] = name
                it[this.explanation] = explanation
                it[createdAt] = Instant.now()
            }
        }
        return topicId
    }

    private fun insertGrammarProgress(
        userId: String,
        topicId: UUID,
        masteryState: String = "new",
        correctStreak: Int = 0,
        intervalIndex: Int = 0,
    ) {
        transaction(database) {
            GrammarProgressTable.insert {
                it[id] = UUID.randomUUID()
                it[this.userId] = UUID.fromString(userId)
                it[this.topicId] = topicId
                it[this.masteryState] = masteryState
                it[this.correctStreak] = correctStreak
                it[this.intervalIndex] = intervalIndex.toShort()
                it[nextReviewAt] = Instant.now()
            }
        }
    }

    private fun insertGrammarExercise(
        topicId: UUID,
        type: String,
        prompt: String,
        answerKey: String,
        distractors: List<String>? = null,
        createdAt: Instant = Instant.now(),
    ): UUID {
        val exerciseId = UUID.randomUUID()
        transaction(database) {
            GrammarExercisesTable.insert {
                it[id] = exerciseId
                it[this.topicId] = topicId
                it[this.type] = type
                it[this.prompt] = prompt
                it[this.answerKey] = answerKey
                it[this.distractors] = distractors?.let { d -> MATERIALS_JSON.encodeToString(ListSerializer(String.serializer()), d) }
                it[this.createdAt] = createdAt
            }
        }
        return exerciseId
    }

    // --- listTopics ---

    @Test
    fun `listTopics returns this lesson's topics with mastery state`() = runTest {
        val (userId, goalId) = registerUserWithGoal()
        val material = insertMaterial(userId, goalId)
        val lessonId = insertLesson(material, goalId)
        val topicId = insertGrammarTopic(lessonId, name = "Modalverben")
        insertGrammarProgress(userId, topicId, masteryState = "learning")

        val topics = grammarService.listTopics(userId, lessonId.toString())
        assertNotNull(topics)
        val topic = topics.single()
        assertEquals("Modalverben", topic.name)
        assertEquals("learning", topic.masteryState)
    }

    @Test
    fun `listTopics returns an empty list for a lesson with 0 grammar topics - an honest, non-error outcome`() = runTest {
        val (userId, goalId) = registerUserWithGoal()
        val material = insertMaterial(userId, goalId)
        val lessonId = insertLesson(material, goalId)

        val topics = grammarService.listTopics(userId, lessonId.toString())
        assertNotNull(topics)
        assertTrue(topics.isEmpty())
    }

    @Test
    fun `listTopics returns null for a lesson that does not belong to the user`() = runTest {
        val (userId, goalId) = registerUserWithGoal()
        val material = insertMaterial(userId, goalId)
        val lessonId = insertLesson(material, goalId)
        insertGrammarTopic(lessonId)

        val (otherUserId, _) = registerUserWithGoal("otheruser@example.com")
        assertNull(grammarService.listTopics(otherUserId, lessonId.toString()))
    }

    // --- startOrResumeSession ---

    @Test
    fun `startOrResumeSession returns null when the user has no progress row for the topic`() = runTest {
        val (userId, goalId) = registerUserWithGoal()
        val material = insertMaterial(userId, goalId)
        val lessonId = insertLesson(material, goalId)
        val topicId = insertGrammarTopic(lessonId)
        // Deliberately no insertGrammarProgress call - this user never had this topic extracted.

        assertNull(grammarService.startOrResumeSession(userId, topicId.toString()))
    }

    @Test
    fun `startOrResumeSession resumes the same session with stable shuffled choices across repeated calls`() = runTest {
        val (userId, goalId) = registerUserWithGoal()
        val material = insertMaterial(userId, goalId)
        val lessonId = insertLesson(material, goalId)
        val topicId = insertGrammarTopic(lessonId)
        insertGrammarProgress(userId, topicId)
        insertGrammarExercise(
            topicId, "multiple_choice", "Which is correct?", "kann",
            distractors = listOf("kannst", "koennt", "koennen"),
        )

        val first = grammarService.startOrResumeSession(userId, topicId.toString())
        assertNotNull(first)
        val second = grammarService.startOrResumeSession(userId, topicId.toString())
        assertNotNull(second)

        assertEquals(first.sessionId, second.sessionId)
        assertEquals(first.exercises.single().choices, second.exercises.single().choices)
        assertTrue(first.exercises.single().choices.containsAll(listOf("kann", "kannst", "koennt", "koennen")))
    }

    @Test
    fun `startOrResumeSession marks a previously answered exercise as answered on resume`() = runTest {
        val (userId, goalId) = registerUserWithGoal()
        val material = insertMaterial(userId, goalId)
        val lessonId = insertLesson(material, goalId)
        val topicId = insertGrammarTopic(lessonId)
        insertGrammarProgress(userId, topicId)
        val exerciseId = insertGrammarExercise(topicId, "fill_in_blank", "Ich ___ schwimmen.", "kann")

        val session = grammarService.startOrResumeSession(userId, topicId.toString())!!
        assertEquals(false, session.exercises.single().answered)

        grammarService.answer(userId, session.sessionId, GrammarAnswerRequest(exerciseId.toString(), "kann", correct = true))

        val resumed = grammarService.startOrResumeSession(userId, topicId.toString())!!
        assertEquals(true, resumed.exercises.single().answered)
    }

    // --- answer: two-consecutive-correct mastery rule ---

    @Test
    fun `answer only advances mastery on the second of two consecutive correct answers, and drops it immediately on a wrong answer`() = runTest {
        val (userId, goalId) = registerUserWithGoal()
        val material = insertMaterial(userId, goalId)
        val lessonId = insertLesson(material, goalId)
        val topicId = insertGrammarTopic(lessonId)
        insertGrammarProgress(userId, topicId, masteryState = "new", correctStreak = 0)
        val ex1 = insertGrammarExercise(topicId, "fill_in_blank", "p1", "a1")
        val ex2 = insertGrammarExercise(topicId, "fill_in_blank", "p2", "a2")
        val ex3 = insertGrammarExercise(topicId, "fill_in_blank", "p3", "a3")

        val session = grammarService.startOrResumeSession(userId, topicId.toString())!!

        val afterFirst = grammarService.answer(userId, session.sessionId, GrammarAnswerRequest(ex1.toString(), "a1", correct = true))
        assertNotNull(afterFirst)
        assertEquals("new", afterFirst.masteryState)

        val afterSecond = grammarService.answer(userId, session.sessionId, GrammarAnswerRequest(ex2.toString(), "a2", correct = true))
        assertNotNull(afterSecond)
        assertEquals("learning", afterSecond.masteryState)

        val afterWrong = grammarService.answer(userId, session.sessionId, GrammarAnswerRequest(ex3.toString(), "wrong", correct = false))
        assertNotNull(afterWrong)
        assertEquals("new", afterWrong.masteryState)
    }

    @Test
    fun `answer is idempotent - replaying the same answer does not double-advance mastery`() = runTest {
        val (userId, goalId) = registerUserWithGoal()
        val material = insertMaterial(userId, goalId)
        val lessonId = insertLesson(material, goalId)
        val topicId = insertGrammarTopic(lessonId)
        insertGrammarProgress(userId, topicId, masteryState = "new", correctStreak = 0)
        val ex1 = insertGrammarExercise(topicId, "fill_in_blank", "p1", "a1")
        val ex2 = insertGrammarExercise(topicId, "fill_in_blank", "p2", "a2")

        val session = grammarService.startOrResumeSession(userId, topicId.toString())!!

        val first = grammarService.answer(userId, session.sessionId, GrammarAnswerRequest(ex1.toString(), "a1", correct = true))
        assertNotNull(first)
        assertEquals("new", first.masteryState)

        // Replay the identical answer for ex1 - must return the stored snapshot, not re-apply
        // onPartialCorrect and push correctStreak past 1.
        val replay = grammarService.answer(userId, session.sessionId, GrammarAnswerRequest(ex1.toString(), "a1", correct = true))
        assertNotNull(replay)
        assertEquals(first.masteryState, replay.masteryState)
        assertEquals(first.nextReviewAt, replay.nextReviewAt)

        // If the replay had wrongly incremented correctStreak to 2 (even), ex2's answer would take
        // the "first of a new pair" (partial-only) branch instead of advancing - assert it doesn't.
        val afterEx2 = grammarService.answer(userId, session.sessionId, GrammarAnswerRequest(ex2.toString(), "a2", correct = true))
        assertNotNull(afterEx2)
        assertEquals("learning", afterEx2.masteryState)
    }

    @Test
    fun `answer returns null for a session that does not belong to the user`() = runTest {
        val (userId, goalId) = registerUserWithGoal()
        val material = insertMaterial(userId, goalId)
        val lessonId = insertLesson(material, goalId)
        val topicId = insertGrammarTopic(lessonId)
        insertGrammarProgress(userId, topicId)
        val exerciseId = insertGrammarExercise(topicId, "fill_in_blank", "p1", "a1")
        val session = grammarService.startOrResumeSession(userId, topicId.toString())!!

        val (otherUserId, _) = registerUserWithGoal("otheruser2@example.com")
        assertNull(grammarService.answer(otherUserId, session.sessionId, GrammarAnswerRequest(exerciseId.toString(), "a1", correct = true)))
    }

    // --- complete ---

    @Test
    fun `complete computes exercises_completed and accuracy from this session's own answers`() = runTest {
        val (userId, goalId) = registerUserWithGoal()
        val material = insertMaterial(userId, goalId)
        val lessonId = insertLesson(material, goalId)
        val topicId = insertGrammarTopic(lessonId)
        insertGrammarProgress(userId, topicId)
        val ex1 = insertGrammarExercise(topicId, "fill_in_blank", "p1", "a1")
        val ex2 = insertGrammarExercise(topicId, "fill_in_blank", "p2", "a2")
        val ex3 = insertGrammarExercise(topicId, "fill_in_blank", "p3", "a3")

        val session = grammarService.startOrResumeSession(userId, topicId.toString())!!
        grammarService.answer(userId, session.sessionId, GrammarAnswerRequest(ex1.toString(), "a1", correct = true))
        grammarService.answer(userId, session.sessionId, GrammarAnswerRequest(ex2.toString(), "wrong", correct = false))
        grammarService.answer(userId, session.sessionId, GrammarAnswerRequest(ex3.toString(), "a3", correct = true))

        val summary = grammarService.complete(userId, session.sessionId)
        assertNotNull(summary)
        assertEquals(3, summary.exercisesCompleted)
        assertEquals(2.0 / 3.0 * 100.0, summary.accuracy, 0.001)
    }
}
