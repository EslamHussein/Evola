package evola.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Toolchain smoke-test entity — proves KSP + Room KMP codegen works on every target before the
 * real schema is ported over. Removed once the first real entity lands. */
@Entity
data class PingEntity(
    @PrimaryKey val id: String,
    val value: Long,
)
