package evola.composeapp.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.ui.graphics.Color
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import evola.composeapp.BackHandler
import evola.composeapp.language.LocalNativeLanguage
import evola.composeapp.theme.components.GlassNavigationBar
import evola.composeapp.lessons.GrammarExerciseSessionScreen
import evola.composeapp.lessons.GrammarExerciseSessionViewModel
import evola.composeapp.lessons.GrammarTopicListScreen
import evola.composeapp.lessons.GrammarTopicListViewModel
import evola.composeapp.lessons.LessonDetailScreen
import evola.composeapp.lessons.LessonDetailViewModel
import evola.composeapp.lessons.VocabularyListScreen
import evola.composeapp.lessons.VocabularyListViewModel
import evola.composeapp.lessons.VocabularySessionScreen
import evola.composeapp.lessons.VocabularySessionViewModel
import evola.composeapp.materials.AddMaterialScreen
import evola.composeapp.materials.AddMaterialViewModel
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
import evola.shared.goals.GoalsRepository
import evola.shared.grammar.GrammarRepository
import evola.shared.lessons.LessonsRepository
import evola.shared.materials.MaterialsRepository
import evola.shared.vocabulary.VocabularyRepository

private enum class MainTab { HOME, MATERIALS, PROFILE }

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
@Composable
fun MainScreen(
    initialGoal: Goal,
    goalsRepository: GoalsRepository,
    materialsRepository: MaterialsRepository,
    vocabularyRepository: VocabularyRepository,
    lessonsRepository: LessonsRepository,
    grammarRepository: GrammarRepository,
) {
    var goal by remember { mutableStateOf(initialGoal) }
    var selectedTab by remember { mutableStateOf(MainTab.HOME) }
    var materialsSubScreen by remember { mutableStateOf<MaterialsSubScreen>(MaterialsSubScreen.List) }

    val showTabBar = selectedTab != MainTab.MATERIALS || materialsSubScreen is MaterialsSubScreen.List

    val glassIndicatorColor = Color.White.copy(alpha = 0.16f)
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
                    NavigationBarItem(
                        selected = selectedTab == MainTab.HOME,
                        onClick = { selectedTab = MainTab.HOME },
                        icon = { Icon(Icons.Filled.Home, contentDescription = "Home") },
                        label = { Text("Home") },
                        colors = glassItemColors,
                    )
                    NavigationBarItem(
                        selected = selectedTab == MainTab.MATERIALS,
                        onClick = {
                            selectedTab = MainTab.MATERIALS
                            materialsSubScreen = MaterialsSubScreen.List
                        },
                        icon = { Icon(Icons.Filled.Folder, contentDescription = "Materials") },
                        label = { Text("Materials") },
                        colors = glassItemColors,
                    )
                    NavigationBarItem(
                        selected = selectedTab == MainTab.PROFILE,
                        onClick = { selectedTab = MainTab.PROFILE },
                        icon = { Icon(Icons.Filled.Person, contentDescription = "Profile") },
                        label = { Text("Profile") },
                        colors = glassItemColors,
                    )
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding).hazeSource(state = hazeState)) {
            when (selectedTab) {
                MainTab.HOME -> {
                    val homeViewModel = remember(goal.id) { HomeViewModel(goal.id, goalsRepository) }
                    HomeScreen(
                        goal = goal,
                        viewModel = homeViewModel,
                        onGoToMaterials = { selectedTab = MainTab.MATERIALS },
                        onContinueLesson = { lesson ->
                            selectedTab = MainTab.MATERIALS
                            materialsSubScreen = MaterialsSubScreen.LessonDetail(lesson.id, materialId = null)
                        },
                    )
                }

                MainTab.MATERIALS -> when (val sub = materialsSubScreen) {
                    MaterialsSubScreen.List -> {
                        val viewModel = remember(goal.id) { MaterialsListViewModel(goal.id, goalsRepository, materialsRepository) }
                        MaterialsListScreen(
                            viewModel = viewModel,
                            onAddMaterial = { materialsSubScreen = MaterialsSubScreen.Add },
                            onOpenMaterial = { materialId -> materialsSubScreen = MaterialsSubScreen.Detail(materialId) },
                            onContinueLesson = { lesson -> materialsSubScreen = MaterialsSubScreen.LessonDetail(lesson.id, materialId = null) },
                        )
                    }

                    MaterialsSubScreen.Add -> {
                        BackHandler(onBack = { materialsSubScreen = MaterialsSubScreen.List })
                        val viewModel = remember { AddMaterialViewModel() }
                        AddMaterialScreen(
                            viewModel = viewModel,
                            onContinue = { staged -> materialsSubScreen = MaterialsSubScreen.Wizard(staged) },
                            onCancel = { materialsSubScreen = MaterialsSubScreen.List },
                        )
                    }

                    is MaterialsSubScreen.Wizard -> {
                        BackHandler(onBack = { materialsSubScreen = MaterialsSubScreen.Add })
                        val viewModel = remember(sub.staged) {
                            AiWizardViewModel(goal.id, sub.staged, materialsRepository)
                        }
                        AiWizardScreen(
                            viewModel = viewModel,
                            onCancel = { materialsSubScreen = MaterialsSubScreen.Add },
                            onAnalysisStarted = { materialId -> materialsSubScreen = MaterialsSubScreen.Processing(materialId) },
                        )
                    }

                    is MaterialsSubScreen.Processing -> {
                        val viewModel = remember(sub.materialId) {
                            ProcessingViewModel(sub.materialId, materialsRepository)
                        }
                        ProcessingScreen(
                            viewModel = viewModel,
                            onDone = { materialId -> materialsSubScreen = MaterialsSubScreen.Detail(materialId) },
                        )
                    }

                    is MaterialsSubScreen.Detail -> {
                        BackHandler(onBack = { materialsSubScreen = MaterialsSubScreen.List })
                        val viewModel = remember(sub.materialId) {
                            MaterialDetailViewModel(sub.materialId, materialsRepository)
                        }
                        MaterialDetailScreen(
                            viewModel = viewModel,
                            onBack = { materialsSubScreen = MaterialsSubScreen.List },
                            onOpenLesson = { lessonId -> materialsSubScreen = MaterialsSubScreen.LessonDetail(lessonId, sub.materialId) },
                        )
                    }

                    is MaterialsSubScreen.LessonDetail -> {
                        BackHandler(onBack = { materialsSubScreen = sub.backTarget() })
                        val viewModel = remember(sub.lessonId) {
                            LessonDetailViewModel(sub.lessonId, lessonsRepository)
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
                        val viewModel = remember(sub.lessonId) {
                            VocabularySessionViewModel(sub.lessonId, vocabularyRepository)
                        }
                        VocabularySessionScreen(
                            viewModel = viewModel,
                            onDone = { materialsSubScreen = MaterialsSubScreen.LessonDetail(sub.lessonId, sub.materialId) },
                        )
                    }

                    is MaterialsSubScreen.VocabularyList -> {
                        BackHandler(onBack = { materialsSubScreen = MaterialsSubScreen.LessonDetail(sub.lessonId, sub.materialId) })
                        val viewModel = remember(sub.lessonId) {
                            VocabularyListViewModel(sub.lessonId, vocabularyRepository)
                        }
                        VocabularyListScreen(
                            viewModel = viewModel,
                            onBack = { materialsSubScreen = MaterialsSubScreen.LessonDetail(sub.lessonId, sub.materialId) },
                        )
                    }

                    is MaterialsSubScreen.GrammarTopics -> {
                        BackHandler(onBack = { materialsSubScreen = MaterialsSubScreen.LessonDetail(sub.lessonId, sub.materialId) })
                        val viewModel = remember(sub.lessonId) {
                            GrammarTopicListViewModel(sub.lessonId, grammarRepository)
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
                        val viewModel = remember(sub.topicId) {
                            GrammarExerciseSessionViewModel(sub.topicId, grammarRepository)
                        }
                        GrammarExerciseSessionScreen(
                            viewModel = viewModel,
                            onDone = { materialsSubScreen = MaterialsSubScreen.GrammarTopics(sub.lessonId, sub.materialId) },
                        )
                    }
                }

                MainTab.PROFILE -> {
                    val profileViewModel = remember { ProfileViewModel(goalsRepository) }
                    ProfileScreen(
                        goal = goal,
                        viewModel = profileViewModel,
                        onGoalUpdated = { updated -> goal = updated },
                    )
                }
            }
        }
    }
    }
}
