# Compose conventions

- One `@Composable` function per file. Split out any other composables (sub-components, previews) into their own files rather than stacking multiple into one.
- Every composable file must include `@Preview` composables for it in both light and dark theme (e.g. via `EvolaTheme(appTheme = AppTheme.LIGHT) { ... }` / `EvolaTheme(appTheme = AppTheme.DARK) { ... }`).
