package evola.composeapp.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import evola.composeapp.BackHandler
import evola.composeapp.language.LocalNativeLanguage
import evola.composeapp.theme.EvolaColors
import evola.composeapp.theme.components.GlassNavigationBar
import evola.composeapp.lessons.GrammarExerciseSessionScreen
import evola.composeapp.lessons.GrammarExerciseSessionViewModel
import evola.composeapp.lessons.GrammarTopicListScreen
import evola.composeapp.lessons.GrammarTopicListViewModel
import evola.composeapp.lessons.LessonDetailScreen
import evola.composeapp.lessons.LessonDetailViewModel
import evola.composeapp.lessons.VocabularyListScreen
import evola.composeapp.lessons.VocabularyListViewModel
import evola.composeapp.lessons.VocabSessionSource
import evola.composeapp.lessons.VocabularySessionScreen
import evola.composeapp.lessons.VocabularySessionViewModel
import evola.composeapp.materials.AddMaterialScreen
import evola.composeapp.materials.AddMaterialViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import pro.respawn.flowmvi.compose.dsl.subscribe
import evola.composeapp.materials.MaterialDetailScreen
import evola.composeapp.materials.MaterialDetailViewModel
import evola.composeapp.materials.MaterialsListScreen
import evola.composeapp.materials.MaterialsListViewModel
import evola.composeapp.materials.StagedResource
import evola.composeapp.wizard.AiWizardScreen
import evola.composeapp.wizard.AiWizardViewModel
import evola.composeapp.wizard.ProcessingScreen
import evola.composeapp.wizard.ProcessingViewModel
import evola.shared.goals.Goal
import evola.shared.vocabulary.WordCategory
import evola.composeapp.generated.resources.Res
import evola.composeapp.generated.resources.main_nav_home
import evola.composeapp.generated.resources.main_nav_materials
import evola.composeapp.generated.resources.main_nav_profile
import org.jetbrains.compose.resources.stringResource

private enum class MainTab { HOME, MATERIALS, PROFILE }

/** Profile's own tiny sub-navigation, same shape as [MaterialsSubScreen] but with just the two
 * destinations Profile currently needs. */
private sealed interface ProfileSubScreen {
    data object Main : ProfileSubScreen
    data object Settings : ProfileSubScreen
}

/** [materialId] is null when this destination was reached from Home's "Continue lesson" CTA
 * rather than by drilling into a specific material - [LessonDetail] and everything below it fetch
 * purely by lessonId either way (see [evola.shared.lessons.LessonsRepository]), so the only thing
 * a null materialId changes is where the back button lands: [MaterialsSubScreen.List] instead of
 * a specific [MaterialsSubScreen.Detail]. */
private sealed interface MaterialsSubScreen {
    data object List : MaterialsSubScreen
    data object Add : MaterialsSubScreen
    data class Wizard(val staged: StagedResource) : MaterialsSubScreen
    data class Processing(val materialId: String) : MaterialsSubScreen
    data class Detail(val materialId: String) : MaterialsSubScreen
    data class LessonDetail(val lessonId: String, val materialId: String?) : MaterialsSubScreen
    data class Session(val lessonId: String, val materialId: String?) : MaterialsSubScreen

    /** Same word queue as [Session], rendered by [evola.composeapp.lessons.HandsFreeSessionScreen]
     * instead - Home's "Hands-free practice" row. */
    data class HandsFreeSession(val lessonId: String, val materialId: String?) : MaterialsSubScreen

    /** Reword's "Extra modes (do not affect stats)" - a plain, non-graded flip-through of every
     * word in the lesson, rendered by [evola.composeapp.lessons.BrowseFlashcardsScreen]. */
    data class BrowseFlashcards(val lessonId: String, val materialId: String?) : MaterialsSubScreen

    /** Started from Home's Needs practice/Learning/Mastered card - a one-off, cross-lesson
     * practice session, not tied to any single lesson (see [evola.composeapp.lessons.VocabSessionSource.Category]). */
    data class CategorySession(val category: WordCategory) : MaterialsSubScreen

