@file:OptIn(org.koin.core.annotation.KoinExperimentalAPI::class)

package evola.composeapp.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import evola.composeapp.core.navigation.MaterialsNavContext
import evola.composeapp.core.navigation.ProfileNavContext
import evola.composeapp.core.common.LocalNativeLanguage
import evola.composeapp.feature.home.ui.HomeScreen
import evola.composeapp.feature.home.vm.HomeViewModel
import evola.composeapp.feature.profile.ui.ProfileRoute
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import evola.shared.feature.onboarding.domain.Goal

internal enum class MainTab { HOME, MATERIALS, PROFILE }

/**
 * The 3-tab navigation shell: Home / Materials / Profile. Materials doubles as the lesson browser
 * (Material Detail already lists a book's lessons with richer progress stats than a flat list
 * ever could) - the former standalone Study tab was a redundant second way to reach the same
 * [evola.composeapp.feature.learning.ui.LessonDetailScreen], so it was folded in here; Home's "Continue
 * lesson" CTA and Materials' own continue card now both just jump straight into
 * [MaterialsRoute.LessonDetail]. The former standalone Goals tab was removed earlier - Home's
 * progress dashboard (readiness ring, streak, word breakdown) already covers everything it
 * showed, and this app supports exactly one goal per user so a dedicated goals list has nowhere
 * to grow. Modal flows within a tab (add material, material detail) hide the bar, matching the
 * spec's "modal/full-screen flows hide the tab bar" note.
 *
 * Split across sibling files in this package: [MainTabBar] (the bottom bar, previewable on its
 * own), [MaterialsTabHost]/[ProfileTabHost] (each tab's own Navigation 3 host), and
 * [MainScreenPreview] (a Koin-backed preview of this composable itself) - see MainTabBar.kt,
 * MainScreenTabHosts.kt, and MainScreenPreview.kt.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    initialGoal: Goal,
) {
    var goal by remember { mutableStateOf(initialGoal) }
    var selectedTab by remember { mutableStateOf(MainTab.HOME) }
    val materialsBackStack = remember { mutableStateListOf<MaterialsRoute>(MaterialsRoute.List) }
    val profileBackStack = remember { mutableStateListOf<ProfileRoute>(ProfileRoute.Main) }

    // MaterialsNavContext/ProfileNavContext (see their doc comments) are the bridge between
    // MainScreen's own state and the Koin-declared route entries - kept in sync here (plain field
    // writes, not a LaunchedEffect, so a route reading them on its very first composition never sees
    // a stale value) rather than recreated per-composition, so Home's cross-tab CTAs below and every
    // route in materialsNavigationModule/profileNavigationModule see the same instances.
    val materialsNavContext = koinInject<MaterialsNavContext>()
    materialsNavContext.backStack = materialsBackStack
    materialsNavContext.goalId = goal.id
    materialsNavContext.onExitToHome = {
        selectedTab = MainTab.HOME
        materialsBackStack.clear()
        materialsBackStack.add(MaterialsRoute.List)
    }
    val profileNavContext = koinInject<ProfileNavContext>()
    profileNavContext.backStack = profileBackStack
    profileNavContext.goal = goal
    profileNavContext.onGoalUpdated = { updated -> goal = updated }

    val showTabBar = (selectedTab != MainTab.MATERIALS || materialsBackStack.size == 1) &&
        (selectedTab != MainTab.PROFILE || profileBackStack.size == 1)

    CompositionLocalProvider(LocalNativeLanguage provides goal.nativeLanguage) {
    Scaffold(
        // Both insets are deliberately excluded here: each tab screen owns its own top inset via
        // its own Scaffold+TopAppBar (WindowInsets.statusBars), and MainTabBar below owns its own
        // bottom inset. Leaving Scaffold's default contentWindowInsets (safeDrawing, both sides)
        // doubled the gap on both edges.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showTabBar) {
                MainTabBar(
                    selectedTab = selectedTab,
                    onSelectTab = { tab ->
                        selectedTab = tab
                        when (tab) {
                            MainTab.HOME -> Unit
                            MainTab.MATERIALS -> {
                                materialsBackStack.clear()
                                materialsBackStack.add(MaterialsRoute.List)
                            }
                            MainTab.PROFILE -> {
                                profileBackStack.clear()
                                profileBackStack.add(ProfileRoute.Main)
                            }
                        }
                    },
                )
            }
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (selectedTab) {
                MainTab.HOME -> {
                    val homeViewModel = koinViewModel<HomeViewModel>(key = goal.id) { parametersOf(goal.id) }
                    HomeScreen(
                        goal = goal,
                        viewModel = homeViewModel,
                        onGoToMaterials = { selectedTab = MainTab.MATERIALS },
                        onContinueLesson = { lesson ->
                            selectedTab = MainTab.MATERIALS
                            materialsBackStack.clear()
                            materialsBackStack.add(MaterialsRoute.List)
                            materialsBackStack.add(MaterialsRoute.LessonDetail(lesson.id, materialId = null))
                        },
                        onStartCategorySession = { category ->
                            selectedTab = MainTab.MATERIALS
                            materialsBackStack.clear()
                            materialsBackStack.add(MaterialsRoute.List)
                            materialsBackStack.add(MaterialsRoute.CategorySession(category))
                        },
                        onStartModeSession = { mode ->
                            selectedTab = MainTab.MATERIALS
                            materialsBackStack.clear()
                            materialsBackStack.add(MaterialsRoute.List)
                            materialsBackStack.add(MaterialsRoute.ModeSession(mode))
                        },
                        onStartHandsFree = { lesson ->
                            selectedTab = MainTab.MATERIALS
                            materialsBackStack.clear()
                            materialsBackStack.add(MaterialsRoute.List)
                            materialsBackStack.add(MaterialsRoute.HandsFreeSession(lesson.id, materialId = null))
                        },
                        onBrowseFlashcards = { lesson ->
                            selectedTab = MainTab.MATERIALS
                            materialsBackStack.clear()
                            materialsBackStack.add(MaterialsRoute.List)
                            materialsBackStack.add(MaterialsRoute.BrowseFlashcards(lesson.id, materialId = null))
                        },
                    )
                }

                MainTab.MATERIALS -> MaterialsTabHost(backStack = materialsBackStack)

                MainTab.PROFILE -> ProfileTabHost(backStack = profileBackStack)
            }
        }
    }
    }
}
