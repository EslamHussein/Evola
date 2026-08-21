package evola.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import evola.database.entity.MaterialEntity

@Dao
interface MaterialDao {
    @Insert
    suspend fun insert(material: MaterialEntity)

    @Query("UPDATE materials SET input_tokens = input_tokens + :input, output_tokens = output_tokens + :output WHERE id = :id")
    suspend fun addTokenUsage(input: Long, output: Long, id: String)

    @Query("DELETE FROM materials WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM materials WHERE id = :id")
    suspend fun selectById(id: String): MaterialEntity?

    @Query("SELECT * FROM materials WHERE user_id = :userId ORDER BY created_at DESC")
    suspend fun selectByUser(userId: String): List<MaterialEntity>

    @Query("SELECT * FROM materials WHERE user_id = :userId AND content_hash = :contentHash")
    suspend fun selectByContentHash(userId: String, contentHash: String): List<MaterialEntity>

    @Query("UPDATE materials SET status = :status WHERE id = :id")
    suspend fun updateStatus(status: String, id: String)

    // Backup.sq -----------------------------------------------------------

    @Query("SELECT * FROM materials WHERE user_id = :userId")
    suspend fun selectAllForUser(userId: String): List<MaterialEntity>

    @Query("DELETE FROM materials WHERE user_id = :userId")
    suspend fun deleteAllForUser(userId: String)
}
