package evola.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Persisted ordered queue: one row per card the session will show (or has shown). "Current card"
 * is the first unanswered row by position. card_type is one of new|practice
 * (evola.shared.feature.vocabulary.domain.VocabularyCard). choices (JSON-encoded, nullable) holds the shuffled
 * option set while a multiple-choice check is active for a `practice` row, persisted so a resumed
 * session shows the identical choices rather than a reshuffle. See Vocabulary.sq for the full
 * re-queue/origin semantics this table encodes. */
@Entity(
    tableName = "vocabulary_session_queue",
    foreignKeys = [
        ForeignKey(entity = VocabularySessionEntity::class, parentColumns = ["id"], childColumns = ["session_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = VocabularyItemEntity::class, parentColumns = ["id"], childColumns = ["vocabulary_item_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("session_id"), Index("vocabulary_item_id")],
)
data class VocabularySessionQueueEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "session_id") val sessionId: String,
    val position: Long,
    @ColumnInfo(name = "vocabulary_item_id") val vocabularyItemId: String,
    @ColumnInfo(name = "card_type") val cardType: String,
    val origin: String,
    val choices: String?,
    @ColumnInfo(name = "answered_at") val answeredAt: Long?,
    val correct: Long?,
    @ColumnInfo(name = "user_response") val userResponse: String?,
)
