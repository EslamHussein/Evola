package evola.database.entity

import androidx.room.ColumnInfo
import androidx.room.DatabaseView
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "lessons",
    foreignKeys = [
        ForeignKey(entity = MaterialEntity::class, parentColumns = ["id"], childColumns = ["material_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("material_id")],
)
data class LessonEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "material_id") val materialId: String,
    @ColumnInfo(name = "goal_id") val goalId: String,
    val number: Long,
    val title: String,
    val status: String,
    @ColumnInfo(name = "origin_kind", defaultValue = "curriculum") val originKind: String,
    @ColumnInfo(name = "source_label") val sourceLabel: String?,
    @ColumnInfo(name = "source_text_ref") val sourceTextRef: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

/** Mirrors Lessons.sq's `curriculum_lessons` view - authored curriculum lessons only, excluding
 * document-derived sections. See [LessonEntity]'s doc reference in the original .sq for the
 * origin_kind rationale. */
@DatabaseView(
    viewName = "curriculum_lessons",
    value = "SELECT * FROM lessons WHERE origin_kind = 'curriculum'",
)
data class CurriculumLessonView(
    val id: String,
    @ColumnInfo(name = "material_id") val materialId: String,
    @ColumnInfo(name = "goal_id") val goalId: String,
    val number: Long,
    val title: String,
    val status: String,
    @ColumnInfo(name = "origin_kind") val originKind: String,
    @ColumnInfo(name = "source_label") val sourceLabel: String?,
    @ColumnInfo(name = "source_text_ref") val sourceTextRef: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)
