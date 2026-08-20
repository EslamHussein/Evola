package evola.shared.local

import evola.shared.achievements.ALL_BADGES
import evola.shared.achievements.AchievementsRepository
import evola.shared.achievements.BadgeDefinition
import evola.shared.core.common.ApiResult
import evola.shared.core.common.LOCAL_USER
import evola.shared.core.common.newId
import evola.shared.core.common.nowMillis
import evola.shared.db.EvolaDatabase

/** Threshold each badge unlocks at, keyed by [BadgeDefinition.id] - kept alongside [ALL_BADGES]'s
 * own id/title/description (not folded into the shared data class) since it's evaluation logic,
 * not display data. [masteredCount]/[streakDays] are the only two signals badges are evaluated
 * against today - see [AchievementsRepository.checkAndUnlock]. */
private val MASTERED_THRESHOLDS = mapOf("first_word_mastered" to 1, "ten_words_mastered" to 10, "fifty_words_mastered" to 50, "hundred_words_mastered" to 100)
private val STREAK_THRESHOLDS = mapOf("three_day_streak" to 3, "seven_day_streak" to 7, "thirty_day_streak" to 30)

class LocalAchievementsRepository(private val db: EvolaDatabase) : AchievementsRepository {

    override suspend fun unlockedBadgeIds(): ApiResult<Set<String>> =
        ApiResult.Success(db.achievementsQueries.unlockedBadgeIds(LOCAL_USER).executeAsList().toSet())

    override suspend fun checkAndUnlock(masteredCount: Int, streakDays: Int): ApiResult<List<BadgeDefinition>> {
        val alreadyUnlocked = db.achievementsQueries.unlockedBadgeIds(LOCAL_USER).executeAsList().toSet()
        val newlyUnlocked = ALL_BADGES.filter { badge ->
            badge.id !in alreadyUnlocked &&
                (MASTERED_THRESHOLDS[badge.id]?.let { masteredCount >= it } ?: STREAK_THRESHOLDS[badge.id]?.let { streakDays >= it } ?: false)
        }
        val now = nowMillis()
        newlyUnlocked.forEach { badge -> db.achievementsQueries.unlock(newId(), LOCAL_USER, badge.id, now) }
        return ApiResult.Success(newlyUnlocked)
    }
}
