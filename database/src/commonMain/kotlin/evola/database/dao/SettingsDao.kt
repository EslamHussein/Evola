package evola.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import evola.database.entity.UserSettingEntity
import kotlinx.coroutines.flow.Flow

data class SettingKeyValue(val key: String, val value: String)

@Dao
interface SettingsDao {
    @Query("SELECT value FROM user_settings WHERE user_id = :userId AND key = :key")
    suspend fun get(userId: String, key: String): String?

    /** Every setting the app reads reactively (Settings screen, session screen, Home, the reminder
     * scheduler) goes through this Flow, keyed by user_id alone - a write to any key notifies
     * every listener, which is fine at this table's tiny size and write frequency. */
    @Query("SELECT key, value FROM user_settings WHERE user_id = :userId")
    fun all(userId: String): Flow<List<SettingKeyValue>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(setting: UserSettingEntity)

    // Backup.sq -----------------------------------------------------------

    @Query("SELECT * FROM user_settings WHERE user_id = :userId")
    suspend fun selectAllForUser(userId: String): List<UserSettingEntity>

    @Query("DELETE FROM user_settings WHERE user_id = :userId")
    suspend fun deleteAllForUser(userId: String)
}
