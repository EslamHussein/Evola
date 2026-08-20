package evola.shared.feature.profile.domain

import evola.shared.core.common.ApiResult

/** Reword's achievement badges - a fixed, code-defined set (not user/goal editable). [id] is the
 * stable string persisted in the `achievements` table, so reordering/renaming this list is safe as
 * long as [id]s themselves never change. */
data class BadgeDefinition(val id: String, val title: String, val description: String)

val ALL_BADGES: List<BadgeDefinition> = listOf(
    BadgeDefinition("first_word_mastered", "First Steps", "Master your first word"),
    BadgeDefinition("ten_words_mastered", "Getting Fluent", "Master 10 words"),
    BadgeDefinition("fifty_words_mastered", "Word Collector", "Master 50 words"),
    BadgeDefinition("hundred_words_mastered", "Vocabulary Master", "Master 100 words"),
    BadgeDefinition("three_day_streak", "Building Momentum", "Study 3 days in a row"),
    BadgeDefinition("seven_day_streak", "One Week Strong", "Study 7 days in a row"),
    BadgeDefinition("thirty_day_streak", "Unstoppable", "Study 30 days in a row"),
)

interface AchievementsRepository {
    suspend fun unlockedBadgeIds(): ApiResult<Set<String>>

    /** Checks [masteredCount]/[streakDays] against every badge's threshold and unlocks any not
     * already unlocked - idempotent (unlocking an already-unlocked badge is a no-op via the table's
     * own unique constraint). Returns only the badges newly unlocked by *this* call, for a one-shot
     * celebration - not the full unlocked set (see [unlockedBadgeIds] for that). */
    suspend fun checkAndUnlock(masteredCount: Int, streakDays: Int): ApiResult<List<BadgeDefinition>>
}
