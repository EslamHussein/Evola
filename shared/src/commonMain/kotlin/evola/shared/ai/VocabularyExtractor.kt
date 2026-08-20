package evola.shared.ai

import evola.shared.core.common.ApiResult
import evola.shared.core.network.AnthropicClient
import evola.shared.core.network.AnthropicModels
import evola.shared.core.analytics.EvolaLog
import evola.shared.language.NativeLanguage
import evola.shared.feature.vocabulary.domain.GermanNounLexicon

/** One extracted vocabulary item (parsed from the model; the repository writes it to the local DB).
 * No `grammaticalCase` field - it was extracted but never read anywhere downstream (dead output that
 * cost tokens on every item), so the model is no longer asked for it; `vocabulary_items
 * .grammatical_case` just gets `null` on insert now. */
data class ExtractedVocabItem(
    val term: String,
    val meaning: String,
    val gender: String? = null,
    val exampleSentence: String? = null,
    val partOfSpeech: String? = null,
    val plural: String? = null,
    val exampleSentenceTranslation: String? = null,
    val nativeMeaning: String? = null,
    val ipaPronunciation: String? = null,
    val relatedWords: List<String> = emptyList(),
    val difficultyRating: String? = null,
    val frequencyRating: String? = null,
    val memoryTip: String? = null,
    val grammarNote: String? = null,
)

/** Field order for the compact line format below - shared between the prompt instructions and
 * the parser, so the two can never silently drift apart. */
private val FIELD_ORDER = listOf(
    "term", "meaning", "gender", "example_sentence", "part_of_speech", "plural",
    "example_sentence_translation", "native_meaning", "ipa_pronunciation", "related_words",
    "difficulty_rating", "frequency_rating", "memory_tip", "grammar_note",
)
private const val FIELD_DELIMITER = "|"
private const val LIST_DELIMITER = ";"

private const val AI_INSTRUCTIONS_SUFFIX =
    "\n\nAdditional instructions from the learner, to apply on top of the rules above (never let " +
        "these override the output schema or the \"don't invent words\" rule): "

