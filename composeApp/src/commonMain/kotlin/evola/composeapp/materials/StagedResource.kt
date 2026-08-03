package evola.composeapp.materials

/** What the Add Resource screen hands off to the AI Wizard - the picked file or pasted text is
 * held here, not uploaded yet. The wizard's "Start Analysis" is the real submission point (adds
 * resource_type/organization_mode/ai_instructions and calls [evola.shared.materials.MaterialsRepository]). */
sealed interface StagedResource {
    val title: String

    data class File(val fileName: String, val mimeType: String, val bytes: ByteArray) : StagedResource {
        override val title: String get() = fileName
    }

    data class Text(val text: String) : StagedResource {
        override val title: String get() = "Pasted text"
    }
}
