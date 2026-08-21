package evola.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import evola.database.entity.PingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PingDao {
    @Insert
    suspend fun insert(entity: PingEntity)

    @Query("SELECT * FROM PingEntity")
    fun observeAll(): Flow<List<PingEntity>>
}
