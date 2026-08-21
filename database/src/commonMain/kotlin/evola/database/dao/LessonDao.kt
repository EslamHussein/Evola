package evola.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import evola.database.entity.CurriculumLessonView
import evola.database.entity.LessonEntity

@Dao
interface LessonDao {
    @Insert
    suspend fun insert(lesson: LessonEntity)

    @Query("SELECT * FROM lessons WHERE id = :id")
    suspend fun selectById(id: String): LessonEntity?

    @Query("SELECT * FROM lessons WHERE material_id = :materialId ORDER BY number ASC")
    suspend fun selectByMaterial(materialId: String): List<LessonEntity>

    @Query("SELECT * FROM curriculum_lessons WHERE goal_id = :goalId ORDER BY created_at ASC, number ASC")
    suspend fun selectByGoal(goalId: String): List<CurriculumLessonView>

    /** Option B re-parenting check in LocalMaterialsRepository.deleteMaterial - document-derived
     * lessons for a material (see Vocabulary.sq's originatingItemsForLesson/reassignItemOrigin). */
    @Query("SELECT id FROM lessons WHERE material_id = :materialId AND origin_kind != 'curriculum'")
    suspend fun documentDerivedLessonIdsForMaterial(materialId: String): List<String>

    @Query("UPDATE lessons SET status = :status WHERE id = :id")
    suspend fun updateStatus(status: String, id: String)

    @Query("DELETE FROM lessons WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE lessons SET status = 'failed' WHERE material_id = :materialId AND status = 'extracting'")
    suspend fun sweepStuckExtracting(materialId: String)

    @Query("SELECT COUNT(*) FROM lessons WHERE material_id = :materialId")
    suspend fun countByMaterial(materialId: String): Long

    @Query("SELECT COUNT(*) FROM lessons WHERE material_id = :materialId AND status = 'ready'")
    suspend fun countReadyByMaterial(materialId: String): Long

    // Backup.sq -------------------------------------------------------------

    @Query("SELECT lessons.* FROM lessons JOIN materials ON materials.id = lessons.material_id WHERE materials.user_id = :userId")
    suspend fun selectAllForUser(userId: String): List<LessonEntity>

    @Query("DELETE FROM lessons WHERE material_id IN (SELECT id FROM materials WHERE user_id = :userId)")
    suspend fun deleteAllForUser(userId: String)
}
