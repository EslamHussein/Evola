package evola.shared.goals

import kotlinx.datetime.LocalDate

enum class Skill { VOCABULARY, GRAMMAR, WRITING, SPEAKING }

/**
 * Not yet wired to a REST endpoint (that's Milestone 2 — goal creation + deterministic pace
 * checking), so this isn't @Serializable yet. readinessOverall/readinessBySkill are always a
 * deterministic rollup of MasteryScore rows — never a model call (spec §4 non-goals).
 */
data class Goal(
    val id: String,
    val userId: String,
    val examType: String,
    val targetDate: LocalDate,
    val readinessOverall: Float,
    val readinessBySkill: Map<Skill, Float>,
)

data class PlannedSession(val id: String, val skill: Skill, val dueDate: LocalDate)

data class StudyPlan(val id: String, val goalId: String, val sessions: List<PlannedSession>)
