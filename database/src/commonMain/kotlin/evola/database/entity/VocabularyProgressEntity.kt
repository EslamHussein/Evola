package evola.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Lingvist-style flat-queue engine. status is one of unseen|introduced|learning|review|mastered
 * (evola.shared.feature.vocabulary.domain.VocabularySrs.STATUSES). */
@Entity(
    tableName = "vocabulary_progress",
    foreignKeys = [
        ForeignKey(entity = VocabularyItemEntity::class, parentColumns = ["id"], childColumns = ["vocabulary_item_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index(value = ["user_id", "next_review_at"], name = "vocabulary_progress_due_idx"), Index("vocabulary_item_id")],
)
data class VocabularyProgressEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "vocabulary_item_id") val vocabularyItemId: String,
    val status: String,
    @ColumnInfo(name = "correct_streak") val correctStreak: Long,
    @ColumnInfo(name = "incorrect_streak") val incorrectStreak: Long,
    @ColumnInfo(name = "interval_index") val intervalIndex: Long,
    @ColumnInfo(name = "next_review_at") val nextReviewAt: Long,
    @ColumnInfo(name = "last_seen_at") val lastSeenAt: Long?,
    @ColumnInfo(name = "is_bookmarked") val isBookmarked: Long,
    @ColumnInfo(name = "marked_difficult") val markedDifficult: Long,
)
