package evola.composeapp.theme

import androidx.compose.ui.unit.dp

/** Spacing scale for the design-handoff redesign's screens - the app had no shared spacing tokens
 * before this (existing screens use ad hoc inline dp values), so this is additive only; it doesn't
 * refactor screens outside this redesign. */
object EvolaSpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
}
