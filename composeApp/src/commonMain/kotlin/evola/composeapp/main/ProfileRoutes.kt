package evola.composeapp.main

import kotlinx.serialization.Serializable

/** Navigation 3 route model for the Profile tab, same rationale/shape as [MaterialsRoute] but much
 * smaller - just the two destinations Profile has ever needed. */
@Serializable
sealed interface ProfileRoute {
    @Serializable data object Main : ProfileRoute
    @Serializable data object Settings : ProfileRoute
}
