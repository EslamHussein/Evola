package evola.shared.language

/** The learner's chosen native language, set during onboarding and editable later from Profile.
 * Drives the vocabulary extraction prompt's translation target ([aiPromptName]) and the UI's
 * RTL/font choice ([isRtl]). A small curated list rather than free text - every entry is
 * guaranteed to render correctly (font glyph coverage, correct layout direction); adding a new
 * language later is exactly one more entry, no other code change needed since every call site
 * branches on [isRtl]/[aiPromptName], never on the specific language. */
enum class NativeLanguage(
    val code: String,
    val englishName: String,
    val nativeName: String,
    val isRtl: Boolean,
    val aiPromptName: String,
) {
    ARABIC("ar", "Arabic", "العربية", true, "Modern Standard Arabic"),
    ENGLISH("en", "English", "English", false, "English"),
    GERMAN("de", "German", "Deutsch", false, "German"),
    ;

    companion object {
        fun fromCode(code: String): NativeLanguage = entries.first { it.code == code }
    }
}
