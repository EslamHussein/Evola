---
name: material3-kmp
description: >
  Material 3 UI engineering for Kotlin Multiplatform and Compose Multiplatform.
  Use when implementing, reviewing, refactoring, or designing UI, components,
  navigation, adaptive layouts, accessibility, theming, or design systems
  in KMP projects.
---

# Material 3 for Evola (Kotlin Multiplatform / Compose Multiplatform)

Evola is a single-user, local-first, dark-only KMP app. This skill is grounded in what
actually exists in this repo today — reuse it, don't parallel it. Versions and file
paths below were verified against the codebase; re-verify with a quick grep if this
skill feels stale (dependency bumps, new components) rather than trusting it blindly.

## Project ground truth (verified — re-check `gradle/libs.versions.toml` if suspect)

- **Kotlin** 2.1.0, **Compose Multiplatform** 1.8.0, **AGP** 8.7.3, JVM toolchain 21.
- **Material3**: bundled via the Compose Multiplatform Gradle plugin (`compose.material3`) —
  no separately pinned `androidx.compose.material3` version. Treat Compose Multiplatform
  1.8.0's Material3 surface as the ceiling: don't recommend APIs newer than that without
  checking they're actually resolvable (`ExperimentalMaterial3Api` opt-ins are common and fine).
- **compileSdk/targetSdk** 35, **minSdk** 24.
- Modules: `:shared` (pure KMP business/data, no UI), `:composeApp` (all UI —
  `commonMain`/`androidMain`/`iosMain`), `:androidApp` (thin Android shell), `iosApp/`
  (native Xcode project hosting `ComposeUIViewController`, not a Gradle module).
- Theme: `composeApp/src/commonMain/kotlin/evola/composeapp/theme/EvolaTheme.kt` — dark-only
  (`darkColorScheme()` unconditionally, no `isSystemInDarkTheme()`), no dynamic color.
  `EvolaColors` (hand-picked "Nocturne" palette: Paper/Surface/SurfaceAlt/Border/Accent/
  Teal/Rust/Amber/Text tiers, German-gender mnemonic colors), mapped explicitly onto the
  full M3 `colorScheme` tonal ladder (see EvolaTheme.kt:103-138 — comment there explains
  *why* `surfaceContainer*` is pinned rather than auto-derived). `EvolaTypography` overrides
  every standard M3 role with Inter (+ a bespoke `dataMono()` style for numeric readouts,
  outside the M3 slot system by design). Shapes: `extraSmall=8dp, small=12dp, medium=20dp,
  large=24dp, extraLarge=100dp`. Spacing: `EvolaSpacing` (`xs=4, sm=8, md=12, lg=16, xl=24,
  xxl=32`, dp) — additive-only; many existing screens still use ad hoc inline dp values,
  don't assume every padding call already uses it.
- Reusable components: `composeApp/src/commonMain/kotlin/evola/composeapp/theme/components/`
  — `DesignComponents.kt` (SelectableChip, IconTile, StatusTag, SegmentedProgressBar,
  CircularProgressRing, ComingSoonChip, LockedRow, GlassNavigationBar), `AppBottomSheetScaffold.kt`
  (custom 4-state non-modal bottom sheet built on `AnchoredDraggableState`, guarantees the
  sheet never covers a persistent bottom bar by layout construction), `AppLogoMark.kt`.
- Navigation: **not** Navigation Compose/Navigation3. Custom `sealed interface` +
  `when`-based screen state in `MainScreen.kt` (`MainTab` enum for the 3-tab shell,
  `MaterialsSubScreen` sealed interface for the Materials stack), plain
  `remember { mutableStateOf(...) }`, back handled via the custom `BackHandler` expect/actual.
- Adaptive UI: **not implemented**. No `WindowSizeClass`, no breakpoints, no foldable
  awareness. `BoxWithConstraints` exists in exactly two places (`AppBottomSheetScaffold.kt`,
  `SplashScreen.kt`) for sizing, not breakpoints. Phone-only today.
- Accessibility: minimal. `contentDescription` used ad hoc (~51 call sites), no
  `semantics {}`, no `mergeDescendants`, no explicit touch-target sizing. A dedicated
  `a11y` package exists but only implements reduce-motion detection
  (`composeApp/.../a11y/ReduceMotion.kt`, expect/actual).
