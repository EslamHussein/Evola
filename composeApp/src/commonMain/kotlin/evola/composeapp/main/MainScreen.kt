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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation3.ui.NavDisplay
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import evola.composeapp.BackHandler
import evola.composeapp.language.LocalNativeLanguage
import evola.composeapp.theme.EvolaColors
import evola.composeapp.theme.components.GlassNavigationBar
import org.koin.compose.koinInject
import org.koin.compose.navigation3.koinEntryProvider
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import org.orbitmvi.orbit.compose.collectAsState
import evola.shared.goals.Goal
import evola.composeapp.generated.resources.Res
import evola.composeapp.generated.resources.main_nav_home
import evola.composeapp.generated.resources.main_nav_materials
import evola.composeapp.generated.resources.main_nav_profile
import org.jetbrains.compose.resources.stringResource

private enum class MainTab { HOME, MATERIALS, PROFILE }

/** Profile's own tiny sub-navigation, same shape as [MaterialsRoute] but with just the two
 * destinations Profile currently needs. Not yet migrated to Navigation 3 - see the plan file. */
private sealed interface ProfileSubScreen {
    data object Main : ProfileSubScreen
    data object Settings : ProfileSubScreen
}

/**
 * The 3-tab navigation shell: Home / Materials / Profile. Materials doubles as the lesson browser
 * (Material Detail already lists a book's lessons with richer progress stats than a flat list
 * ever could) - the former standalone Study tab was a redundant second way to reach the same
 * [evola.composeapp.lessons.LessonDetailScreen], so it was folded in here; Home's "Continue
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
    var profileSubScreen by remember { mutableStateOf<ProfileSubScreen>(ProfileSubScreen.Main) }

    // MaterialsNavContext (see that class's doc comment) is the bridge between MainScreen's own
    // state and the Koin-declared MaterialsRoute entries - kept in sync here rather than recreated
    // per-composition so Home's cross-tab CTAs below and every route in materialsNavigationModule
    // see the same backStack instance and the current goal id.
    val materialsNavContext = koinInject<MaterialsNavContext>()
    materialsNavContext.backStack = materialsBackStack
    LaunchedEffect(goal.id) { materialsNavContext.goalId = goal.id }
    materialsNavContext.onExitToHome = {
        selectedTab = MainTab.HOME
        materialsBackStack.clear()
        materialsBackStack.add(MaterialsRoute.List)
    }

    val showTabBar = (selectedTab != MainTab.MATERIALS || materialsBackStack.size == 1) &&
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

                MainTab.PROFILE -> ProfileTabHost(
                    goal = goal,
                    subScreen = profileSubScreen,
                    onSubScreenChange = { profileSubScreen = it },
                    onGoalUpdated = { updated -> goal = updated },
                )
            }
        }
    }
    }
}

/** Materials tab's own sub-navigation - a real Navigation 3 back stack (see [MaterialsRoute] and
 * [materialsNavigationModule]) instead of the hand-rolled `when`-on-a-sealed-interface state
 * machine this replaced (see git history for that version). [backStack] is hoisted in [MainScreen],
 * not created here, since [MainScreen] also needs its size to decide whether the tab bar is
 * visible, and Home's cross-tab CTAs need to reset it before switching tabs in. */
@Composable
private fun MaterialsTabHost(backStack: MutableList<MaterialsRoute>) {
    BackHandler(enabled = backStack.size > 1) { backStack.removeLastOrNull() }
    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider = koinEntryProvider(),
    )
}

/** Profile tab's own sub-navigation - same extraction rationale as [MaterialsTabHost]. */
@Composable
private fun ProfileTabHost(
    goal: Goal,
    subScreen: ProfileSubScreen,
    onSubScreenChange: (ProfileSubScreen) -> Unit,
    onGoalUpdated: (Goal) -> Unit,
) {
    when (subScreen) {
        ProfileSubScreen.Main -> {
            val profileViewModel = koinViewModel<ProfileViewModel>(key = goal.id) { parametersOf(goal.id) }
            ProfileScreen(
                goal = goal,
                viewModel = profileViewModel,
                onGoalUpdated = onGoalUpdated,
                onOpenSettings = { onSubScreenChange(ProfileSubScreen.Settings) },
            )
        }

        ProfileSubScreen.Settings -> {
            val settingsViewModel = koinViewModel<SettingsViewModel>()
            val reminderScheduler = evola.composeapp.reminders.rememberReminderScheduler()
            val currentSettingsState by settingsViewModel.collectAsState()
            val requestNotificationPermission = evola.composeapp.reminders.rememberNotificationPermissionRequester { granted ->
                if (granted) {
                    reminderScheduler.scheduleDaily(currentSettingsState.settings.reminderHour)
                } else {
                    // Permission denied - the toggle stays visually on (matching the OS's own
                    // "you can flip this in system settings later" convention) but nothing is
                    // actually scheduled until the user grants it from system settings.
                    settingsViewModel.setNotificationsEnabled(false)
                }
            }
            val speechService = evola.composeapp.speech.rememberSpeechService()
            SettingsScreen(
                viewModel = settingsViewModel,
                speechService = speechService,
                onBack = { onSubScreenChange(ProfileSubScreen.Main) },
                onNotificationsToggled = { enabled ->
                    if (enabled) requestNotificationPermission() else reminderScheduler.cancel()
                },
            )
        }
    }
}
