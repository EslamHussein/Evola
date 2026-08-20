@file:OptIn(org.koin.core.annotation.KoinExperimentalAPI::class)

package evola.composeapp.feature.learning.ui

import evola.composeapp.core.navigation.MaterialsNavContext
import evola.composeapp.feature.learning.vm.GrammarExerciseSessionViewModel
import evola.composeapp.feature.learning.vm.GrammarTopicListViewModel
import evola.composeapp.feature.learning.vm.LessonDetailViewModel
import evola.composeapp.main.MaterialsRoute
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import org.koin.dsl.module
import org.koin.dsl.navigation3.navigation

/** The `feature/learning` subset of the Materials-tab [MaterialsRoute] destinations, split out
 * of [evola.composeapp.main.materialsNavigationModule] (see that module's doc comment for the
 * overall back-stack/DI rationale, which still applies unchanged here). [MaterialsRoute] itself
 * stays a single sealed interface shared by every Materials-tab destination - splitting the route
 * TYPE would break the shared Nav3 back stack, so only the route REGISTRATIONS for learning's own
 * screens (lesson detail, grammar topics, grammar session) move here. [MaterialsNavContext] is
 * registered as a `single` in [evola.composeapp.main.materialsNavigationModule], not here - both
 * modules are wired into the same Koin instance via `modules(...)` in `App.kt`, so
 * `koinInject<MaterialsNavContext>()` below resolves the one shared instance regardless of which
 * module registered it. */
val learningNavigationModule = module {
    navigation<MaterialsRoute.LessonDetail> { route ->
        val context = koinInject<MaterialsNavContext>()
        val viewModel = koinViewModel<LessonDetailViewModel>(key = route.lessonId) {
            parametersOf(route.lessonId)
        }
        LessonDetailScreen(
            viewModel = viewModel,
            onBack = { context.backStack.removeLastOrNull() },
            onOpenSection = { key ->
                when (key) {
                    "vocabulary" -> context.backStack.add(MaterialsRoute.Session(route.lessonId, route.materialId))
                    "grammar" -> context.backStack.add(MaterialsRoute.GrammarTopics(route.lessonId, route.materialId))
                }
            },
            onViewVocabularyList = {
                context.backStack.add(MaterialsRoute.VocabularyList(route.lessonId, route.materialId))
            },
        )
    }

    navigation<MaterialsRoute.GrammarTopics> { route ->
        val context = koinInject<MaterialsNavContext>()
        val viewModel = koinViewModel<GrammarTopicListViewModel>(key = route.lessonId) {
            parametersOf(route.lessonId)
        }
        GrammarTopicListScreen(
            viewModel = viewModel,
            onOpenTopic = { topicId ->
                context.backStack.add(MaterialsRoute.GrammarSession(route.lessonId, topicId, route.materialId))
            },
            onBack = { context.backStack.removeLastOrNull() },
        )
    }

    navigation<MaterialsRoute.GrammarSession> { route ->
        val context = koinInject<MaterialsNavContext>()
        val viewModel = koinViewModel<GrammarExerciseSessionViewModel>(key = route.topicId) {
            parametersOf(route.topicId)
        }
        GrammarExerciseSessionScreen(
            viewModel = viewModel,
            onDone = { context.backStack.removeLastOrNull() },
        )
    }
}
