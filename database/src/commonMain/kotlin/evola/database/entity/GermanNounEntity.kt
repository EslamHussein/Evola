package evola.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** One-time local copy of the bundled German noun dataset (gambolputty/german-nouns, CC-BY-SA-4.0).
 * Populated once by GermanNounImporter from the composeResources CSV asset. The original SQLDelight
 * table had no primary key (plain rowid rows); [id] is a Room-required surrogate autoGenerate key -
 * lemma is deliberately NOT unique (homographs like der/die Bank share a lemma), matching the
 * original schema's lack of a uniqueness constraint. */
@Entity(tableName = "german_nouns", indices = [Index("lemma")])
data class GermanNounEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val lemma: String,
    @ColumnInfo(name = "part_of_speech") val partOfSpeech: String,
    val genus: String?,
    @ColumnInfo(name = "nominativ_plural") val nominativPlural: String?,
    @ColumnInfo(name = "raw_row") val rawRow: String,
)
