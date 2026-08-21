package evola.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import evola.database.entity.GoalEntity

@Dao
interface GoalDao {
    @Insert
    suspend fun insert(goal: GoalEntity)

    @Query("SELECT * FROM goals WHERE user_id = :userId AND is_active = 1 LIMIT 1")
    suspend fun selectActive(userId: String): GoalEntity?

    @Query("SELECT * FROM goals WHERE id = :id")
    suspend fun selectById(id: String): GoalEntity?

    @Query("UPDATE goals SET goal_text = :goalText, title = :title, native_language = :nativeLanguage, updated_at = :updatedAt WHERE id = :id")
    suspend fun update(goalText: String, title: String?, nativeLanguage: String, updatedAt: Long, id: String)

    @Query("UPDATE goals SET is_active = 0 WHERE user_id = :userId")
    suspend fun deactivateAll(userId: String)

    // Backup.sq -----------------------------------------------------------

    @Query("SELECT * FROM goals WHERE user_id = :userId")
    suspend fun selectAllForUser(userId: String): List<GoalEntity>

    @Query("DELETE FROM goals WHERE user_id = :userId")
    suspend fun deleteAllForUser(userId: String)
}
