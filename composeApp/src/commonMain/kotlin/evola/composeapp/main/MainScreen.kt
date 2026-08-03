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
import evola.composeapp.materials.AddMaterialScreen
import evola.composeapp.materials.AddMaterialViewModel
import evola.composeapp.materials.MaterialDetailScreen
import evola.composeapp.materials.MaterialDetailViewModel
import evola.composeapp.materials.MaterialsListScreen
import evola.composeapp.materials.MaterialsListViewModel
import evola.shared.auth.AuthUser
import evola.shared.goals.Goal
import evola.shared.goals.GoalsRepository
import evola.shared.materials.MaterialsRepository

private enum class MainTab { HOME, GOALS, STUDY, MATERIALS, PROFILE }

private sealed interface MaterialsSubScreen {
    data object List : MaterialsSubScreen
    data object Add : MaterialsSubScreen
    data class Detail(val materialId: String) : MaterialsSubScreen
}

/**
 * The 5-tab navigation shell per 06_SCREENS_REFERENCE.md: Home / Goals / Study / Materials /
 * Profile. Modal flows within a tab (add material, material detail) hide the bar, matching the
 * spec's "modal/full-screen flows hide the tab bar" note.
 */
@Composable
fun MainScreen(
    user: AuthUser,
    initialGoal: Goal,
    goalsRepository: GoalsRepository,
    materialsRepository: MaterialsRepository,
    accessToken: String,
    onLogout: () -> Unit,
) {
    var goal by remember { mutableStateOf(initialGoal) }
    var selectedTab by remember { mutableStateOf(MainTab.HOME) }
    var materialsSubScreen by remember { mutableStateOf<MaterialsSubScreen>(MaterialsSubScreen.List) }

    val showTabBar = selectedTab != MainTab.MATERIALS || materialsSubScreen is MaterialsSubScreen.List

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
                        onClick = { selectedTab = MainTab.STUDY },
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
                MainTab.HOME -> HomeScreen(goal = goal, onGoToMaterials = { selectedTab = MainTab.MATERIALS })

                MainTab.GOALS -> GoalsScreen(goal = goal)

                MainTab.STUDY -> {
                    val viewModel = remember(goal.id) {
                        LessonSelectionViewModel(accessToken, goal.id, goalsRepository)
                    }
                    // No lesson is ever "ready" until M6/M7 populate real vocab/grammar content,
                    // so there's nowhere to route to yet - see StudyScreen's own doc comment.
                    StudyScreen(viewModel = viewModel, onOpenLesson = {})
                }

                MainTab.MATERIALS -> when (val sub = materialsSubScreen) {
                    MaterialsSubScreen.List -> {
                        val viewModel = remember { MaterialsListViewModel(accessToken, materialsRepository) }
                        MaterialsListScreen(
                            viewModel = viewModel,
                            onAddMaterial = { materialsSubScreen = MaterialsSubScreen.Add },
                            onOpenMaterial = { materialId -> materialsSubScreen = MaterialsSubScreen.Detail(materialId) },
                        )
                    }

                    MaterialsSubScreen.Add -> {
                        val viewModel = remember { AddMaterialViewModel(accessToken, goal.id, materialsRepository) }
                        AddMaterialScreen(
                            viewModel = viewModel,
                            onUploaded = { materialId -> materialsSubScreen = MaterialsSubScreen.Detail(materialId) },
                            onCancel = { materialsSubScreen = MaterialsSubScreen.List },
                        )
                    }

                    is MaterialsSubScreen.Detail -> {
                        val viewModel = remember(sub.materialId) {
                            MaterialDetailViewModel(accessToken, sub.materialId, materialsRepository)
                        }
                        MaterialDetailScreen(viewModel = viewModel, onBack = { materialsSubScreen = MaterialsSubScreen.List })
                    }
                }

                MainTab.PROFILE -> {
                    val profileViewModel = remember { ProfileViewModel(goalsRepository, accessToken) }
                    ProfileScreen(
                        user = user,
                        goal = goal,
                        viewModel = profileViewModel,
                        onGoalUpdated = { updated -> goal = updated },
                        onLogout = onLogout,
                    )
                }
            }
        }
    }
}
