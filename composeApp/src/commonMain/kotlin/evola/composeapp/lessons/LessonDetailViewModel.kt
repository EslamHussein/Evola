package evola.composeapp.lessons

import evola.shared.lessons.LessonsRepository
import pro.respawn.flowmvi.android.StoreViewModel

class LessonDetailViewModel(lessonId: String, repository: LessonsRepository) :
    StoreViewModel<LessonDetailState, LessonDetailIntent, Nothing>(LessonDetailContainer(lessonId, repository))
