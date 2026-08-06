package evola.composeapp.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import evola.composeapp.BackHandler
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
import evola.shared.goals.Lesson
import evola.shared.grammar.GrammarRepository
import evola.shared.lessons.LessonsRepository
import evola.shared.materials.MaterialsRepository
import evola.shared.vocabulary.VocabularyRepository

private enum class MainTab { HOME, GOALS, STUDY, MATERIALS, PROFILE }

private sealed interface MaterialsSubScreen {
    data object List : MaterialsSubScreen
    data object Add : MaterialsSubScreen
    data class Wizard(val staged: StagedResource) : MaterialsSubScreen
    data class Processing(val materialId: String) : MaterialsSubScreen
    data class Detail(val materialId: String) : MaterialsSubScreen
    data class LessonDetail(val lessonId: String, val materialId: String) : MaterialsSubScreen
    data class Session(val lessonId: String, val materialId: String) : MaterialsSubScreen
    data class VocabularyList(val lessonId: String, val materialId: String) : MaterialsSubScreen
    data class GrammarTopics(val lessonId: String, val materialId: String) : MaterialsSubScreen
    data class GrammarSession(val lessonId: String, val topicId: String, val materialId: String) : MaterialsSubScreen
}

private sealed interface StudySubScreen {
    data object List : StudySubScreen
    data class Home(val lesson: Lesson) : StudySubScreen
    data class Session(val lesson: Lesson) : StudySubScreen
    data class VocabularyList(val lesson: Lesson) : StudySubScreen
    data class GrammarTopics(val lesson: Lesson) : StudySubScreen
    data class GrammarSession(val lesson: Lesson, val topicId: String) : StudySubScreen
}

