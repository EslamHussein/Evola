@file:OptIn(org.koin.core.annotation.KoinExperimentalAPI::class)

package evola.composeapp.main

import androidx.compose.runtime.Composable
import androidx.navigation3.ui.NavDisplay
import evola.composeapp.feature.profile.ui.ProfileRoute
import org.koin.compose.navigation3.koinEntryProvider

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
internal fun MaterialsTabHost(backStack: MutableList<MaterialsRoute>) {
    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider = koinEntryProvider(),
    )
}

/** Profile tab's own sub-navigation - same extraction rationale as [MaterialsTabHost]. */
@Composable
internal fun ProfileTabHost(backStack: MutableList<ProfileRoute>) {
    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider = koinEntryProvider(),
    )
}
