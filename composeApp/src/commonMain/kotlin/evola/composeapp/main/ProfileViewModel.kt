package evola.composeapp.main

import evola.shared.achievements.AchievementsRepository
import evola.shared.goals.GoalsRepository
import evola.shared.vocabulary.VocabularyRepository
import pro.respawn.flowmvi.android.StoreViewModel

class ProfileViewModel(
    goalId: String,
    goalsRepository: GoalsRepository,
    vocabularyRepository: VocabularyRepository,
    achievementsRepository: AchievementsRepository,
) : StoreViewModel<ProfileState, ProfileIntent, Nothing>(
    ProfileContainer(goalId, goalsRepository, vocabularyRepository, achievementsRepository),
)
