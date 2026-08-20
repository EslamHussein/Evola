package evola.composeapp.main

import evola.shared.feature.vocabulary.domain.SessionMode
import evola.shared.feature.vocabulary.domain.WordCategory
import kotlinx.serialization.Serializable

/** Navigation 3 route model for the Materials tab, mirroring the (now-removed) `MaterialsSubScreen`
 * sealed interface 1:1 - see git history for that version if a transition's old shape needs
 * checking. [materialId] is null on several routes when reached from Home's "Continue lesson" CTA
 * rather than by drilling into a specific material - [LessonDetail] and everything below it fetch
 * purely by lessonId either way, so the only thing a null materialId changes is where "back" lands.
 *
 * None of these routes carry a `goalId` field - the goal is effectively constant for the lifetime of
 * a Materials-tab session (only editable from Profile, which is a different tab), so every route
 * that needs it reads [MaterialsNavContext.goalId] instead of repeating the field on every route.
 *
 * [Wizard] deliberately carries no payload - the staged resource it needs (which can carry raw file
 * bytes, potentially several MB) is read from [MaterialsNavContext] instead of the route itself,
 * since Navigation 3's back stack is serialized for state save/restore and a multi-MB [ByteArray] in
 * a route risks exceeding Android's Binder transaction size limit on restore. */
@Serializable
sealed interface MaterialsRoute {
    @Serializable data object List : MaterialsRoute
    @Serializable data object Add : MaterialsRoute
    @Serializable data object Wizard : MaterialsRoute
    @Serializable data class Processing(val materialId: String) : MaterialsRoute
    @Serializable data class Detail(val materialId: String) : MaterialsRoute
    @Serializable data class LessonDetail(val lessonId: String, val materialId: String?) : MaterialsRoute
    @Serializable data class Session(val lessonId: String, val materialId: String?) : MaterialsRoute
    @Serializable data class HandsFreeSession(val lessonId: String, val materialId: String?) : MaterialsRoute
    @Serializable data class BrowseFlashcards(val lessonId: String, val materialId: String?) : MaterialsRoute
    @Serializable data class CategorySession(val category: WordCategory) : MaterialsRoute
    @Serializable data class ModeSession(val mode: SessionMode) : MaterialsRoute
    @Serializable data class VocabularyList(val lessonId: String, val materialId: String?) : MaterialsRoute
    @Serializable data class GrammarTopics(val lessonId: String, val materialId: String?) : MaterialsRoute
    @Serializable data class GrammarSession(val lessonId: String, val topicId: String, val materialId: String?) : MaterialsRoute
}