- Platform-specific UI expect/actuals that exist today: `BackHandler` (Android delegates
  to `androidx.activity.compose.BackHandler`; iOS is a no-op — no system back gesture,
  screens use on-screen back buttons) and `FilePicker`/image/camera pickers
  (`ActivityResultContracts` on Android, `UIDocumentPickerViewController`/
  `UIImagePickerController` cinterop on iOS). No expect/actual yet for system bars, date
  pickers, or share sheets — if a task needs one, it doesn't exist, build it deliberately.
- State pattern: `androidx.lifecycle.ViewModel` (KMP artifact) + `viewModelScope`,
  `MutableStateFlow` → `.asStateFlow()`, sealed-interface `UiState` (`Loading`/`Loaded`/
  `Error` shape repeats consistently — see `HomeViewModel.kt`, `ProcessingStatusViewModel.kt`).
  Composables collect via `collectAsStateWithLifecycle()` (never plain `collectAsState()`).

Do not reduce "Material 3" to "wraps `MaterialTheme`". It spans color roles, typography,
shape, elevation/surface hierarchy, component + interaction states, motion, accessibility,
adaptive layout, navigation, insets, and dark theme — evaluate all of them, not just color.

## 1. Color

Always use semantic roles from `MaterialTheme.colorScheme`, never raw `EvolaColors` hex
values or `Color(0x...)` literals inside reusable components — `EvolaColors` is the
source-of-truth palette, `colorScheme` is how components should consume it (see the
explicit mapping in `EvolaTheme.kt`). A screen-specific one-off (e.g. the German gender
mnemonic colors, which are deliberately outside the M3 role system) is the one legitimate
exception — those are a documented domain concept, not a shortcut.

```kotlin
MaterialTheme.colorScheme.primary        // Accent
MaterialTheme.colorScheme.onSurface      // Text
MaterialTheme.colorScheme.surfaceVariant // Surface
MaterialTheme.colorScheme.error          // Rust
```

