package evola.composeapp.main

import evola.shared.goals.GoalsRepository
import evola.shared.vocabulary.VocabularyRepository
import pro.respawn.flowmvi.android.StoreViewModel

class ProfileViewModel(goalsRepository: GoalsRepository, vocabularyRepository: VocabularyRepository) :
    StoreViewModel<ProfileState, ProfileIntent, Nothing>(ProfileContainer(goalsRepository, vocabularyRepository))
