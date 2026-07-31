package evola.tutoring.infrastructure

import evola.core.kernel.DialogueTurnId
import evola.core.kernel.LearnerId
import evola.core.kernel.TutoringSessionId
import evola.tutoring.application.DialogueTurnRepository
import evola.tutoring.domain.DialogueTurn
import evola.tutoring.domain.ExerciseKind
import evola.tutoring.domain.LearningMode
import evola.tutoring.domain.TurnRole
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

class ExposedDialogueTurnRepository(private val database: Database) : DialogueTurnRepository {

    override suspend fun findBySession(sessionId: TutoringSessionId): List<DialogueTurn> =
        newSuspendedTransaction(Dispatchers.IO, database) {
            DialogueTurnsTable.selectAll().where { DialogueTurnsTable.sessionId eq sessionId.value }
                .orderBy(DialogueTurnsTable.turnIndex, SortOrder.ASC)
                .map { it.toTurn() }
        }

    override suspend fun append(turn: DialogueTurn) {
        newSuspendedTransaction(Dispatchers.IO, database) {
            DialogueTurnsTable.insert {
                it[id] = turn.id.value
                it[sessionId] = turn.sessionId.value
                it[turnIndex] = turn.turnIndex
                it[role] = turn.role.name
                it[exerciseKind] = turn.exerciseKind?.name
                it[content] = turn.content
                it[correctAnswer] = turn.correctAnswer
                it[explanation] = turn.explanation
                it[wasCorrect] = turn.wasCorrect
                it[createdAt] = turn.createdAt
            }
        }
    }

    override suspend fun mostFrequentWrongGrammarTopic(learnerId: LearnerId): String? =
        newSuspendedTransaction(Dispatchers.IO, database) {
            val sessionIdToTopic = TutoringSessionsTable.selectAll()
                .where { (TutoringSessionsTable.learnerId eq learnerId.value) and (TutoringSessionsTable.mode eq LearningMode.GRAMMAR.name) }
                .associate { it[TutoringSessionsTable.id] to it[TutoringSessionsTable.focusGrammarTopic] }

            if (sessionIdToTopic.isEmpty()) return@newSuspendedTransaction null

            val wrongTurns = DialogueTurnsTable.selectAll().where {
                (DialogueTurnsTable.sessionId inList sessionIdToTopic.keys) and
                    (DialogueTurnsTable.role eq TurnRole.LEARNER_ANSWER.name) and
                    (DialogueTurnsTable.wasCorrect eq false)
            }

            wrongTurns
                .mapNotNull { sessionIdToTopic[it[DialogueTurnsTable.sessionId]] }
                .groupingBy { it }
                .eachCount()
                .maxByOrNull { it.value }
                ?.key
        }

    private fun ResultRow.toTurn() = DialogueTurn(
        id = DialogueTurnId(this[DialogueTurnsTable.id]),
        sessionId = TutoringSessionId(this[DialogueTurnsTable.sessionId]),
        turnIndex = this[DialogueTurnsTable.turnIndex],
        role = TurnRole.valueOf(this[DialogueTurnsTable.role]),
        exerciseKind = this[DialogueTurnsTable.exerciseKind]?.let { ExerciseKind.valueOf(it) },
        content = this[DialogueTurnsTable.content],
        correctAnswer = this[DialogueTurnsTable.correctAnswer],
        explanation = this[DialogueTurnsTable.explanation],
        wasCorrect = this[DialogueTurnsTable.wasCorrect],
        createdAt = this[DialogueTurnsTable.createdAt],
    )
}
