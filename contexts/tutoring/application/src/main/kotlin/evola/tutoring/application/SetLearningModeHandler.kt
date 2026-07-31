package evola.tutoring.application

import evola.core.application.Command
import evola.core.application.UseCase
import evola.core.kernel.LearnerId
import evola.tutoring.domain.LearningMode

data class SetLearningModeCommand(val learnerId: LearnerId, val mode: LearningMode) : Command<Unit>

class SetLearningModeHandler(
    private val profileRepository: LearnerTutoringProfileRepository,
) : UseCase<SetLearningModeCommand, Unit> {
    override suspend fun handle(input: SetLearningModeCommand) {
        profileRepository.setActiveMode(input.learnerId, input.mode)
    }
}
