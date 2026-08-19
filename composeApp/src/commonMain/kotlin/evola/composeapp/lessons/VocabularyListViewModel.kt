package evola.composeapp.lessons

import evola.shared.vocabulary.VocabularyRepository
import pro.respawn.flowmvi.android.StoreViewModel

class VocabularyListViewModel(lessonId: String, goalId: String, repository: VocabularyRepository) :
    StoreViewModel<VocabularyListState, VocabularyListIntent, Nothing>(VocabularyListContainer(lessonId, goalId, repository))
