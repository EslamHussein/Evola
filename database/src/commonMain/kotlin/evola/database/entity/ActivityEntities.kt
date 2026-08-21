package evola.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "daily_activity", indices = [Index(value = ["user_id", "activity_date"], unique = true)])
data class DailyActivityEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "activity_date") val activityDate: String,
    val completed: Long,
)

/** Reword's "Streak freeze" - a bridged date is treated as if it had real activity for streak-
 * continuity purposes only. Append-only, tiny (bounded by however many freezes the user is ever
 * granted). */
@Entity(tableName = "streak_freeze_dates", indices = [Index(value = ["user_id", "freeze_date"], unique = true)])
data class StreakFreezeDateEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "freeze_date") val freezeDate: String,
)

/** Extraction pipeline scratch (content-hash keyed, not user-scoped) - kept for the on-device
 * segmentation port; no cross-user cache semantics remain on a single device. */
@Entity(tableName = "extraction_jobs", indices = [Index(value = ["content_hash"], unique = true)])
data class ExtractionJobEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "content_hash") val contentHash: String,
    val status: String,
    val error: String?,
    @ColumnInfo(name = "content_text") val contentText: String,
    @ColumnInfo(name = "failed_ranges") val failedRanges: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