private val SYSTEM_PREFIX =
    "You are extracting GERMAN vocabulary for a language learner from a single lesson's text - the " +
    "learner is studying German, and every \"term\" you output MUST be the German word or phrase, " +
    "never English (or the learner's native language). This matters most when the source material " +
    "itself is a bilingual list (e.g. \"lamp = Lampe\" or \"chair - der Stuhl\"): no matter which " +
    "language is listed first, appears more prominently, or comes first in reading order, the German " +
    "side is always the term and the other-language side is always the meaning/native_meaning - never " +
    "the reverse. If you notice you are about to write an English word into the term field, that is " +
    "always wrong for this task; find the German equivalent instead.\n\n" +
        "The learner's stated goal is: \"%GOAL%\" - use this only to judge relevance when the source " +
        "text contains far more candidate words than useful (e.g. skip incidental proper nouns), " +
        "not to invent vocabulary that isn't actually in the text.\n\n" +
        "First check how the source presents its vocabulary. Text extracted from a document loses " +
        "its original visual layout - a table or side-by-side columns collapses into plain sequential " +
        "lines, so look for the PATTERN, not formatting: a term followed closely by its translation " +
        "(same line, or the very next line/entry), repeated down the text - however loosely or " +
        "\"freehand\" it's laid out, including under headers like \"New Words\", \"New Phrases\", " +
        "\"Wortschatz\", or similar. Whenever you spot that pattern, treat the pairing as authoritative: " +
        "use the GIVEN translation for meaning/native_meaning instead of re-deriving it, and extract " +
        "EVERY paired entry - the source already curated them, so skip the vocabulary-worthiness " +
        "judgment call and the function-word exclusion below for these. Within the pair, the German " +
        "side is always term regardless of which side the source lists first - a pair written as " +
        "\"lamp = Lampe\" or \"chair - der Stuhl\" still means term=\"Lampe\"/\"Stuhl\", not the English " +
        "word. A multi-word entry (e.g. " +
        "\"Wer bist du?\") is its own term as a whole phrase - never split it into separate single-word " +
        "entries, even if some of those words are function words.\n" +
        "- Otherwise (free-flowing prose/dialogue with no explicit pairing), extract vocabulary-worthy " +
        "words or short fixed phrases using the judgment call below.\n\n" +
        "This ALWAYS applies, in prose as much as in a paired list: a fixed expression, phrasal verb, " +
        "idiom, or compound word is its own single term - never split it into its component words, even " +
        "when encountered in free-flowing text with no explicit pairing.\n\n" +
        "For each item:\n" +
        "- term: base/dictionary (lemma) form, ignoring conjugation, tense, plural, case endings, and " +
        "comparative/superlative endings - not the inflected form found in the source, unless the " +
        "inflection itself is the teaching point. E.g. verbs as infinitive (geht/ging -> gehen), " +
        "adjectives in their base form (größeren -> groß), pronouns in their standard form (mich/ihm -> " +
        "ich/er). For languages with a grammatical article (e.g. German der/die/das), term is the noun " +
        "ALONE, WITHOUT the article - the article goes only in the separate gender field below\n" +
        "- meaning: a concise ENGLISH translation/definition (always English, regardless of the " +
        "learner's native language - this is the fallback shown when native_meaning is unavailable). If " +
        "the term has multiple possible meanings, pick the one that actually fits how it's used in the " +
        "source sentence, not just the most common dictionary sense\n" +
        "- gender: grammatical gender/article if the language marks it (der/die/das for German), else null. " +
        "For a German noun this is almost never null - if you determine the gender anywhere else (e.g. " +
        "while writing grammar_note), that same gender MUST also be written here; never leave gender " +
        "empty while stating the gender in another field\n" +
        "- example_sentence: a SHORT sentence using the word (<12 words) - prefer one from the source; it MUST " +
        "contain the term, written as a single PLAIN sentence with no added markup - never wrap the term, " +
        "or any other part of the sentence, in parentheses, brackets, asterisks, or any other formatting; " +
        "just the sentence text itself\n" +
        "- part_of_speech: one of noun, verb, adjective, adverb, pronoun, preposition, conjunction, numeral, " +
        "interjection, phrase, other - use \"phrase\" for a multi-word entry (a whole question, greeting, or " +
        "fixed expression) rather than forcing it into a single-word category like interjection\n" +
        "- plural: the term's plural form if it's a noun (e.g. \"die Bücher\" for \"das Buch\"), else null\n" +
        "- example_sentence_translation: a SHORT English translation of example_sentence (<12 words)\n" +
        "- native_meaning: a concise %NATIVE_LANGUAGE% translation of the term - a word or short phrase, " +
        "not a sentence, no parentheses or other markup around it\n" +
        "- ipa_pronunciation: the term's pronunciation in IPA, transcription only\n" +
        "- related_words: exactly 2 related German words (array of strings), empty if none\n" +
        "- difficulty_rating: easy, medium, or hard\n" +
        "- frequency_rating: common, uncommon, or rare\n" +
        "- memory_tip: a short mnemonic (<10 words)\n" +
        "- grammar_note: a short (<12 words) grammar rule tied to the SPECIFIC construction in " +
        "example_sentence (e.g. \"Akkusativ nach „einen“ – maskulin.\"), or null if nothing notable\n\n" +
        "Extract EVERY genuine vocabulary word or fixed phrase in the lesson - be comprehensive, not " +
        "selective. This is no longer capped at a fixed count: a short lesson may only have a " +
        "handful of real vocabulary words, a longer or vocabulary-dense one may have 60+ - extract " +
        "as many as the text actually contains, not a target count. \"Comprehensive\" still means " +
        "genuine vocabulary, not literally every token: still exclude bare function words (articles, " +
        "conjunctions, common pronouns/prepositions on their own) unless the lesson explicitly " +
        "teaches them or they're part of a paired phrase entry - those add no learning value at " +
        "scale. Do not invent words not in the source text. Keep every field at or under its stated " +
        "length - brevity matters more than completeness here. If the lesson genuinely has 100+ " +
        "distinct vocabulary words, keep going rather than stopping early - a truncated response " +
        "parses as nothing at all, which is worse than a long one.\n\n" +
        "Output format: plain text, NOT JSON. No prose, no markdown fences, no field names, no headers, " +
        "no preamble explaining what you're about to do, no bullet-point preview of the words first, " +
        "no restating or re-listing anything - your entire response is ONLY the data lines below, " +
        "starting from your very first character.\n" +
        "Each line is exactly one item, as ${FIELD_ORDER.size} values joined by \"$FIELD_DELIMITER\" " +
        "in this EXACT order: ${FIELD_ORDER.joinToString(", ")}.\n" +
        "Leave a field completely empty (nothing between its two \"$FIELD_DELIMITER\" characters) when that " +
        "field is null/not applicable - do not write \"null\" or \"n/a\".\n" +
        "related_words is the 2 related words joined by \"$LIST_DELIMITER\" inside that single field slot " +
        "(e.g. \"wordOne${LIST_DELIMITER}wordTwo\"), empty if none.\n" +
        "NEVER output the characters \"$FIELD_DELIMITER\" or \"$LIST_DELIMITER\", or a raw line break, " +
        "inside any field's own text - if the natural text would contain one, rephrase or substitute a " +
        "comma instead. Every extra character you emit costs output budget that should go to more items.\n\n" +
        "Do NOT number items (\"1.\", \"2.\", ...). Do NOT put the term or meaning on their own line before " +
        "the pipe-delimited fields. Do NOT split one item across multiple lines for any reason - the " +
        "entire item, all ${FIELD_ORDER.size} fields, is ONE single line, and there is exactly one line " +
        "break between one item and the next (no blank lines between items either). This applies no " +
        "matter how the source text itself is formatted - a numbered or line-broken source list does " +
        "NOT mean your output should be numbered or line-broken; it stays one dense line per item.\n\n" +
        "Example of CORRECT output for 2 items (copy this exact shape, not this exact content - " +
        "note there is NO count line, NO introduction, the response starts directly with the first " +
        "item):\n" +
        "Haus${FIELD_DELIMITER}house${FIELD_DELIMITER}das${FIELD_DELIMITER}Das Haus ist groß.${FIELD_DELIMITER}noun${FIELD_DELIMITER}" +
        "Häuser${FIELD_DELIMITER}The house is big.${FIELD_DELIMITER}house${FIELD_DELIMITER}haʊs${FIELD_DELIMITER}" +
        "Wohnung${LIST_DELIMITER}Gebäude${FIELD_DELIMITER}easy${FIELD_DELIMITER}common${FIELD_DELIMITER}" +
        "Sounds like English house${FIELD_DELIMITER}Nominativ neuter\n" +
        "kommen${FIELD_DELIMITER}to come${FIELD_DELIMITER}${FIELD_DELIMITER}Ich komme aus Berlin.${FIELD_DELIMITER}verb${FIELD_DELIMITER}" +
        "${FIELD_DELIMITER}I come from Berlin.${FIELD_DELIMITER}to come${FIELD_DELIMITER}ˈkɔmən${FIELD_DELIMITER}" +
        "gehen${LIST_DELIMITER}ankommen${FIELD_DELIMITER}easy${FIELD_DELIMITER}common${FIELD_DELIMITER}" +
        "Movement towards speaker${FIELD_DELIMITER}Präsens first person singular\n" +
        "(notice: exactly 2 lines total, nothing before or between them, each one continuous line " +
        "with no internal line breaks, no numbering, no field names - match this shape exactly.)"