/**
 * The 5-tab navigation shell per 06_SCREENS_REFERENCE.md: Home / Goals / Study / Materials /
 * Profile. Modal flows within a tab (add material, material detail) hide the bar, matching the
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
    var studySubScreen by remember { mutableStateOf<StudySubScreen>(StudySubScreen.List) }

    val showTabBar = (selectedTab != MainTab.MATERIALS || materialsSubScreen is MaterialsSubScreen.List) &&
        (selectedTab != MainTab.STUDY || studySubScreen is StudySubScreen.List)

    Scaffold(
        bottomBar = {
            if (showTabBar) {
                NavigationBar {
                    NavigationBarItem(
                        selected = selectedTab == MainTab.HOME,
                        onClick = { selectedTab = MainTab.HOME },
                        icon = { Icon(Icons.Filled.Home, contentDescription = "Home") },
                        label = { Text("Home") },
                    )
                    NavigationBarItem(
                        selected = selectedTab == MainTab.GOALS,
                        onClick = { selectedTab = MainTab.GOALS },
                        icon = { Icon(Icons.Filled.Flag, contentDescription = "Goals") },
                        label = { Text("Goals") },
                    )
                    NavigationBarItem(
                        selected = selectedTab == MainTab.STUDY,
                        onClick = {
                            selectedTab = MainTab.STUDY
                            studySubScreen = StudySubScreen.List
                        },
                        icon = { Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = "Study") },
                        label = { Text("Study") },
                    )
                    NavigationBarItem(
                        selected = selectedTab == MainTab.MATERIALS,
                        onClick = {
                            selectedTab = MainTab.MATERIALS
                            materialsSubScreen = MaterialsSubScreen.List
                        },
                        icon = { Icon(Icons.Filled.Folder, contentDescription = "Materials") },
                        label = { Text("Materials") },
                    )
                    NavigationBarItem(
                        selected = selectedTab == MainTab.PROFILE,
                        onClick = { selectedTab = MainTab.PROFILE },
                        icon = { Icon(Icons.Filled.Person, contentDescription = "Profile") },
                        label = { Text("Profile") },
                    )
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (selectedTab) {
                MainTab.HOME -> {
                    val homeViewModel = remember(goal.id) { HomeViewModel(goal.id, goalsRepository) }
                    HomeScreen(
                        goal = goal,
                        viewModel = homeViewModel,
                        onGoToMaterials = { selectedTab = MainTab.MATERIALS },
                        onContinueLesson = { lesson ->
                            selectedTab = MainTab.STUDY
                            studySubScreen = StudySubScreen.Home(lesson)
                        },
                        onProfile = { selectedTab = MainTab.PROFILE },
                    )
                }

                MainTab.GOALS -> GoalsScreen(goal = goal)

                MainTab.STUDY -> when (val sub = studySubScreen) {
                    StudySubScreen.List -> {
                        val viewModel = remember(goal.id) {
                            LessonSelectionViewModel(goal.id, goalsRepository)
                        }
                        StudyScreen(viewModel = viewModel, onOpenLesson = { lesson -> studySubScreen = StudySubScreen.Home(lesson) })
                    }

                    is StudySubScreen.Home -> {
                        BackHandler(onBack = { studySubScreen = StudySubScreen.List })
                        val viewModel = remember(sub.lesson.id) {
                            LessonDetailViewModel(sub.lesson.id, lessonsRepository)
                        }
                        LessonDetailScreen(
                            viewModel = viewModel,
                            onBack = { studySubScreen = StudySubScreen.List },
                            onOpenSection = { key ->
                                when (key) {
                                    "vocabulary" -> studySubScreen = StudySubScreen.Session(sub.lesson)
                                    "grammar" -> studySubScreen = StudySubScreen.GrammarTopics(sub.lesson)
                                }
                            },
                            onViewVocabularyList = { studySubScreen = StudySubScreen.VocabularyList(sub.lesson) },
                        )
                    }

                    is StudySubScreen.Session -> {
                        val viewModel = remember(sub.lesson.id) {
                            VocabularySessionViewModel(sub.lesson.id, vocabularyRepository)
                        }
                        VocabularySessionScreen(viewModel = viewModel, onDone = { studySubScreen = StudySubScreen.Home(sub.lesson) })
                    }

                    is StudySubScreen.VocabularyList -> {
                        BackHandler(onBack = { studySubScreen = StudySubScreen.Home(sub.lesson) })
                        val viewModel = remember(sub.lesson.id) {
                            VocabularyListViewModel(sub.lesson.id, vocabularyRepository)
                        }
                        VocabularyListScreen(viewModel = viewModel, onBack = { studySubScreen = StudySubScreen.Home(sub.lesson) })
                    }

                    is StudySubScreen.GrammarTopics -> {
                        BackHandler(onBack = { studySubScreen = StudySubScreen.Home(sub.lesson) })
                        val viewModel = remember(sub.lesson.id) {
                            GrammarTopicListViewModel(sub.lesson.id, grammarRepository)
                        }
                        GrammarTopicListScreen(
                            viewModel = viewModel,
                            onOpenTopic = { topicId -> studySubScreen = StudySubScreen.GrammarSession(sub.lesson, topicId) },
                            onBack = { studySubScreen = StudySubScreen.Home(sub.lesson) },
                        )
                    }

                    is StudySubScreen.GrammarSession -> {
                        val viewModel = remember(sub.topicId) {
                            GrammarExerciseSessionViewModel(sub.topicId, grammarRepository)
                        }
                        GrammarExerciseSessionScreen(
                            viewModel = viewModel,
                            onDone = { studySubScreen = StudySubScreen.GrammarTopics(sub.lesson) },
                        )
                    }
                }

                MainTab.MATERIALS -> when (val sub = materialsSubScreen) {
                    MaterialsSubScreen.List -> {
                        val viewModel = remember { MaterialsListViewModel(materialsRepository) }
                        MaterialsListScreen(
                            viewModel = viewModel,
                            onAddMaterial = { materialsSubScreen = MaterialsSubScreen.Add },
                            onOpenMaterial = { materialId -> materialsSubScreen = MaterialsSubScreen.Detail(materialId) },
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
                        BackHandler(onBack = { materialsSubScreen = MaterialsSubScreen.Detail(sub.materialId) })
                        val viewModel = remember(sub.lessonId) {
                            LessonDetailViewModel(sub.lessonId, lessonsRepository)
                        }
                        LessonDetailScreen(
                            viewModel = viewModel,
                            onBack = { materialsSubScreen = MaterialsSubScreen.Detail(sub.materialId) },
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
