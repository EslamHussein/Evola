package evola.server

import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GoalServiceTest {

    private val database = TestDatabase.database
    private val authService = AuthService(database, jwtSecret = "test-secret")
    private val goalService = GoalService(database)

    @BeforeEach
    fun clearTables() {
        transaction(database) {
            GrammarProgressTable.deleteAll()
            GrammarTopicsTable.deleteAll()
            LessonsTable.deleteAll()
            MaterialsTable.deleteAll()
            GoalsTable.deleteAll()
            RefreshTokensTable.deleteAll()
            UsersTable.deleteAll()
        }
    }

    /** Lessons have an FK to materials, so a lesson-list test needs a real (throwaway) material row. */
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

    private fun insertLesson(materialId: UUID, goalId: String, number: Int, title: String, status: String, createdAt: Instant) {
        transaction(database) {
            LessonsTable.insert {
                it[id] = UUID.randomUUID()
                it[this.materialId] = materialId
                it[this.goalId] = UUID.fromString(goalId)
                it[this.number] = number
                it[this.title] = title
                it[this.status] = status
                it[this.createdAt] = createdAt
            }
        }
    }

    private suspend fun registerUser(): String {
        val outcome = authService.register(RegisterRequest("Amara Nwosu", "amara@example.com", "Passw0rd!"))
        return (outcome as RegisterOutcome.Created).tokens.user.id
    }

    @Test
    fun `creating a goal auto-titles it when blank and flips onboarding_completed`() = runTest {
        val userId = registerUser()
        val outcome = goalService.createGoal(userId, CreateGoalRequest("Pass the German B1 Exam", title = null))

        assertIs<CreateGoalOutcome.Created>(outcome)
        assertEquals("Pass the German B1 Exam", outcome.goal.goalText)
        assertTrue(outcome.goal.title!!.startsWith("My "))
        assertTrue(outcome.goal.isActive)

        val user = authService.getUser(userId)
        assertNotNull(user)
        assertTrue(user.onboardingCompleted)
    }

    @Test
    fun `creating a goal keeps an explicit title`() = runTest {
        val userId = registerUser()
        val outcome = goalService.createGoal(userId, CreateGoalRequest("Pass the German B1 Exam", title = "My B1 Push"))
        assertIs<CreateGoalOutcome.Created>(outcome)
        assertEquals("My B1 Push", outcome.goal.title)
    }

    @Test
    fun `a second active goal is rejected`() = runTest {
        val userId = registerUser()
        goalService.createGoal(userId, CreateGoalRequest("Pass the German B1 Exam"))
        val second = goalService.createGoal(userId, CreateGoalRequest("Learn conversational Spanish"))
        assertIs<CreateGoalOutcome.ActiveGoalExists>(second)
    }

    @Test
    fun `goal text outside 3-200 characters is rejected`() = runTest {
        val userId = registerUser()
        assertIs<CreateGoalOutcome.Invalid>(goalService.createGoal(userId, CreateGoalRequest("ab")))
        assertIs<CreateGoalOutcome.Invalid>(goalService.createGoal(userId, CreateGoalRequest("a".repeat(201))))
    }

    @Test
    fun `updating a goal changes only the provided fields`() = runTest {
        val userId = registerUser()
        val created = goalService.createGoal(userId, CreateGoalRequest("Pass the German B1 Exam", "Original Title"))
        assertIs<CreateGoalOutcome.Created>(created)

        val updated = goalService.updateGoal(userId, created.goal.id, UpdateGoalRequest(title = "New Title"))
        assertIs<UpdateGoalOutcome.Updated>(updated)
        assertEquals("New Title", updated.goal.title)
        assertEquals("Pass the German B1 Exam", updated.goal.goalText)
    }

    @Test
    fun `updating a goal that does not belong to the user is not found`() = runTest {
        val userId = registerUser()
        val created = goalService.createGoal(userId, CreateGoalRequest("Pass the German B1 Exam"))
        assertIs<CreateGoalOutcome.Created>(created)

        val otherUserId = java.util.UUID.randomUUID().toString()
        val outcome = goalService.updateGoal(otherUserId, created.goal.id, UpdateGoalRequest(title = "Hijacked"))
        assertIs<UpdateGoalOutcome.NotFound>(outcome)
    }

    @Test
    fun `getActiveGoal returns null when no goal exists yet`() = runTest {
        val userId = registerUser()
        assertNull(goalService.getActiveGoal(userId))
    }

    @Test
    fun `getActiveGoal returns the goal once created`() = runTest {
        val userId = registerUser()
        goalService.createGoal(userId, CreateGoalRequest("Pass the German B1 Exam"))
        val active = goalService.getActiveGoal(userId)
        assertNotNull(active)
        assertEquals("Pass the German B1 Exam", active.goalText)
    }

    @Test
    fun `listLessonsForGoal returns lessons in sequential order, oldest material first`() = runTest {
        val userId = registerUser()
        val created = goalService.createGoal(userId, CreateGoalRequest("Pass the German B1 Exam"))
        assertIs<CreateGoalOutcome.Created>(created)
        val goalId = created.goal.id
        val now = Instant.now()

        val firstMaterial = insertMaterial(userId, goalId)
        insertLesson(firstMaterial, goalId, number = 2, title = "Second", status = "pending", createdAt = now.plusSeconds(1))
        insertLesson(firstMaterial, goalId, number = 1, title = "First", status = "ready", createdAt = now)

        val secondMaterial = insertMaterial(userId, goalId)
        insertLesson(secondMaterial, goalId, number = 1, title = "Later material's lesson", status = "pending", createdAt = now.plusSeconds(10))

        val lessons = goalService.listLessonsForGoal(userId, goalId)
        assertNotNull(lessons)
        assertEquals(listOf("First", "Second", "Later material's lesson"), lessons.map { it.title })
        assertEquals("ready", lessons[0].status)
        assertEquals("pending", lessons[1].status)
    }

    @Test
    fun `listLessonsForGoal returns null for a goal that does not belong to the user`() = runTest {
        val userId = registerUser()
        val created = goalService.createGoal(userId, CreateGoalRequest("Pass the German B1 Exam"))
        assertIs<CreateGoalOutcome.Created>(created)

        val otherUserId = UUID.randomUUID().toString()
        assertNull(goalService.listLessonsForGoal(otherUserId, created.goal.id))
    }

    @Test
    fun `listLessonsForGoal reports real grammar_count and grammar_progress alongside vocab_progress`() = runTest {
        val userId = registerUser()
        val created = goalService.createGoal(userId, CreateGoalRequest("Pass the German B1 exam"))
        assertIs<CreateGoalOutcome.Created>(created)
        val goalId = created.goal.id
        val now = Instant.now()

        val material = insertMaterial(userId, goalId)
        insertLesson(material, goalId, number = 1, title = "First", status = "ready", createdAt = now)
        val lessonId = transaction(database) {
            LessonsTable.selectAll().single()[LessonsTable.id]
        }

        transaction(database) {
            val topicId = UUID.randomUUID()
            GrammarTopicsTable.insert {
                it[id] = topicId
                it[this.lessonId] = lessonId
                it[name] = "Modalverben"
                it[explanation] = "Modal verbs express ability or necessity."
                it[createdAt] = Instant.now()
            }
            GrammarProgressTable.insert {
                it[id] = UUID.randomUUID()
                it[this.userId] = UUID.fromString(userId)
                it[this.topicId] = topicId
                it[masteryState] = "learning"
                it[correctStreak] = 1
                it[intervalIndex] = 1
                it[nextReviewAt] = Instant.now()
            }
        }

        val lessons = goalService.listLessonsForGoal(userId, goalId)
        assertNotNull(lessons)
        val lesson = lessons.single()
        assertEquals(1, lesson.grammarCount)
        assertEquals(1f / 3f, lesson.grammarProgress)
        assertEquals(0f, lesson.vocabProgress)
    }

    @Test
    fun `listLessonsForGoal returns an empty list when no materials have been uploaded yet`() = runTest {
        val userId = registerUser()
        val created = goalService.createGoal(userId, CreateGoalRequest("Pass the German B1 Exam"))
        assertIs<CreateGoalOutcome.Created>(created)

        val lessons = goalService.listLessonsForGoal(userId, created.goal.id)
        assertNotNull(lessons)
        assertTrue(lessons.isEmpty())
    }
}
