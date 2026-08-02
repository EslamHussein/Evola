package evola.server

import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
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
            GoalsTable.deleteAll()
            RefreshTokensTable.deleteAll()
            UsersTable.deleteAll()
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
}