For every color decision, check: light/dark (this app is dark-only, so "light theme" means
"don't accidentally hardcode something that'll break if light mode is ever added" — flag
it, don't silently add light-mode support nobody asked for), error, disabled, selected,
pressed, surface hierarchy (does this new surface sit at the right tonal step relative to
its neighbors?), and contrast (WCAG AA: 4.5:1 body text, 3:1 large text/UI components).

## 2. Typography

Use `MaterialTheme.typography.*` roles (`displayLarge` hero, `headlineLarge` section,
`titleLarge` card/dialog titles, `bodyLarge`/`bodyMedium` content, `labelLarge` buttons/
captions). Reach for `EvolaTypography.dataMono()` only for numeric readouts (percentages,
scores, timestamps) — that's its documented, narrow purpose, not a general escape hatch.
Arbitrary inline `fontSize`/`fontWeight` needs a stated design reason in the PR/response,
not silent use.

## 3. Shapes

Use `MaterialTheme.shapes.*` (`extraSmall`=8dp chips, `small`=12dp cards, `medium`=20dp
dialogs/sheets, `large`=24dp prominent surfaces, `extraLarge`=100dp pills/FABs — Evola's
scale, not the M3 defaults, already centralized in `evolaShapes()`). A hardcoded
`RoundedCornerShape(Ndp)` outside that scale is a smell — either it maps to an existing
shape token or it's a new one that belongs in `EvolaTheme.kt`, not scattered inline.

## 4. Spacing

Prefer `EvolaSpacing` (`xs=4, sm=8, md=12, lg=16, xl=24, xxl=32`). Reality check: much of
the existing codebase still uses inline dp values, so don't treat every existing
`padding(14.dp)` as a bug to fix uninvited — but new code should use the scale, and if a
task touches a screen with scattered inline values, migrating the ones you're already
touching (not a drive-by rewrite of the whole file) is reasonable. A genuinely novel
spacing value needs a reason (e.g. matching a specific icon's optical padding); "13.dp
looked right" is not one.

## 5. Components

Prefer official M3 components (`Button`/`FilledTonalButton`/`OutlinedButton`/`TextButton`,
`Card`/`ElevatedCard`/`OutlinedCard`, `NavigationBar`/`NavigationRail`/`NavigationDrawer`,
`TopAppBar` family, `FloatingActionButton`, `TextField`/`OutlinedTextField`, `Checkbox`/
`RadioButton`/`Switch`, chips, `AlertDialog`, `ModalBottomSheet`, `Snackbar`, progress
indicators) over recreating them. Before building anything new, check
`theme/components/` — Evola already has `SelectableChip`, `IconTile`, `StatusTag`,
`SegmentedProgressBar`, `CircularProgressRing`, `GlassNavigationBar`,
`AppBottomSheetScaffold`. A custom component is justified when M3 doesn't provide the
behavior (e.g. `GlassNavigationBar`'s frosted/floating look, `AppBottomSheetScaffold`'s
non-modal 4-state sheet that `ModalBottomSheet`/`BottomSheetScaffold` can't do) — not for
convenience or slight visual preference.

## 6. Bottom navigation + bottom sheet

Evola already solved this once (`AppBottomSheetScaffold`, wired into `MainScreen.kt` for
the live processing-status indicator) — reuse it rather than re-deriving the pattern.
**Never use `ModalBottomSheet`** where a persistent nav bar must stay visible/interactive;
it owns the full bottom edge and will cover custom nav bars. `BottomSheetScaffold` is
non-modal but owns its own bottom slot, so only use it standalone, not layered against
`GlassNavigationBar` without checking the layering (Evola's working pattern: the sheet's
region is bounded by the space the outer `Scaffold` already reserves above its
`bottomBar`, guaranteed by measured `BoxWithConstraints` height, not z-order).

For any screen combining both, evaluate explicitly: collapsed / partially expanded / fully
expanded sheet states, nav bar visibility and interactivity in each, sheet drag gesture
behavior, FAB position relative to both, system navigation bar insets, IME/keyboard
behavior when the sheet contains input, and accessibility (can a screen reader user still
reach nav items while the sheet is open?). Sheets must never unintentionally cover
navigation — if a design seems to require that, flag it rather than implementing it silently.

## 7. Adaptive UI

Evola is phone-only today — no `WindowSizeClass`, no breakpoints exist. Don't add a
speculative adaptive layer to a screen nobody asked to make adaptive. When a task
*does* ask for tablet/foldable/landscape/large-screen support: compact → `NavigationBar`
(matches current `GlassNavigationBar` usage), medium → `NavigationBar` or `NavigationRail`
depending on actual content density and reachability, expanded → `NavigationRail` or
`NavigationDrawer`. This mapping is a starting point, not a rule to apply blindly — check
what the screen actually needs (a 3-tab shell may stay a bottom bar even at `Medium` if
there's no case for a rail). Foldable posture: never place interactive content or critical
info across a hinge.

## 8. KMP boundaries

`commonMain` is the default for UI and business logic. Never introduce Android `Context`,
`Activity`, Android-only Material APIs, or Android resources into `commonMain` —
`composeApp/src/commonMain` currently has zero such leaks; keep it that way. When a
platform genuinely needs different behavior (see the `BackHandler` and `FilePicker`
precedents above), use `expect`/`actual` with the actual implementation living in
`androidMain`/`iosMain` only — not for things that could reasonably be shared. Before
reaching for `expect`/`actual`, check whether the difference can be handled with a
parameter/callback from commonMain instead; only fall back to `expect`/`actual` when the
platform APIs themselves are genuinely different (file pickers, system back gesture,
haptics, share sheets, date/time pickers).

## 9. Android vs iOS UX

Compose Multiplatform shares UI code, not platform conventions. Respect the differences
already encoded in this repo (iOS `BackHandler` is a no-op — Android's system back gesture
has no iOS equivalent, so iOS screens need their own visible back affordance, never assume
a back gesture will save an iOS screen without one). Extend the same judgment to anything
not yet built: system dialogs, permission prompts, keyboard behavior/avoidance, system UI
(status bar style, safe areas), date/time pickers, share sheets — check platform
convention before assuming Android's pattern is universal. Never force Android UX onto iOS
(or vice versa) when it harms that platform's feel.

## 10. Accessibility

Evola's a11y coverage today is thin (ad hoc `contentDescription`, no `semantics {}`, no
touch-target enforcement) — this is a real gap, not a style choice, so hold new/touched
code to a higher bar than what's already there rather than matching it. For UI work, check:
touch targets (≥48dp), meaningful `contentDescription` (and explicit `null` for decorative
icons — don't add a description just to silence a lint warning), `semantics {}` /
`mergeDescendants` for composite rows (e.g. a `StatusTag` + label that should announce as
one unit), heading semantics for section titles, logical focus/traversal order, text
scaling (does the layout survive 200% font scale without clipping?), contrast, and that
error states are announced, not just colored red. Don't add meaningless
`contentDescription = "icon"` — a missing description that gets flagged is better than a
useless one that silences the flag.

## 11. UI states

For every screen/component with real state, cover the full set, not just the happy path:
Loading, Content, Empty, Error, Refreshing, Offline (this app is local-first/serverless —
"offline" mostly means "AI features gated on a stored key," check `Profile`/AI-call sites
for how that's already handled), Disabled, Selected, Pressed, Focused. The existing
`sealed interface XState { Loading / Loaded(...) / Error(message) }` shape is the project
convention — extend it, don't invent a parallel shape.

## 12. Architecture

No business logic in composables. Composables render `UiState` and emit events; the
`ViewModel` (extends `androidx.lifecycle.ViewModel`, exposes `StateFlow` via
`.asStateFlow()`, `viewModelScope.launch` for async work) owns state derivation and talks
to `:shared` repositories. Match the existing pattern exactly (see `HomeViewModel.kt`,
`ProcessingStatusViewModel.kt`) rather than introducing a second state-management style.

## 13. Design-system reuse

Before adding a new color, typography style, shape, spacing value, button variant, card
style, input, or nav component: grep `theme/` and `theme/components/` first. Duplicating
an existing token or component under a slightly different name is the most common way
this kind of system rots — if something close already exists, extend or parameterize it
instead of adding a sibling.

## Workflow

1. **Inspect** — re-check the relevant part of the ground-truth section above against the
   actual files if anything seems like it may have changed (versions, new components).
2. **Identify** — find existing M3 components/tokens/patterns that already cover this need.
3. **Design** — pick the right M3 pattern before writing code; state the choice and why.
4. **Implement** — Compose Multiplatform in `commonMain` by default; platform code only
   behind `expect`/`actual` when genuinely required.
5. **Validate** — M3 compliance, accessibility, adaptive behavior (if in scope), dark mode
   (this app is dark-only — verify nothing assumes a light scheme), insets, keyboard/IME,
   component states, error/empty/loading states, Android behavior, iOS behavior.
6. **Verify** — run what the repo actually has: `./gradlew :composeApp:compileDebugKotlinAndroid`
   and `./gradlew :composeApp:compileKotlinIosSimulatorArm64` at minimum for any commonMain
   change (confirms true KMP compatibility, catches Android-only API leaks); there is no
   dedicated UI test suite today, so manual verification (build + install, per this
   project's established device-testing workflow) is the only additional check available —
   say so explicitly rather than claiming automated UI verification that doesn't exist.
7. **Report** — what changed, which M3 decisions were made and why, which KMP decisions
   were made (shared vs expect/actual, and why), any platform-specific behavior introduced,
   any limitations, any experimental APIs used (`@OptIn(ExperimentalMaterial3Api::class)`
   etc. — flag them, don't bury them).

## Conflicts and existing code

Don't blindly rewrite. If existing code already follows M3 correctly, leave it. If a
product requirement conflicts with strict M3 guidance: name the conflict, explain the M3-
recommended approach, then preserve the explicit product requirement unless told to change
it — implement the closest accessible/maintainable version of what was actually asked for.

## References

Treat official Google/Android (developer.android.com/jetpack/compose, m3.material.io) and
JetBrains Compose Multiplatform docs as the primary source of truth over blog posts,
especially for anything version-sensitive. Given this project pins Compose Multiplatform
1.8.0/Kotlin 2.1.0, don't recommend APIs from a newer Compose Multiplatform release without
checking they actually resolve in this project first — a plausible-sounding newer M3 API
that doesn't exist in 1.8.0 is worse than a slightly older pattern that compiles.

## Quality gate

Before calling UI work done, check:

**Material 3** — correct component choice · correct color roles (no hardcoded hex in
reusable components) · correct typography role · correct shape token · correct
elevation/surface hierarchy · correct spacing (token or justified) · all interaction states
covered (default/hover/pressed/focused/disabled/selected/error).

**Accessibility** — touch targets ≥48dp · meaningful `contentDescription` (or explicit
`null`) · semantics/merge for composite elements · contrast (4.5:1 body / 3:1 large) ·
survives text scaling · logical focus order.

**KMP** — shared code stays in `commonMain` · no Android `Context`/`Activity`/Android-only
API leaked into `commonMain` · platform differences isolated behind `expect`/`actual`,
used only where genuinely needed · iOS behavior explicitly considered, not assumed
identical to Android.

**Adaptive** (when in scope) — compact/medium/expanded considered · landscape considered ·
insets handled · keyboard/IME handled.

**UX states** — loading · empty · error · offline/AI-key-gated · disabled · selected ·
user feedback on every action that needs it.
