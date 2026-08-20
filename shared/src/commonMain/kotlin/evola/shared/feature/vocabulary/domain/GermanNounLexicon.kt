package evola.shared.feature.vocabulary.domain

import evola.shared.db.EvolaDatabase

/** One row from the local `german_nouns` table (populated once by [GermanNounImporter] from the
 * bundled gambolputty/german-nouns dataset, CC-BY-SA-4.0 - attribution in Profile's Credits
 * section, see [evola.composeapp.feature.profile.ui.ProfileScreen]). [rawRow] keeps the full original CSV row
 * (all case/number flexion columns) even though only the indexed fields are used today, so a
 * later feature can read a specific flexion column without re-parsing the source file. */
data class GermanNounEntry(
    val lemma: String,
    val partOfSpeech: String,
    val genus: String?,
    val nominativPlural: String?,
    val rawRow: String,
)

/** Queries the local `german_nouns` table instead of holding the ~100k-row dataset in memory - a
 * single indexed SQLite lookup per call is both faster and lighter than an in-memory Map of the
 * whole dataset for the handful of terms a lesson's vocabulary extraction actually looks up.
 * Nouns only - the dataset has no verb/adjective coverage; callers fall back to the AI for those.
 * Returns null (not an error) before [GermanNounImporter] has finished its one-time import. */
class GermanNounLexicon(private val db: EvolaDatabase) {
    fun lookup(term: String): GermanNounEntry? {
        val key = term.trim()
        if (key.isEmpty()) return null
        val row = db.germanNounsQueries.lookupNoun(key).executeAsOneOrNull()
            // ß and "ss" are the same sound/word (e.g. source text spelling "Fussball" vs. the
            // dataset's canonical "Fußball") - a plain exact match misses this, so retry with
            // the other spelling before giving up. Only fires when the first lookup misses, and
            // only when the term actually contains one of the two forms.
            ?: run {
                val altKey = when {
                    key.contains("ß") -> key.replace("ß", "ss")
                    key.contains("ss") -> key.replace("ss", "ß")
                    else -> return null
                }
                db.germanNounsQueries.lookupNoun(altKey).executeAsOneOrNull()
            }
            ?: return null
        return GermanNounEntry(row.lemma, row.part_of_speech, row.genus, row.nominativ_plural, row.raw_row)
    }
}
