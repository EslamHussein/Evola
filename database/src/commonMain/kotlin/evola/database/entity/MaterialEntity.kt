package evola.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "materials")
data class MaterialEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "goal_id") val goalId: String,
    val filename: String,
    @ColumnInfo(name = "content_hash") val contentHash: String,
    val status: String,
    @ColumnInfo(name = "mime_type") val mimeType: String,
    @ColumnInfo(name = "size_bytes") val sizeBytes: Long,
    @ColumnInfo(name = "page_count") val pageCount: Long?,
    @ColumnInfo(name = "organization_mode") val organizationMode: String,
    @ColumnInfo(name = "ai_instructions") val aiInstructions: String?,
    @ColumnInfo(name = "resource_type") val resourceType: String?,
    @ColumnInfo(name = "content_text") val contentText: String?,
    @ColumnInfo(name = "input_tokens", defaultValue = "0") val inputTokens: Long,
    @ColumnInfo(name = "output_tokens", defaultValue = "0") val outputTokens: Long,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)
