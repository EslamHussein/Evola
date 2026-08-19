package evola.composeapp.lessons

import evola.shared.vocabulary.VocabularyRepository
import pro.respawn.flowmvi.android.StoreViewModel

class BrowseFlashcardsViewModel(lessonId: String, repository: VocabularyRepository) :
    StoreViewModel<BrowseFlashcardsState, BrowseFlashcardsIntent, Nothing>(BrowseFlashcardsContainer(lessonId, repository))
