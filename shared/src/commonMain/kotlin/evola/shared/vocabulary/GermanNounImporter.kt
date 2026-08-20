package evola.shared.vocabulary

import evola.shared.core.common.SqlLoggingGate
import evola.shared.db.EvolaDatabase

sealed interface GermanNounImportState {
    data object NotStarted : GermanNounImportState
    data class InProgress(val imported: Int, val total: Int) : GermanNounImportState
    data object Done : GermanNounImportState
}

/** One-time CSV -> SQLite import of the bundled German noun dataset (see [GermanNounLexicon]) -
 * runs once per install (or after a future dataset update that clears the table), then every
 * later app launch queries the already-populated table directly instead of re-parsing a 20MB file
 * every time. [importIfNeeded] is a no-op if the table already has rows. */
class GermanNounImporter(private val db: EvolaDatabase) {

    suspend fun importIfNeeded(csvText: String, onProgress: (imported: Int, total: Int) -> Unit) {
        if (db.germanNounsQueries.countNouns().executeAsOne() > 0) return

        val lines = csvText.lineSequence().iterator()
        if (!lines.hasNext()) return
        lines.next() // header - column order (lemma, pos, genus, ..., nominativ plural at index 16) is fixed

        val seenLemmas = HashSet<String>()
        var imported = 0
        val batch = ArrayList<PendingRow>(BATCH_SIZE)

        fun flush() {
            if (batch.isEmpty()) return
            db.germanNounsQueries.transaction {
                batch.forEach { row -> db.germanNounsQueries.insertNoun(row.lemma, row.pos, row.genus, row.plural, row.raw) }
            }
            batch.clear()
        }

        // ~100k individual INSERTs through the normal per-query-logged driver measured at ~5
        // minutes (each statement synchronously writes a log line); suppressed, the same import
        // takes seconds. Reset in `finally` so a crash mid-import can't leave logging off for the
        // rest of the app session.
        SqlLoggingGate.suppressed = true
        try {
            while (lines.hasNext()) {
                val line = lines.next()
                if (line.isBlank()) continue
                val columns = parseCsvRow(line)
                val lemma = columns.getOrNull(0)?.trim()?.takeIf { it.isNotEmpty() } ?: continue
                // First entry per lemma wins - the source file lists compound-word sub-entries and
                // rare variants after the primary form for common nouns.
                if (!seenLemmas.add(lemma.lowercase())) continue
                val partOfSpeech = columns.getOrNull(1)?.trim().orEmpty()
                val genus = columns.getOrNull(2)?.trim()?.takeIf { it.isNotEmpty() }
                val plural = columns.getOrNull(16)?.trim()?.takeIf { it.isNotEmpty() }
                batch += PendingRow(lemma, partOfSpeech, genus, plural, line)
                imported++
                if (batch.size >= BATCH_SIZE) {
                    flush()
                    onProgress(imported, TOTAL_ROWS_HINT)
                }
            }
            flush()
        } finally {
            SqlLoggingGate.suppressed = false
        }
        onProgress(imported, imported)
    }

    private data class PendingRow(val lemma: String, val pos: String, val genus: String?, val plural: String?, val raw: String)

    companion object {
        private const val BATCH_SIZE = 2000

        // Real row count in the bundled file (verified at download time) - used only to compute a
        // percentage for the progress UI; a future dataset update with a different count just makes
        // the percentage approximate for one release, never wrong in a way that matters.
        const val TOTAL_ROWS_HINT = 102_445

        /** Minimal RFC4180-style CSV row split: comma-separated, double-quoted fields may contain
         * commas (e.g. `pos` values like `"Gebundenes Lexem,Substantiv"`). The source dataset
         * contains no escaped (`""`) quotes, so that case isn't handled. */
        private fun parseCsvRow(line: String): List<String> {
            val fields = mutableListOf<String>()
            val current = StringBuilder()
            var inQuotes = false
            for (char in line) {
                when {
                    char == '"' -> inQuotes = !inQuotes
                    char == ',' && !inQuotes -> {
                        fields += current.toString()
                        current.clear()
                    }
                    else -> current.append(char)
                }
            }
            fields += current.toString()
            return fields
        }
    }
}
