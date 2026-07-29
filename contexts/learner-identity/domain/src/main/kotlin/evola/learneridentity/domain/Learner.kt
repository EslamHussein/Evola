package evola.learneridentity.domain

import evola.core.kernel.LearnerId
import java.time.Instant

data class Learner(
    val id: LearnerId,
    val createdAt: Instant,
) {
    companion object {
        fun register(now: Instant = Instant.now()) = Learner(id = LearnerId.new(), createdAt = now)
    }
}