    /** Reword's Home "Learn new words"/"Review words"/"Mixed mode" rows - goal-wide, same shape as
     * [CategorySession] (see [evola.composeapp.lessons.VocabSessionSource.Mode]). */
    data class ModeSession(val mode: evola.shared.vocabulary.SessionMode) : MaterialsSubScreen
    data class VocabularyList(val lessonId: String, val materialId: String?) : MaterialsSubScreen
    data class GrammarTopics(val lessonId: String, val materialId: String?) : MaterialsSubScreen
    data class GrammarSession(val lessonId: String, val topicId: String, val materialId: String?) : MaterialsSubScreen
}

private fun MaterialsSubScreen.LessonDetail.backTarget(): MaterialsSubScreen =
    materialId?.let { MaterialsSubScreen.Detail(it) } ?: MaterialsSubScreen.List

/**
 * The 3-tab navigation shell: Home / Materials / Profile. Materials doubles as the lesson browser
 * (Material Detail already lists a book's lessons with richer progress stats than a flat list
 * ever could) - the former standalone Study tab was a redundant second way to reach the same
 * [evola.composeapp.lessons.LessonDetailScreen], so it was folded in here; Home's "Continue
 * lesson" CTA and Materials' own continue card now both just jump straight into
 * [MaterialsSubScreen.LessonDetail]. The former standalone Goals tab was removed earlier - Home's
 * progress dashboard (readiness ring, streak, word breakdown) already covers everything it
 * showed, and this app supports exactly one goal per user so a dedicated goals list has nowhere
 * to grow. Modal flows within a tab (add material, material detail) hide the bar, matching the
 * spec's "modal/full-screen flows hide the tab bar" note.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    initialGoal: Goal,
) {
    var goal by remember { mutableStateOf(initialGoal) }
    var selectedTab by remember { mutableStateOf(MainTab.HOME) }
    var materialsSubScreen by remember { mutableStateOf<MaterialsSubScreen>(MaterialsSubScreen.List) }
    var profileSubScreen by remember { mutableStateOf<ProfileSubScreen>(ProfileSubScreen.Main) }

    val showTabBar = (selectedTab != MainTab.MATERIALS || materialsSubScreen is MaterialsSubScreen.List) &&
        (selectedTab != MainTab.PROFILE || profileSubScreen is ProfileSubScreen.Main)

    // A white-alpha indicator (right for the old dark glass bar) all but disappears against
    // Reword's light palette's white/near-white blurred surface - the accent at low alpha reads on
    // both.
    val glassIndicatorColor = EvolaColors.Accent.copy(alpha = 0.16f)
    val glassItemColors = NavigationBarItemDefaults.colors(indicatorColor = glassIndicatorColor)
    val hazeState = rememberHazeState()

    CompositionLocalProvider(LocalNativeLanguage provides goal.nativeLanguage) {
    Scaffold(
        // Both insets are deliberately excluded here: each tab screen owns its own top inset via
        // its own Scaffold+TopAppBar (WindowInsets.statusBars), and GlassNavigationBar below owns
        // its own bottom inset via navigationBarsPadding() so it floats above the gesture area.
        // Leaving Scaffold's default contentWindowInsets (safeDrawing, both sides) doubled the gap
        // on both edges.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showTabBar) {
                GlassNavigationBar(hazeState = hazeState) {
                    val homeLabel = stringResource(Res.string.main_nav_home)
                    val materialsLabel = stringResource(Res.string.main_nav_materials)
                    val profileLabel = stringResource(Res.string.main_nav_profile)
                    NavigationBarItem(
                        selected = selectedTab == MainTab.HOME,
                        onClick = { selectedTab = MainTab.HOME },
                        icon = { Icon(Icons.Filled.Home, contentDescription = homeLabel) },
                        label = { Text(homeLabel) },
                        colors = glassItemColors,
                    )
                    NavigationBarItem(
                        selected = selectedTab == MainTab.MATERIALS,
                        onClick = {
                            selectedTab = MainTab.MATERIALS
                            materialsSubScreen = MaterialsSubScreen.List
                        },
                        icon = { Icon(Icons.Filled.Folder, contentDescription = materialsLabel) },
                        label = { Text(materialsLabel) },
                        colors = glassItemColors,
                    )
                    NavigationBarItem(
                        selected = selectedTab == MainTab.PROFILE,
                        onClick = {
                            selectedTab = MainTab.PROFILE
                            profileSubScreen = ProfileSubScreen.Main
                        },
                        icon = { Icon(Icons.Filled.Person, contentDescription = profileLabel) },
                        label = { Text(profileLabel) },
                        colors = glassItemColors,
                    )
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding).hazeSource(state = hazeState)) {
            when (selectedTab) {
                MainTab.HOME -> {
                    val homeViewModel = koinViewModel<HomeViewModel>(key = goal.id) { parametersOf(goal.id) }
                    HomeScreen(
                        goal = goal,
                        viewModel = homeViewModel,
                        onGoToMaterials = { selectedTab = MainTab.MATERIALS },
                        onContinueLesson = { lesson ->
                            selectedTab = MainTab.MATERIALS
                            materialsSubScreen = MaterialsSubScreen.LessonDetail(lesson.id, materialId = null)
                        },
                        onStartCategorySession = { category ->
                            selectedTab = MainTab.MATERIALS
                            materialsSubScreen = MaterialsSubScreen.CategorySession(category)
                        },
                        onStartModeSession = { mode ->
                            selectedTab = MainTab.MATERIALS
                            materialsSubScreen = MaterialsSubScreen.ModeSession(mode)
                        },
                        onStartHandsFree = { lesson ->
                            selectedTab = MainTab.MATERIALS
                            materialsSubScreen = MaterialsSubScreen.HandsFreeSession(lesson.id, materialId = null)
                        },
                        onBrowseFlashcards = { lesson ->
                            selectedTab = MainTab.MATERIALS
                            materialsSubScreen = MaterialsSubScreen.BrowseFlashcards(lesson.id, materialId = null)
                        },
                    )
                }

                MainTab.MATERIALS -> when (val sub = materialsSubScreen) {
                    MaterialsSubScreen.List -> {
                        val viewModel = koinViewModel<MaterialsListViewModel>()
                        MaterialsListScreen(
                            viewModel = viewModel,
                            onAddMaterial = { materialsSubScreen = MaterialsSubScreen.Add },
                            onOpenMaterial = { materialId -> materialsSubScreen = MaterialsSubScreen.Detail(materialId) },
                        )
                    }

                    MaterialsSubScreen.Add -> {
                        BackHandler(onBack = { materialsSubScreen = MaterialsSubScreen.List })
                        val viewModel = koinViewModel<AddMaterialViewModel>()
                        AddMaterialScreen(
                            viewModel = viewModel,
                            onContinue = { staged -> materialsSubScreen = MaterialsSubScreen.Wizard(staged) },
                            onCancel = { materialsSubScreen = MaterialsSubScreen.List },
                        )
                    }

                    is MaterialsSubScreen.Wizard -> {
                        BackHandler(onBack = { materialsSubScreen = MaterialsSubScreen.Add })
                        val viewModel = koinViewModel<AiWizardViewModel>(key = sub.staged.toString()) {
                            parametersOf(goal.id, sub.staged)
                        }
                        AiWizardScreen(
                            viewModel = viewModel,
                            onCancel = { materialsSubScreen = MaterialsSubScreen.Add },
                            onAnalysisStarted = { materialId -> materialsSubScreen = MaterialsSubScreen.Processing(materialId) },
                        )
                    }

                    is MaterialsSubScreen.Processing -> {
                        val viewModel = koinViewModel<ProcessingViewModel>(key = sub.materialId) {
                            parametersOf(sub.materialId)
                        }
                        ProcessingScreen(
                            viewModel = viewModel,
                            materialId = sub.materialId,
                            onDone = { materialId -> materialsSubScreen = MaterialsSubScreen.Detail(materialId) },
                        )
                    }

                    is MaterialsSubScreen.Detail -> {
                        BackHandler(onBack = { materialsSubScreen = MaterialsSubScreen.List })
                        val viewModel = koinViewModel<MaterialDetailViewModel>(key = sub.materialId) {
                            parametersOf(sub.materialId)
                        }
                        MaterialDetailScreen(
                            viewModel = viewModel,
                            onBack = { materialsSubScreen = MaterialsSubScreen.List },
                            onOpenLesson = { lessonId -> materialsSubScreen = MaterialsSubScreen.LessonDetail(lessonId, sub.materialId) },
                        )
                    }

                    is MaterialsSubScreen.LessonDetail -> {
                        BackHandler(onBack = { materialsSubScreen = sub.backTarget() })
                        val viewModel = koinViewModel<LessonDetailViewModel>(key = sub.lessonId) {
                            parametersOf(sub.lessonId)
                        }
                        LessonDetailScreen(
                            viewModel = viewModel,
                            onBack = { materialsSubScreen = sub.backTarget() },
                            onOpenSection = { key ->
                                when (key) {
                                    "vocabulary" -> materialsSubScreen = MaterialsSubScreen.Session(sub.lessonId, sub.materialId)
                                    "grammar" -> materialsSubScreen = MaterialsSubScreen.GrammarTopics(sub.lessonId, sub.materialId)
                                }
                            },
                            onViewVocabularyList = { materialsSubScreen = MaterialsSubScreen.VocabularyList(sub.lessonId, sub.materialId) },
                        )
                    }

                    is MaterialsSubScreen.Session -> {
                        val viewModel = koinViewModel<VocabularySessionViewModel>(key = "session-${sub.lessonId}") {
                            parametersOf(VocabSessionSource.Lesson(sub.lessonId))
                        }
                        VocabularySessionScreen(
                            viewModel = viewModel,
                            onDone = { materialsSubScreen = MaterialsSubScreen.LessonDetail(sub.lessonId, sub.materialId) },
                        )
                    }

                    is MaterialsSubScreen.HandsFreeSession -> {
                        val viewModel = koinViewModel<VocabularySessionViewModel>(key = "handsfree-${sub.lessonId}") {
                            parametersOf(VocabSessionSource.Lesson(sub.lessonId))
                        }
                        val speechService = evola.composeapp.speech.rememberSpeechService()
                        evola.composeapp.lessons.HandsFreeSessionScreen(
                            viewModel = viewModel,
                            speechService = speechService,
                            onDone = { materialsSubScreen = MaterialsSubScreen.LessonDetail(sub.lessonId, sub.materialId) },
                        )
                    }

                    is MaterialsSubScreen.BrowseFlashcards -> {
                        val viewModel = koinViewModel<evola.composeapp.lessons.BrowseFlashcardsViewModel>(key = sub.lessonId) {
                            parametersOf(sub.lessonId)
                        }
                        evola.composeapp.lessons.BrowseFlashcardsScreen(
                            viewModel = viewModel,
                            onDone = { materialsSubScreen = MaterialsSubScreen.LessonDetail(sub.lessonId, sub.materialId) },
                        )
                    }

                    is MaterialsSubScreen.CategorySession -> {
                        val viewModel = koinViewModel<VocabularySessionViewModel>(key = "category-${sub.category}") {
                            parametersOf(VocabSessionSource.Category(goal.id, sub.category))
                        }
                        VocabularySessionScreen(
                            viewModel = viewModel,
                            onDone = {
                                selectedTab = MainTab.HOME
                                materialsSubScreen = MaterialsSubScreen.List
                            },
                        )
                    }

                    is MaterialsSubScreen.ModeSession -> {
                        val viewModel = koinViewModel<VocabularySessionViewModel>(key = "mode-${sub.mode}") {
                            parametersOf(VocabSessionSource.Mode(goal.id, sub.mode))
                        }
                        VocabularySessionScreen(
                            viewModel = viewModel,
                            onDone = {
                                selectedTab = MainTab.HOME
                                materialsSubScreen = MaterialsSubScreen.List
                            },
                        )
                    }

                    is MaterialsSubScreen.VocabularyList -> {
                        BackHandler(onBack = { materialsSubScreen = MaterialsSubScreen.LessonDetail(sub.lessonId, sub.materialId) })
                        val viewModel = koinViewModel<VocabularyListViewModel>(key = sub.lessonId) {
                            parametersOf(sub.lessonId, goal.id)
                        }
                        VocabularyListScreen(
                            viewModel = viewModel,
                            onBack = { materialsSubScreen = MaterialsSubScreen.LessonDetail(sub.lessonId, sub.materialId) },
                        )
                    }

                    is MaterialsSubScreen.GrammarTopics -> {
                        BackHandler(onBack = { materialsSubScreen = MaterialsSubScreen.LessonDetail(sub.lessonId, sub.materialId) })
                        val viewModel = koinViewModel<GrammarTopicListViewModel>(key = sub.lessonId) {
                            parametersOf(sub.lessonId)
                        }
                        GrammarTopicListScreen(
                            viewModel = viewModel,
                            onOpenTopic = { topicId ->
                                materialsSubScreen = MaterialsSubScreen.GrammarSession(sub.lessonId, topicId, sub.materialId)
                            },
                            onBack = { materialsSubScreen = MaterialsSubScreen.LessonDetail(sub.lessonId, sub.materialId) },
                        )
                    }

                    is MaterialsSubScreen.GrammarSession -> {
                        val viewModel = koinViewModel<GrammarExerciseSessionViewModel>(key = sub.topicId) {
                            parametersOf(sub.topicId)
                        }
                        GrammarExerciseSessionScreen(
                            viewModel = viewModel,
                            onDone = { materialsSubScreen = MaterialsSubScreen.GrammarTopics(sub.lessonId, sub.materialId) },
                        )
                    }
                }

                MainTab.PROFILE -> when (profileSubScreen) {
                    ProfileSubScreen.Main -> {
                        val profileViewModel = koinViewModel<ProfileViewModel>(key = goal.id) { parametersOf(goal.id) }
                        ProfileScreen(
                            goal = goal,
                            viewModel = profileViewModel,
                            onGoalUpdated = { updated -> goal = updated },
                            onOpenSettings = { profileSubScreen = ProfileSubScreen.Settings },
                        )
                    }

                    ProfileSubScreen.Settings -> {
                        val settingsViewModel = koinViewModel<SettingsViewModel>()
                        val reminderScheduler = evola.composeapp.reminders.rememberReminderScheduler()
                        val currentSettingsState by settingsViewModel.subscribe()
                        val requestNotificationPermission = evola.composeapp.reminders.rememberNotificationPermissionRequester { granted ->
                            if (granted) {
                                reminderScheduler.scheduleDaily(currentSettingsState.settings.reminderHour)
                            } else {
                                // Permission denied - the toggle stays visually on (matching the OS's own
                                // "you can flip this in system settings later" convention) but nothing is
                                // actually scheduled until the user grants it from system settings.
                                settingsViewModel.intent(SettingsIntent.SetNotificationsEnabled(false))
                            }
                        }
                        val speechService = evola.composeapp.speech.rememberSpeechService()
                        SettingsScreen(
                            viewModel = settingsViewModel,
                            speechService = speechService,
                            onBack = { profileSubScreen = ProfileSubScreen.Main },
                            onNotificationsToggled = { enabled ->
                                if (enabled) requestNotificationPermission() else reminderScheduler.cancel()
                            },
                        )
                    }
                }
            }
        }
    }
    }
}
