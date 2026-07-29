package evola.learneridentity.domain

import evola.core.kernel.LearnerId

interface LearnerRepository {
    suspend fun findById(id: LearnerId): Learner?
    suspend fun save(learner: Learner)
}

interface ExternalIdentityRepository {
    suspend fun findByChannelAndExternalId(channel: Channel, externalId: String): ExternalIdentity?
    suspend fun save(identity: ExternalIdentity)
}
