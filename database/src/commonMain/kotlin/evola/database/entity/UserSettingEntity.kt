package evola.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity

/** Single-user local settings: a plain key-value table rather than one column per setting, so a
 * new toggle never needs a schema migration - just a new key the typed wrapper
 * (evola.shared.feature.profile.data.LocalSettingsRepository) knows how to default when absent. */
@Entity(tableName = "user_settings", primaryKeys = ["user_id", "key"])
data class UserSettingEntity(
    @ColumnInfo(name = "user_id") val userId: String,
    val key: String,
    val value: String,
)
