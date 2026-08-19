# AI Token Reduction & Prompt Generality — Plan

Status tracker for the discussion in chat about cutting AI token cost in the extraction
pipeline and removing German-specific hardcoding from prompts that should be language-generic.
Nothing here is implemented yet — each item needs explicit go-ahead before work starts (see
"How this gets worked" below). Grounded in the current pipeline as of this writing:

- Text extraction is flattened plain text (PdfBox on Android, PDFKit on iOS), page breaks only,
  no heading/table structure preserved.
- `LessonSegmenter` is rule-based first (heading regex), falls back to AI (`SegmentationExtractor`,
  chunked at 16k chars) only when heuristics fail.
- `VocabularyExtractor` is the heaviest prompt (8000 max output tokens), JSON output, hardcodes
  "German" in a few places despite the rest of the app being language-parametrized.
- Prompt caching (`cache_control: ephemeral`) is already applied to every system prompt — so the
  *input* side of repeated calls is already discounted; the *output* side and any *new* system
  prompt content are not.

## Items

### 1. Compact output format (replace repeated-JSON-keys with a tabular/compact wire format)
**Why:** every vocab item repeats all 14 field names in JSON; that's ~250-300 uncacheable output
chars of pure structure per item, up to 40 items per lesson. Output tokens are never cached, so
this is the highest-leverage single change.
**Scope:** `VocabularyExtractor.kt` (prompt schema + parsing), possibly a small shared
encode/decode helper if we hand-roll a compact format instead of adopting `ktoon`.
**Decision (superseded — see below):** two `ktoon` library options were evaluated and both ruled out:
- `JosephSanjaya/ktoon` — real KMP (Android/iOS/Desktop/Web/WASM), but not on Maven Central (needs
  vendoring as a local module/submodule, no versioned releases).
- `com.lukelast.ktoon:ktoon:5.0.0` — on Maven Central with a genuinely full KMP target matrix
  (verified in its `build.gradle.kts`: `jvm`, `android`, `iosArm64`/`iosX64`/`iosSimulatorArm64`,
  `macos*`, `linux*`, `mingwX64`, `wasmJs`, etc. — the README undersold this). **Spiked and ruled
  out**: adding it to `shared/build.gradle.kts` and compiling `:shared:compileDebugKotlinAndroid`
  failed immediately — `Module was compiled with an incompatible version of Kotlin. The binary
  version of its metadata is 2.3.0, expected version is 2.1.0.` It also transitively pulls
  `kotlinx-serialization-core-jvm:1.11.0`, itself built against the same newer metadata, so this
  isn't fixable without bumping the whole project's Kotlin version — out of scope for this item.
  Same failure class as the Kermit 2.0.8 ABI mismatch hit earlier in this project; confirmed via a
  real compile spike (dependency added, compiled, failed, reverted), not just version-number reading.
**TOON as a wire format is not adopted via either library. Decision (confirmed in chat): hand-roll
a compact format** — no dependency, no ABI risk, full control over the parser. Since our schema is
a single fixed 14-field shape (not polymorphic like general TOON), we don't need a per-response
header naming fields — the client and prompt both already know the field order, so we only need a
count line + delimited data lines. Spec:
- Line 1: item count (integer) - the model commits to a count upfront (mirrors TOON's finding that
  explicit array-length declaration improves accuracy vs. leaving row count implicit).
- Lines 2..N+1: one item per line, exactly 14 fields joined by `|`, in the fixed order: term,
  meaning, gender, example_sentence, part_of_speech, plural, example_sentence_translation,
  native_meaning, ipa_pronunciation, related_words, difficulty_rating, frequency_rating,
  memory_tip, grammar_note.
- Null/absent optional field = empty string between delimiters (e.g. two consecutive `|` for a
  null gender).
- `related_words` (a 2-item list) joined with `;` inside its single field slot.
- Model instructed never to emit `|`, `;`, or a raw newline inside a field value.
- Parser is line-based, not a single monolithic parse: a malformed line is skipped and logged
  rather than failing the whole batch (a real reliability improvement over JSON, where one syntax
  error invalidates the entire response) - existing `maxAttempts` retry loop in
  `VocabularyExtractor.extract()` stays as the fallback if too few valid lines come back.
**Status:** ✅ implemented (`VocabularyExtractor.kt`) — compiles clean on both platforms. **Not yet
verified against a real AI call** — needs an actual on-device vocabulary extraction run (add a
material, let it process) to confirm the model reliably follows the new format and check the
real token-count difference, before calling this item done.

