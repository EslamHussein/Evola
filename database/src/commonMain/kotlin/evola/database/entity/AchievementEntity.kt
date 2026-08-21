package evola.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Reword's achievement badges - a fixed, code-defined badge set (see
 * evola.shared.feature.profile.domain.ALL_BADGES), this table only records which ones this user has
 * unlocked and when. Append-only, tiny. */
@Entity(tableName = "achievements", indices = [Index(value = ["user_id", "badge_id"], unique = true)])
data class AchievementEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "badge_id") val badgeId: String,
    @ColumnInfo(name = "unlocked_at") val unlockedAt: Long,
)
