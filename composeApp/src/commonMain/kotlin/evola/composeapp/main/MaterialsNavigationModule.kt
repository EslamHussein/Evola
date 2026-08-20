@file:OptIn(org.koin.core.annotation.KoinExperimentalAPI::class)

package evola.composeapp.main

import androidx.compose.runtime.remember
import evola.composeapp.core.navigation.MaterialsNavContext
import evola.composeapp.lessons.GrammarExerciseSessionScreen
import evola.composeapp.lessons.GrammarExerciseSessionViewModel
import evola.composeapp.lessons.GrammarTopicListScreen
import evola.composeapp.lessons.GrammarTopicListViewModel
import evola.composeapp.lessons.LessonDetailScreen
import evola.composeapp.lessons.LessonDetailViewModel
import evola.composeapp.materials.AddMaterialScreen
import evola.composeapp.materials.AddMaterialViewModel
import evola.composeapp.materials.MaterialDetailScreen
import evola.composeapp.materials.MaterialDetailViewModel
import evola.composeapp.materials.MaterialsListScreen
import evola.composeapp.materials.MaterialsListViewModel
import evola.composeapp.wizard.AiWizardScreen
import evola.composeapp.wizard.AiWizardViewModel
import evola.composeapp.wizard.ProcessingScreen
import evola.composeapp.wizard.ProcessingViewModel
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import org.koin.dsl.module
import org.koin.dsl.navigation3.navigation

/** Declares every Materials-tab [MaterialsRoute] once - the composable to render plus how its
 * ViewModel is resolved - instead of the `when (subScreen) { ... koinViewModel<X>() ... }` block
 * this replaces (see git history for that version). Back-stack navigation reads/writes
 * [MaterialsNavContext.backStack] directly (see that class's doc comment for why, instead of
 * koin-compose-navigation3's own back-stack injection) - every `onXyz` lambda below is just a
 * `backStack.add(...)` (push), `backStack.removeLastOrNull()` (pop), or a same-position replace,
 * chosen per-destination to reproduce the exact transition the old `onSubScreenChange(...)` call
 * produced. See [MaterialsRoute] for the full destination list. */
val materialsNavigationModule = module {
    single { MaterialsNavContext() }

    navigation<MaterialsRoute.List> {
        val context = koinInject<MaterialsNavContext>()
        val viewModel = koinViewModel<MaterialsListViewModel>()
        MaterialsListScreen(
            viewModel = viewModel,
            onAddMaterial = { context.backStack.add(MaterialsRoute.Add) },
            onOpenMaterial = { materialId -> context.backStack.add(MaterialsRoute.Detail(materialId)) },
        )
    }

    navigation<MaterialsRoute.Add> {
        val context = koinInject<MaterialsNavContext>()
        val viewModel = koinViewModel<AddMaterialViewModel>()
        AddMaterialScreen(
            viewModel = viewModel,
            onContinue = { staged ->
                context.setStagedResource(staged)
                context.backStack.add(MaterialsRoute.Wizard)
            },
            onCancel = { context.backStack.removeLastOrNull() },
        )
    }

    navigation<MaterialsRoute.Wizard> {
        val context = koinInject<MaterialsNavContext>()
        val staged = remember { context.takeStagedResource() }
        if (staged == null) {
            // Defensive only - AddMaterialScreen always sets this immediately before pushing this
            // route, so a null read here means the back stack was restored without the in-memory
            // context surviving (e.g. process death) rather than a reachable app state. Bounce back
            // to Add rather than crash on the non-null AiWizardViewModel constructor below.
            context.backStack.removeLastOrNull()
        } else {
            val viewModel = koinViewModel<AiWizardViewModel>(key = staged.toString()) {
                parametersOf(context.goalId, staged)
            }
            AiWizardScreen(
                viewModel = viewModel,
                onCancel = { context.backStack.removeLastOrNull() },
                onAnalysisStarted = { materialId ->
                    context.backStack[context.backStack.lastIndex] = MaterialsRoute.Processing(materialId)
                },
            )
        }
    }

    navigation<MaterialsRoute.Processing> { route ->
        val context = koinInject<MaterialsNavContext>()
        val viewModel = koinViewModel<ProcessingViewModel>(key = route.materialId) {
            parametersOf(route.materialId)
        }
        ProcessingScreen(
            viewModel = viewModel,
            materialId = route.materialId,
            onDone = { materialId ->
                context.backStack[context.backStack.lastIndex] = MaterialsRoute.Detail(materialId)
            },
        )
    }

    navigation<MaterialsRoute.Detail> { route ->
        val context = koinInject<MaterialsNavContext>()
        val viewModel = koinViewModel<MaterialDetailViewModel>(key = route.materialId) {
            parametersOf(route.materialId)
        }
        MaterialDetailScreen(
            viewModel = viewModel,
            onBack = { context.backStack.removeLastOrNull() },
            onOpenLesson = { lessonId ->
                context.backStack.add(MaterialsRoute.LessonDetail(lessonId, route.materialId))
            },
        )
    }

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