### 2. Language-generic prompt fixes (remove hardcoded "German")
**Why:** `VocabularyExtractor.kt` line 86 literally says "2 related German words" regardless of
the learner's actual target language; a few other passages use German as the running example
rather than a true conditional. Correctness issue, not primarily a token issue — low token impact
since it's cached input, but worth bundling since the file is already being touched for #1.
**Scope:** `VocabularyExtractor.kt` prompt text only — introduce a `%TARGET_LANGUAGE%` placeholder
parallel to the existing `%NATIVE_LANGUAGE%`, rewrite the gendered-article guidance and examples
as genuinely conditional rather than German-flavored.
**Status:** ⬜ not started — pending confirmation.

### 3. Structured text extraction (PDF → Markdown-ish structure instead of flattened plain text)
**Why:** flattened text is why `LessonSegmenter` needs AI fallback for boundary detection, and why
`VocabularyExtractor`'s prompt has to spend real estate explaining how to detect a term/translation
pairing that "collapsed into plain sequential lines." Preserving heading/table structure at
extraction time would make both more reliable and likely cut AI-segmentation-fallback calls.
**Scope:** `FileTextExtractor.android.kt` (PdfBox) and `FileTextExtractor.ios.kt` (PDFKit) — both
platform-specific, nontrivial (neither library gives semantic markup for free, both are text +
position extraction). Downstream: `LessonSegmenter`'s heading regex and `VocabularyExtractor`'s
pairing-detection instructions could likely simplify once real structure exists.
**Status:** ⬜ not started — pending confirmation.

