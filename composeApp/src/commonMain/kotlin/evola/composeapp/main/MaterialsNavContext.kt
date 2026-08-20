package evola.composeapp.main

import androidx.compose.runtime.snapshots.SnapshotStateList
import evola.composeapp.materials.StagedResource

/** Koin-scoped context for the Materials tab's navigation - a single `single { }` this app fully
 * controls, deliberately used instead of relying on koin-compose-navigation3's own (undocumented,
 * as of this writing) back-stack injection scoping. [MaterialsTabHost] assigns [backStack] once per
 * composition; every `navigation<MaterialsRoute.X> { }` entry reads it via `koinInject<
 * MaterialsNavContext>()` to push/pop, same as it reads [goalId]/[takeStagedResource] below.
 *
 * - [backStack]: the Materials tab's real Navigation 3 back stack. Hoisted at [MainScreen]'s level
 *   (not local to [MaterialsTabHost]) so it survives switching away to another tab, and so Home's
 *   cross-tab CTAs (hands-free practice, flashcards, category/mode session) can reset it before
 *   switching tabs in - matching the pre-migration behavior where those CTAs always started the
 *   Materials tab fresh at the target screen rather than resuming wherever it was left.
 * - [goalId]: kept in sync with [MainScreen]'s own `goal` state (see the `LaunchedEffect` in
 *   [MainScreen]) so a route-construction call site that isn't itself parameterized by goal (e.g.
 *   [MaterialsRoute.Add]'s "continue to wizard" action) can still read the current goal without
 *   every route needing its own `goalId` field.
 * - [stagedResource]/[takeStagedResource]: the just-picked [StagedResource] handed from
 *   AddMaterialScreen to the AI Wizard. Not carried on [MaterialsRoute.Wizard] itself - see that
 *   route's doc comment - because it can carry raw file bytes (potentially several MB), and
 *   Navigation 3's back stack is serialized for state save/restore; a multi-MB [ByteArray] in a
 *   route risks exceeding Android's Binder transaction size limit on restore. [takeStagedResource]
 *   reads and clears in one step since only one add-material flow is ever in progress at a time.
 */
class MaterialsNavContext {
    lateinit var backStack: SnapshotStateList<MaterialsRoute>
    var goalId: String = ""

    /** Set by [MainScreen] - switches the active tab back to Home. [MaterialsRoute.CategorySession]/
     * [MaterialsRoute.ModeSession] call this on completion, matching the pre-migration behavior
     * where finishing one of these goal-wide sessions always returned to Home rather than staying
     * on the Materials tab. */
    var onExitToHome: () -> Unit = {}
    private var stagedResource: StagedResource? = null

    fun setStagedResource(resource: StagedResource) {
        stagedResource = resource
    }

    fun takeStagedResource(): StagedResource? {
        val current = stagedResource
        stagedResource = null
        return current
    }
}
