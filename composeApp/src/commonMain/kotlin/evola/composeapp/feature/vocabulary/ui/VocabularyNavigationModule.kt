@file:OptIn(org.koin.core.annotation.KoinExperimentalAPI::class)

package evola.composeapp.feature.vocabulary.ui

import evola.composeapp.core.navigation.MaterialsNavContext
import evola.composeapp.feature.vocabulary.vm.BrowseFlashcardsViewModel
import evola.composeapp.feature.vocabulary.vm.VocabSessionSource
import evola.composeapp.feature.vocabulary.vm.VocabularyListViewModel
import evola.composeapp.feature.vocabulary.vm.VocabularySessionViewModel
import evola.composeapp.main.MaterialsRoute
import evola.composeapp.speech.rememberSpeechService
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import org.koin.dsl.module
import org.koin.dsl.navigation3.navigation

/** The `feature/vocabulary` subset of the Materials-tab [MaterialsRoute] destinations, split out
 * of [evola.composeapp.main.materialsNavigationModule] (see that module's doc comment for the
 * overall back-stack/DI rationale, which still applies unchanged here). [MaterialsRoute] itself
 * stays a single sealed interface shared by every Materials-tab destination - splitting the route
 * TYPE would break the shared Nav3 back stack, so only the route REGISTRATIONS for vocabulary's own
 * screens move here. [MaterialsNavContext] is registered as a `single` in
 * [evola.composeapp.main.materialsNavigationModule], not here - both modules are wired into the
 * same Koin instance via `modules(...)` in `App.kt`, so `koinInject<MaterialsNavContext>()` below
 * resolves the one shared instance regardless of which module registered it. */
val vocabularyNavigationModule = module {
    navigation<MaterialsRoute.Session> { route ->
        val context = koinInject<MaterialsNavContext>()
        val viewModel = koinViewModel<VocabularySessionViewModel>(key = "session-${route.lessonId}") {
            parametersOf(VocabSessionSource.Lesson(route.lessonId))
        }
        VocabularySessionScreen(
            viewModel = viewModel,
            onDone = { context.backStack.removeLastOrNull() },
        )
    }

    navigation<MaterialsRoute.HandsFreeSession> { route ->
        val context = koinInject<MaterialsNavContext>()
        val viewModel = koinViewModel<VocabularySessionViewModel>(key = "handsfree-${route.lessonId}") {
            parametersOf(VocabSessionSource.Lesson(route.lessonId))
        }
        val speechService = rememberSpeechService()
        HandsFreeSessionScreen(
            viewModel = viewModel,
            speechService = speechService,
            onDone = {
                context.backStack[context.backStack.lastIndex] = MaterialsRoute.LessonDetail(route.lessonId, route.materialId)
            },
        )
    }

    navigation<MaterialsRoute.BrowseFlashcards> { route ->
        val context = koinInject<MaterialsNavContext>()
        val viewModel = koinViewModel<BrowseFlashcardsViewModel>(key = route.lessonId) {
            parametersOf(route.lessonId)
        }
        BrowseFlashcardsScreen(
            viewModel = viewModel,
            onDone = {
                context.backStack[context.backStack.lastIndex] = MaterialsRoute.LessonDetail(route.lessonId, route.materialId)
            },
        )
    }

    navigation<MaterialsRoute.CategorySession> { route ->
        val context = koinInject<MaterialsNavContext>()
        val viewModel = koinViewModel<VocabularySessionViewModel>(key = "category-${route.category}") {
            parametersOf(VocabSessionSource.Category(context.goalId, route.category))
        }
        VocabularySessionScreen(
            viewModel = viewModel,
            onDone = {
                context.backStack.clear()
                context.backStack.add(MaterialsRoute.List)
                context.onExitToHome()
            },
        )
    }

    navigation<MaterialsRoute.ModeSession> { route ->
        val context = koinInject<MaterialsNavContext>()
        val viewModel = koinViewModel<VocabularySessionViewModel>(key = "mode-${route.mode}") {
            parametersOf(VocabSessionSource.Mode(context.goalId, route.mode))
        }
        VocabularySessionScreen(
            viewModel = viewModel,
            onDone = {
                context.backStack.clear()
                context.backStack.add(MaterialsRoute.List)
                context.onExitToHome()
            },
        )
    }

    navigation<MaterialsRoute.VocabularyList> { route ->
        val context = koinInject<MaterialsNavContext>()
        val viewModel = koinViewModel<VocabularyListViewModel>(key = route.lessonId) {
            parametersOf(route.lessonId, context.goalId)
        }
        VocabularyListScreen(
            viewModel = viewModel,
            onBack = { context.backStack.removeLastOrNull() },
        )
    }
}