### 5. Vocabulary pipeline redesign — comprehensive extraction + word-level dedup across lessons
**Decided in chat (supersedes/extends #1 and #4 below):**
- Extraction becomes comprehensive (all German words in the lesson text), not the current
  judgment-call/capped selection.
- Dedup moves from post-hoc (today: AI fully enriches a word, then the app discards it if it's a
  duplicate - wasted output tokens) to a two-phase pipeline: **Phase A** cheap candidate pass
  (term + gender + part_of_speech only, comprehensive) → **Phase B** local dedup against
  `existingTerms` (already exists in `LocalMaterialsRepository.kt:267-336`, just moves earlier) →
  **Phase C** full 14-field enrichment, only for words that survive dedup. Chosen over "single call
  with an exclusion list of known terms in the prompt" because that list would grow unboundedly
  over months of use and become its own real input-token cost; the two-phase pipeline never sends
  the existing-vocab list to the AI at all, dedup stays a free local DB/set operation.
- **Schema change required**: a word that already exists (from any other material/lesson) should
  be *linked* to the new lesson, not silently dropped as it is today. `vocabulary_items.lesson_id`
  is currently a required single FK (one word = one owning lesson,
  `shared/src/commonMain/sqldelight/evola/shared/db/Vocabulary.sq:3`) - needs a new
  `lesson_vocabulary_items` many-to-many join table. `vocabulary_progress` already keys off
  `vocabulary_item_id` (not `lesson_id`), so SRS/progress tracking is already word-level, not
  lesson-level - no conflict there. Queries that currently filter `vocabulary_items` directly by
  `lesson_id` (`itemsByLesson`, `itemsWithProgressByLesson`, `statusByLesson`, `newItemsForLesson`,
  `dueItemsInLesson`) need to join through the new table instead.
- **Migration approach confirmed: fresh DB is acceptable** (pre-launch, single local user, no
  production data at stake) - no migration script needed for existing installs.

**Phase A implementation research (this session):** searched for a KMP-compatible German
lemmatizer/POS-tagger library to run Phase A locally with zero AI cost. **None exists** - all real
tools found (HanTa, GermaLemma, DEMorphy) are Python-only; the closest Kotlin-adjacent options are
JVM-only Java NLP libraries, which would break iOS `commonMain` support entirely. The one viable
data-only option is a bundled lemma lookup table -
[michmech/lemmatization-lists](https://github.com/michmech/lemmatization-lists) has a German
token→lemma list (358,473 pairs, Open Database License) that could ship as a static asset and be
queried with pure Kotlin (genuinely commonMain-safe, no platform API needed). But this alone
doesn't solve Phase A: it gives lemmas, not part-of-speech or German noun gender (der/die/das) -
those need separate datasets we haven't located yet - plus real questions on bundle size, coverage
of out-of-vocabulary words (proper nouns, compounds), and a fallback path for misses.

**ODbL license check (this session):** confirmed via the actual license text (not just the
summary) that bundling a filtered/subset copy of the raw lemma data inside the app counts as
creating a "Derivative Database" (ODbL §1.0, §4.4(b)) - not the lighter "Produced Work" category -
because it's "Extracting or Re-utilising... a Substantial part of the Contents into a new
database." That triggers full attribution + share-alike: Evola would need to (a) credit ODbL + the
original source publicly (e.g. an About/Credits screen) and (b) make the specific bundled dataset
itself available under ODbL too (does NOT require Evola's own source code to be open - only that
data file). This is a real but bounded, satisfiable obligation, not a blocker - not yet decided
whether to accept it or look for a more permissively-licensed word list instead.

This is
real feature-building work, not a quick dependency spike like the `ktoon` check - **not yet decided
whether to build it now or start Phase A as a cheap AI call and revisit local lemmatization later.**

**Phase A dataset decided (this session):** evaluated German-specific licensed word lists
(`lemmatization-lists`/ODbL-1.0 - lemma only, no POS/gender; `german-nouns`/CC-BY-SA-4.0 - lemma +
genus + POS + full flexion, nouns only). Chose **`gambolputty/german-nouns`
(CC-BY-SA-4.0)**, bundled **verbatim/unfiltered** (all 78 columns, not just the 3 needed today) per
explicit direction - keeping every column both preserves data for future use and, since no content
is modified, keeps this a plain redistribution rather than "Adapted Material" under CC-BY-SA (only
attribution required, not a separate published derivative). Real numbers from the actual downloaded
file: 102,445 rows, 20.19MB raw. Nouns-only limitation accepted; non-noun POS/gender will fall back
to AI or the (free, reliable for German specifically) capitalization-implies-noun heuristic - not
yet implemented.

**Implemented (this session):**
- `composeApp/src/commonMain/composeResources/files/german_nouns.csv` - the bundled dataset.
- `shared/src/commonMain/kotlin/evola/shared/vocabulary/GermanNounLexicon.kt` - `GermanNounEntry`
  (lemma/partOfSpeech/genus + full raw row for future columns) + `GermanNounLexicon` (lemma→entry
  map, case-insensitive lookup, custom CSV row parser handling quoted commas). Compiles clean on
  both platforms.
- `composeApp/.../di/AppModule.kt` - `germanNounLexicon: Deferred<GermanNounLexicon>`, parsing
  started immediately in the background (`extractionScope.async`) rather than lazily on first use,
  so it's likely ready by the time real extraction needs it. **Not yet wired into
  `VocabularyExtractor`/`LocalMaterialsRepository`'s actual pipeline** - that's the Phase A/B/C
  rewrite itself, still to do.
- `composeApp/.../main/ProfileScreen.kt` - Credits section with the required CC-BY-SA-4.0
  attribution, added now since the dataset already ships inside the app binary once bundled,
  independent of whether the lookup feature built on it is finished.

**Schema migration implemented (this session):**
- `shared/src/commonMain/sqldelight/evola/shared/db/Vocabulary.sq` - new `lesson_vocabulary_items`
  join table (lesson_id, vocabulary_item_id) + a `lesson_vocabulary` VIEW that UNIONs origin
  (`vocabulary_items.lesson_id`) with linked appearances, so every lesson-scoped query (`itemsByLesson`,
  `itemsWithProgressByLesson`, `statusByLesson`, `wordStatusesByGoal`, `inProgressWordsByGoal`,
  `newItemsForLesson`, `dueItemsInLesson`, `dueItemsElsewhere`) joins through the view once instead
  of each needing its own UNION. New `linkItemToLesson` query. `wordStatusesByGoal`/
  `inProgressWordsByGoal` gained `DISTINCT` (a word linked into 2+ lessons of the same goal would
  otherwise double-count in Home's dashboard).
- **Accepted v1 semantics**: `vocabulary_items.lesson_id` keeps `ON DELETE CASCADE` as the origin -
  deleting the origin lesson deletes the word entirely, including every lesson it was linked to via
  `lesson_vocabulary_items` (also cascades). No reference-counted "only delete when the last
  linking lesson is gone" logic - simpler, and reversible later if it proves to be the wrong call.
- `shared/src/commonMain/kotlin/evola/shared/local/LocalMaterialsRepository.kt` -
  `extractVocabulary`'s dedup set is now `MutableMap<String, String>` (lowercase term → existing
  item id, was `MutableSet<String>`); a duplicate now calls `linkItemToLesson(lessonId,
  existingItemId)` instead of silently skipping. This is the actual "skip but link" behavior
  requested - implemented and compiling, **not yet verified on-device** (needs a real
  material-processing run with genuinely repeated vocabulary across lessons to confirm the link
  shows up correctly in both lessons' vocab lists).
- Compiles clean on both platforms (`:shared` and `:composeApp`, Android + iOS).

**Comprehensive-extraction prompt implemented (this session):** `VocabularyExtractor.kt`'s
`SYSTEM_PREFIX` no longer caps at "15-30 items / up to 40 for paired lists" - now instructs
"extract EVERY genuine vocabulary word... not a target count," while still excluding bare function
words (unchanged rule) so this doesn't degrade into "every token including der/und/ist."

**Full local-lexicon hybrid Phase A (tokenize text → resolve nouns for free via inflected-form
lookup → AI only for unresolved words) descoped for now - real reason, not just deferred:**
building the inflected-form reverse index (indexing all ~75 flexion columns per row, not just
`lemma`) was evaluated and set aside because, worst case, ~102k rows × up to ~75 non-empty flexion
columns each is on the order of millions of map entries - a real risk of a slow/memory-heavy parse
on every cold start that hasn't been profiled on an actual device, and getting that wrong is worse
than not having it. **What shipped instead, at much lower risk:** `GermanNounLexicon.lookup()`
stays a simple lemma-only index, used as a **post-processing correction/completion pass** on the
AI's own response (`VocabularyExtractor.enrichFromLexicon`) - backfills `gender`/`plural` from the
dataset when the model left them null and the term matches a known lemma exactly. Safe by
construction: lexicon-not-ready or lookup-miss both just fall through to the AI's own fields
unchanged, no blocking, no crash path. This does NOT reduce Phase-A/candidate-identification AI
cost the way full tokenize-and-resolve would have - it's a quality/accuracy improvement (more
reliable gender data) riding on data already bundled, not a further token-cost cut. A true
tokenizer + inflected-form resolver remains a real future option if the app ever needs the
additional token savings and there's room to profile it properly first.

**Wiring**: `AppModule.kt` now passes `germanNounLexicon` into `VocabularyExtractor`'s new optional
`nounLexicon: Deferred<GermanNounLexicon>?` constructor param.

**Lexicon storage redesigned - DB-backed, not in-memory (this session, user-driven):** the original
`GermanNounLexicon.parse()` re-parsed the full 20MB CSV into an in-memory `Map` on every single app
launch. Replaced with a one-time import into a new `german_nouns` SQLite table
(`shared/.../sqldelight/evola/shared/db/GermanNouns.sq`) - `GermanNounImporter.importIfNeeded()`
checks the row count first and no-ops on every launch after the first; `GermanNounLexicon.lookup()`
is now a single indexed SQLite query per call instead of holding the whole dataset in memory.
Batched inserts (2000/transaction) to keep the one-time import fast. Progress is surfaced via
`GermanNounImportState` (NotStarted/InProgress/Done), exposed from `AppModule` as a `StateFlow`.

**First-run progress UI implemented**: `VocabDataImportScreen.kt` (determinate
`LinearProgressIndicator`, real row count, license line) wired into `App.kt`'s screen flow as a new
`AppScreen.VocabDataImport` state - only reached on the very first launch (or after a future
dataset update clears the table); every later launch skips straight past it since the row-count
check is near-instant. Mockup shown to and approved by the user before implementation.

**Status:** 🟡 in progress — all pieces compile clean on both platforms
(`:shared` + `:composeApp`, Android + iOS): dataset infrastructure (now DB-backed), schema/
link-not-skip behavior, comprehensive-extraction prompt, the lexicon correction pass, and the
first-run import progress screen. **Not yet verified on a real device** - needs an actual fresh
install (to see the import screen fire for real) and a real material-processing run (to verify the
AI prompt/parsing/enrichment chain end to end) before being called done. Supersedes item #1's
already-implemented compact format (still in use, now carrying comprehensive-not-capped output)
and folds in item #4 below.

### 4. Lexical pre-filter for vocabulary candidate selection (folded into #5 above)
**Why:** the AI currently does two jobs in one call — deciding *which* words are vocabulary-worthy
(judgment call over prose) and *enriching* each chosen word. Splitting these (local non-AI
candidate proposal, then AI only enriches) could shrink both the system prompt (less judgment-call
prose) and the output (tighter, more predictable list).
**Scope:** biggest architectural change of the four — needs a tokenizer/stopword approach per
target language (not just German), needs to preserve the existing paired-vocabulary-list detection
(AI still needed there, a lexical filter can't replicate document-structure pattern matching), and
carries real quality-regression risk if the filter excludes something the AI would have caught.
**Status:** ⬜ not started — pending confirmation. Treat as a prototype/spike before committing,
given the uncertain payoff-to-effort ratio relative to #1-#3.

## How this gets worked

Going through the list one item at a time, in the order above (#1 → #2 → #3 → #4) unless told
otherwise. For each: confirm scope and any open questions in chat first, implement, verify
(compile both platforms, build + install to device per the usual workflow), then move to the next
item. Nothing in #2-#4 starts until the previous item is confirmed done or explicitly deferred.
