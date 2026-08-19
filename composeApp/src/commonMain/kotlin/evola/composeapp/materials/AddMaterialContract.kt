package evola.composeapp.materials

/** The Add Resource type grid - PDF, Text, and Image (photographed pages, transcribed on-device
 * via Claude vision - see [evola.shared.ai.ImageTranscriber]). Website/Notes stay out of scope,
 * matching the design handoff. */
enum class ResourceType { PDF, TEXT, IMAGE }

/** Add Resource screen state: picks a type and stages a file/pasted text - no network calls here.
 * The real upload happens at the AI Wizard's "Start Analysis" (see [StagedResource]). */
data class AddMaterialState(val selectedType: ResourceType = ResourceType.PDF)
