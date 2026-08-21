package evola.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import evola.database.entity.GermanNounEntity

@Dao
interface GermanNounDao {
    @Query("SELECT COUNT(*) FROM german_nouns")
    suspend fun count(): Long

    @Insert
    suspend fun insert(noun: GermanNounEntity)

    @Query("SELECT * FROM german_nouns WHERE lemma = :lemma COLLATE NOCASE LIMIT 1")
    suspend fun lookup(lemma: String): GermanNounEntity?
}
