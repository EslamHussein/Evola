package evola.learneridentity.domain

import evola.core.kernel.LearnerId
import java.time.Instant
import java.util.UUID

enum class Channel(val code: String) {
    TELEGRAM("telegram");

    companion object {
        fun fromCode(code: String): Channel = entries.first { it.code == code }
    }
}

/**
 * Maps one external channel identity (e.g. a Telegram user id) to a single canonical [Learner].
 * A Learner has exactly one identity regardless of how many channels they use (see ADR D4) —
 * enforced by a unique (channel, externalId) constraint at the persistence layer.
 */
data class ExternalIdentity(
    val id: UUID,
    val learnerId: LearnerId,
    val channel: Channel,
    val externalId: String,
    val displayName: String?,
    val createdAt: Instant,
) {
    companion object {
        fun create(
            learnerId: LearnerId,
            channel: Channel,
            externalId: String,
            displayName: String?,
            now: Instant = Instant.now(),
        ) = ExternalIdentity(
            id = UUID.randomUUID(),
            learnerId = learnerId,
            channel = channel,
            externalId = externalId,
            displayName = displayName,
            createdAt = now,
        )
    }
}
