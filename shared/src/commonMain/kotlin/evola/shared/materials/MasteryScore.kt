package evola.shared.materials

import kotlinx.datetime.Instant

/**
 * Deterministic, cross-skill mastery representation (spec §6) — never touches the model router
 * (spec §4 non-goals). Updated per practice item using the SM-2 machinery already in
 * evola.vocabulary.domain (Sm2Scheduler / VocabularyReviewService), not reimplemented here.
 */
data class MasteryScore(
    val userId: String,
    val itemId: String,
    val score: Float,
    val lastReviewedAt: Instant,
    val nextDueAt: Instant,
)
