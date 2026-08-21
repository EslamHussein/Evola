package evola.composeapp.main

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.rememberHazeState
import evola.composeapp.core.designsystem.EvolaColors
import evola.composeapp.core.designsystem.EvolaTheme
import evola.shared.feature.profile.domain.AppTheme
import evola.composeapp.core.designsystem.components.GlassNavigationBar
import evola.composeapp.generated.resources.Res
import evola.composeapp.generated.resources.main_nav_home
import evola.composeapp.generated.resources.main_nav_materials
import evola.composeapp.generated.resources.main_nav_profile
import org.jetbrains.compose.resources.stringResource

/** The bottom tab bar itself, extracted out of [MainScreen] so it's previewable on its own -
 * [MainScreen] is otherwise Koin-wired end to end (koinInject/koinViewModel), which a @Preview
 * can't satisfy, but this row is pure UI. A white-alpha indicator (right for the old dark glass
 * bar) all but disappears against Reword's light palette's white/near-white blurred surface - the
 * accent at low alpha reads on both. */
@Composable
internal fun MainTabBar(
    selectedTab: MainTab,
    onSelectTab: (MainTab) -> Unit,
    hazeState: HazeState,
) {
    val glassIndicatorColor = EvolaColors.Accent.copy(alpha = 0.16f)
    val glassItemColors = NavigationBarItemDefaults.colors(indicatorColor = glassIndicatorColor)
    GlassNavigationBar(hazeState = hazeState) {
        val homeLabel = stringResource(Res.string.main_nav_home)
        val materialsLabel = stringResource(Res.string.main_nav_materials)
        val profileLabel = stringResource(Res.string.main_nav_profile)
        NavigationBarItem(
            selected = selectedTab == MainTab.HOME,
            onClick = { onSelectTab(MainTab.HOME) },
            icon = { Icon(Icons.Filled.Home, contentDescription = homeLabel) },
            label = { Text(homeLabel) },
            colors = glassItemColors,
        )
        NavigationBarItem(
            selected = selectedTab == MainTab.MATERIALS,
            onClick = { onSelectTab(MainTab.MATERIALS) },
            icon = { Icon(Icons.Filled.Folder, contentDescription = materialsLabel) },
            label = { Text(materialsLabel) },
            colors = glassItemColors,
        )
        NavigationBarItem(
            selected = selectedTab == MainTab.PROFILE,
            onClick = { onSelectTab(MainTab.PROFILE) },
            icon = { Icon(Icons.Filled.Person, contentDescription = profileLabel) },
            label = { Text(profileLabel) },
            colors = glassItemColors,
        )
    }
}

@Preview
@Composable
private fun MainTabBarLightPreview() {
    EvolaTheme(appTheme = AppTheme.LIGHT) {
        MainTabBar(selectedTab = MainTab.HOME, onSelectTab = {}, hazeState = rememberHazeState())
    }
}

@Preview
@Composable
private fun MainTabBarDarkPreview() {
    EvolaTheme(appTheme = AppTheme.DARK) {
        MainTabBar(selectedTab = MainTab.HOME, onSelectTab = {}, hazeState = rememberHazeState())
    }
}
