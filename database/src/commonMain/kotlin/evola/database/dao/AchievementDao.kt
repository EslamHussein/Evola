package evola.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import evola.database.entity.AchievementEntity

@Dao
interface AchievementDao {
    @Query("SELECT badge_id FROM achievements WHERE user_id = :userId")
    suspend fun unlockedBadgeIds(userId: String): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun unlock(achievement: AchievementEntity)
}
