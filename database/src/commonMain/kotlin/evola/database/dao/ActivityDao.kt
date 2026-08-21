package evola.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import evola.database.entity.DailyActivityEntity
import evola.database.entity.StreakFreezeDateEntity

@Dao
interface ActivityDao {
    @Query("SELECT activity_date FROM daily_activity WHERE user_id = :userId AND completed = 1")
    suspend fun completedDates(userId: String): List<String>

    @Query("SELECT * FROM daily_activity WHERE user_id = :userId AND activity_date = :activityDate")
    suspend fun forDate(userId: String, activityDate: String): DailyActivityEntity?

    /** We only ever write completed = 1, so a REPLACE on the (user_id, activity_date) unique key
     * is equivalent to the original's `INSERT OR REPLACE`. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(activity: DailyActivityEntity)

    @Query("SELECT freeze_date FROM streak_freeze_dates WHERE user_id = :userId")
    suspend fun frozenDates(userId: String): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertFreeze(freeze: StreakFreezeDateEntity)

    // Backup.sq -----------------------------------------------------------

    @Query("SELECT * FROM daily_activity WHERE user_id = :userId")
    suspend fun selectAllForUser(userId: String): List<DailyActivityEntity>

    @Query("DELETE FROM daily_activity WHERE user_id = :userId")
    suspend fun deleteAllForUser(userId: String)
}