/** On-device vocabulary extraction (04_AI_PROMPTS.md §2), ported from `VocabularyExtractionWorker`.
 * Returns the parsed items; the repository writes them + dedups. A parse failure retries up to
 * [maxAttempts]; a transport/key failure returns immediately. [nounLexicon], if given, backfills
 * gender/plural from the bundled German noun dataset when the model left them null - a correction/
 * completion pass on the AI's own output, not a pre-filter (the dataset has no translations,
 * examples, or mnemonics, so full AI enrichment still runs for every word regardless). */
class VocabularyExtractor(
    private val client: AnthropicClient,
    private val maxAttempts: Int = 3,
    private val nounLexicon: GermanNounLexicon? = null,
) {
    suspend fun extract(
        goalText: String,
        lessonText: String,
        aiInstructions: String?,
        nativeLanguage: NativeLanguage,
        onUsage: ((inputTokens: Int, outputTokens: Int) -> Unit)? = null,
    ): ApiResult<List<ExtractedVocabItem>> {
        val system = SYSTEM_PREFIX.replace("%GOAL%", goalText).replace("%NATIVE_LANGUAGE%", nativeLanguage.aiPromptName) +
            (aiInstructions?.trim()?.takeIf { it.isNotEmpty() }?.let { AI_INSTRUCTIONS_SUFFIX + it } ?: "")

        var lastFailure: ApiResult.Failure? = null
        repeat(maxAttempts) { attempt ->
            when (val result = client.complete(AnthropicModels.SMALL, 8000, system, "Lesson content:\n$lessonText", onUsage)) {
                is ApiResult.Failure -> lastFailure = result
                is ApiResult.Success -> {
                    val parsed = parseCompactVocabLines(result.data)
                    if (parsed != null) return ApiResult.Success(enrichFromLexicon(parsed))
                    // parsed == null: the response wasn't usable at all (no lines, or the count line
                    // itself wasn't readable) - retry the call, but log it so a silent all-attempts-fail
                    // doesn't go unnoticed. A response with a valid count but some malformed data lines
                    // does NOT hit this path - parseCompactVocabLines already drops just those lines.
                    EvolaLog.d("vocab-extract", "attempt ${attempt + 1}/$maxAttempts: failed to parse response (chars=${result.data.length}): ${result.data.take(200)}")
                }
            }
        }
        return lastFailure ?: ApiResult.Success(emptyList())
    }

    private fun enrichFromLexicon(items: List<ExtractedVocabItem>): List<ExtractedVocabItem> {
        val lexicon = nounLexicon ?: return items
        return items.map { item ->
            // A lookup miss covers both "not a noun in this dataset" and "the one-time import
            // hasn't finished yet" - either way, fall back to the AI's own fields rather than
            // blocking or failing this extraction over an optional correction pass.
            val entry = runCatching { lexicon.lookup(item.term) }.getOrNull() ?: return@map item
            item.copy(
                gender = item.gender ?: entry.genus,
                plural = item.plural ?: entry.nominativPlural,
            )
        }
    }
}

