package evola.learneridentity.application

import evola.core.application.Command
import evola.core.application.UseCase
import evola.core.kernel.LearnerId
import evola.learneridentity.domain.Channel
import evola.learneridentity.domain.ExternalIdentity
import evola.learneridentity.domain.ExternalIdentityRepository
import evola.learneridentity.domain.Learner
import evola.learneridentity.domain.LearnerRepository

data class RegisterLearnerCommand(
    val channel: Channel,
    val externalId: String,
    val displayName: String?,
) : Command<LearnerId>

/** Idempotent: resolves the existing Learner for this channel identity, or creates one. */
class RegisterLearnerHandler(
    private val learnerRepository: LearnerRepository,
    private val externalIdentityRepository: ExternalIdentityRepository,
) : UseCase<RegisterLearnerCommand, LearnerId> {

    override suspend fun handle(input: RegisterLearnerCommand): LearnerId {
        val existing = externalIdentityRepository.findByChannelAndExternalId(input.channel, input.externalId)
        if (existing != null) {
            return existing.learnerId
        }

        val learner = Learner.register()
        learnerRepository.save(learner)

        val identity = ExternalIdentity.create(
            learnerId = learner.id,
            channel = input.channel,
            externalId = input.externalId,
            displayName = input.displayName,
        )
        externalIdentityRepository.save(identity)

        return learner.id
    }
}
