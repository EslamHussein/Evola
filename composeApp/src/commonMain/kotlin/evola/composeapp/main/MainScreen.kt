@file:OptIn(org.koin.core.annotation.KoinExperimentalAPI::class)

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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation3.ui.NavDisplay
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import evola.composeapp.core.navigation.MaterialsNavContext
import evola.composeapp.core.navigation.ProfileNavContext
import evola.composeapp.core.common.LocalNativeLanguage
import evola.composeapp.core.designsystem.EvolaColors
import evola.composeapp.core.designsystem.components.GlassNavigationBar
import org.koin.compose.koinInject
import org.koin.compose.navigation3.koinEntryProvider
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import evola.shared.feature.onboarding.domain.Goal
import evola.composeapp.generated.resources.Res
import evola.composeapp.generated.resources.main_nav_home
import evola.composeapp.generated.resources.main_nav_materials
import evola.composeapp.generated.resources.main_nav_profile
import org.jetbrains.compose.resources.stringResource

private enum class MainTab { HOME, MATERIALS, PROFILE }

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
                            materialsBackStack.clear()
                            materialsBackStack.add(MaterialsRoute.List)
                        },
                        icon = { Icon(Icons.Filled.Folder, contentDescription = materialsLabel) },
                        label = { Text(materialsLabel) },
                        colors = glassItemColors,
                    )
                    NavigationBarItem(
                        selected = selectedTab == MainTab.PROFILE,
                        onClick = {
                            selectedTab = MainTab.PROFILE
                            profileBackStack.clear()
                            profileBackStack.add(ProfileRoute.Main)
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

/** Materials tab's own sub-navigation - a real Navigation 3 back stack (see [MaterialsRoute] and
 * [materialsNavigationModule]) instead of the hand-rolled `when`-on-a-sealed-interface state
 * machine this replaced (see git history for that version). [backStack] is hoisted in [MainScreen],
 * not created here, since [MainScreen] also needs its size to decide whether the tab bar is
 * visible, and Home's cross-tab CTAs need to reset it before switching tabs in.
 *
 * No manual [evola.composeapp.core.navigation.BackHandler] here (unlike the pre-migration version) - [NavDisplay]
 * already registers its own back handling via `androidx.navigationevent`, predictive-back animation
 * included, and only intercepts the system back gesture when there's a previous entry to return to.
 * A redundant `BackHandler` on top would use the older `OnBackPressedCallback` API and risks
 * pre-empting Nav3's own predictive-back gesture instead of just duplicating `removeLastOrNull()`. */
@Composable
private fun MaterialsTabHost(backStack: MutableList<MaterialsRoute>) {
    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider = koinEntryProvider(),
    )
}

/** Profile tab's own sub-navigation - same extraction rationale as [MaterialsTabHost]. */
@Composable
private fun ProfileTabHost(backStack: MutableList<ProfileRoute>) {
    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider = koinEntryProvider(),
    )
}