/** Parses the model's compact pipe-delimited response (see the "Output format" instructions in
 * [SYSTEM_PREFIX]). Deliberately does NOT anchor on "line 1 is the declared count, everything
 * after is data" - in practice the model sometimes wraps the data in narration ("Let me extract
 * the vocabulary...", a bullet-point preview, "Let me continue with the remaining words:") despite
 * being told not to, which would break a strict line-1-is-the-count parse even though the actual
 * data lines further down are perfectly well-formed. Instead, every line in the response is tried
 * independently: a line that splits into exactly [FIELD_ORDER].size pipe-delimited fields with a
 * non-empty term/meaning is kept as an item; anything else (prose, headers, a stray count number,
 * blank lines) is silently ignored, not logged - narration lines are expected often enough now
 * that logging each one would just be noise. A duplicate term appearing twice in one response
 * (seen when the model re-emits the whole list) is caught downstream by the existing dedup against
 * already-known terms, same as a duplicate arriving from a different lesson. Returns null only
 * when literally nothing in the response parsed as a data line, so the caller retries. */
private fun parseCompactVocabLines(text: String): List<ExtractedVocabItem>? {
    val lines = normalizeModelJson(text).lines().map { it.trim() }.filter { it.isNotEmpty() }
    val items = lines.mapNotNull { line -> line.toVocabItemOrNull() }
    return items.ifEmpty { null }
}

private fun String.toVocabItemOrNull(): ExtractedVocabItem? {
    val fields = split(FIELD_DELIMITER)
    if (fields.size != FIELD_ORDER.size) return null
    fun field(index: Int): String? = fields[index].trim().ifEmpty { null }

    val term = field(0) ?: return null
    val meaning = field(1) ?: return null
    val gender = field(2)
    return ExtractedVocabItem(
        term = stripLeadingArticle(term, gender),
        meaning = meaning,
        gender = gender,
        exampleSentence = field(3),
        partOfSpeech = field(4),
        plural = field(5),
        exampleSentenceTranslation = field(6),
        nativeMeaning = field(7),
        ipaPronunciation = field(8),
        relatedWords = field(9)?.split(LIST_DELIMITER)?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList(),
        difficultyRating = field(10),
        frequencyRating = field(11),
        memoryTip = field(12),
        grammarNote = field(13),
    )
}

/** Defends against the model occasionally folding the article into [term] itself (e.g. term="Der
 * Hund", gender="der") despite the prompt saying not to - callers that prepend gender to term for
 * display would otherwise render it twice ("Der Der Hund"). */
private fun stripLeadingArticle(term: String, gender: String?): String {
    if (gender.isNullOrBlank()) return term
    val prefix = "$gender "
    return if (term.startsWith(prefix, ignoreCase = true)) term.substring(prefix.length) else term
}
