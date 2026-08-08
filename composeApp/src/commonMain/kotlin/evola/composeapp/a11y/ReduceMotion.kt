package evola.composeapp.a11y

import androidx.compose.runtime.Composable

/** Whether the OS reports a reduced-motion accessibility preference. Loading indicators (and any
 * other decorative-only animation) should fall back to a static state when this is true, per
 * LOADING_INDICATORS_HANDOFF.md. */
@Composable
expect fun isReduceMotionEnabled(): Boolean
