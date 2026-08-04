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
            DailyActivityTable.deleteAll()
            GrammarProgressTable.deleteAll()
            GrammarTopicsTable.deleteAll()
            VocabularyProgressTable.deleteAll()
            VocabularyItemsTable.deleteAll()
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

    /** Like [insertLesson] but returns the id, so a progress test can seed items against it. */
    private fun insertLessonReturningId(materialId: UUID, goalId: String, number: Int, createdAt: Instant): UUID {
        val lessonId = UUID.randomUUID()
        transaction(database) {
            LessonsTable.insert {
                it[id] = lessonId
                it[this.materialId] = materialId
                it[this.goalId] = UUID.fromString(goalId)
                it[this.number] = number
                it[title] = "Lesson $number"
                it[status] = "ready"
                it[this.createdAt] = createdAt
            }
        }
        return lessonId
    }

    /** Seeds one vocabulary item at [masteryState] so a lesson has a real, non-zero completion pct. */
    private fun insertVocabItem(lessonId: UUID, userId: String, term: String, masteryState: String) {
        transaction(database) {
            val itemId = UUID.randomUUID()
            VocabularyItemsTable.insert {
                it[id] = itemId
                it[this.lessonId] = lessonId
                it[this.term] = term
                it[meaning] = "meaning of $term"
                it[createdAt] = Instant.now()
            }
            VocabularyProgressTable.insert {
                it[id] = UUID.randomUUID()
                it[this.userId] = UUID.fromString(userId)
                it[vocabularyItemId] = itemId
                it[this.masteryState] = masteryState
                it[correctStreak] = 0
                it[intervalIndex] = 0
                it[nextReviewAt] = Instant.now()
            }
        }
    }

    private fun insertDailyActivity(userId: String, date: java.time.LocalDate) {
        transaction(database) {
            DailyActivityTable.insert {
                it[id] = UUID.randomUUID()
                it[this.userId] = UUID.fromString(userId)
                it[activityDate] = date
                it[completed] = true
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

    // --- getProgress (M8 Progress Dashboard, 01_PRODUCT_SPEC.md §1.10) ---

    @Test
    fun `getProgress returns an honest zero state for a goal with no lessons yet`() = runTest {
        val userId = registerUser()
        val created = goalService.createGoal(userId, CreateGoalRequest("Pass the German B1 exam"))
        assertIs<CreateGoalOutcome.Created>(created)

        val progress = goalService.getProgress(userId, created.goal.id, localDate = "2026-08-04")
        assertNotNull(progress)
        assertEquals(0f, progress.overallPct)
        assertNull(progress.currentLessonId)
        assertEquals(0, progress.streakDays)
        assertEquals(false, progress.todayCompleted)
    }

    @Test
    fun `getProgress averages every lesson's completion and points at the first incomplete one`() = runTest {
        val userId = registerUser()
        val created = goalService.createGoal(userId, CreateGoalRequest("Pass the German B1 exam"))
        assertIs<CreateGoalOutcome.Created>(created)
        val goalId = created.goal.id
        val now = Instant.now()

        val material = insertMaterial(userId, goalId)
        val lesson1 = insertLessonReturningId(material, goalId, number = 1, createdAt = now)
        val lesson2 = insertLessonReturningId(material, goalId, number = 2, createdAt = now.plusSeconds(1))

        // Lesson 1 fully mastered (100%), lesson 2 untouched (0%) -> overall 50%, and lesson 2 is
        // the first one still below 100%.
        insertVocabItem(lesson1, userId, "Hund", masteryState = "mastered")
        insertVocabItem(lesson2, userId, "Katze", masteryState = "new")

        val progress = goalService.getProgress(userId, goalId, localDate = "2026-08-04")
        assertNotNull(progress)
        assertEquals(0.5f, progress.overallPct)
        assertEquals(lesson2.toString(), progress.currentLessonId)
    }

    @Test
    fun `getProgress reports a null current lesson once every lesson is complete`() = runTest {
        val userId = registerUser()
        val created = goalService.createGoal(userId, CreateGoalRequest("Pass the German B1 exam"))
        assertIs<CreateGoalOutcome.Created>(created)
        val goalId = created.goal.id

        val material = insertMaterial(userId, goalId)
        val lessonId = insertLessonReturningId(material, goalId, number = 1, createdAt = Instant.now())
        insertVocabItem(lessonId, userId, "Hund", masteryState = "mastered")

        val progress = goalService.getProgress(userId, goalId, localDate = "2026-08-04")
        assertNotNull(progress)
        assertEquals(1f, progress.overallPct)
        assertNull(progress.currentLessonId)
    }

    @Test
    fun `getProgress computes the streak and today_completed against the caller's own local date`() = runTest {
        val userId = registerUser()
        val created = goalService.createGoal(userId, CreateGoalRequest("Pass the German B1 exam"))
        assertIs<CreateGoalOutcome.Created>(created)

        val today = java.time.LocalDate.of(2026, 8, 4)
        insertDailyActivity(userId, today)
        insertDailyActivity(userId, today.minusDays(1))
        insertDailyActivity(userId, today.minusDays(2))

        val progress = goalService.getProgress(userId, created.goal.id, localDate = today.toString())
        assertNotNull(progress)
        assertEquals(3, progress.streakDays)
        assertEquals(true, progress.todayCompleted)

        // The SAME seeded data, read a day later (the user hasn't studied yet today): the streak is
        // still alive at 3 - it only resets the following day - but today_completed is false.
        val tomorrow = goalService.getProgress(userId, created.goal.id, localDate = today.plusDays(1).toString())
        assertNotNull(tomorrow)
        assertEquals(3, tomorrow.streakDays)
        assertEquals(false, tomorrow.todayCompleted)
    }

    @Test
    fun `getProgress returns null for a goal that does not belong to the user`() = runTest {
        val userId = registerUser()
        val created = goalService.createGoal(userId, CreateGoalRequest("Pass the German B1 exam"))
        assertIs<CreateGoalOutcome.Created>(created)

        assertNull(goalService.getProgress(UUID.randomUUID().toString(), created.goal.id, localDate = "2026-08-04"))
    }
}
