package evola.shared.feature.vocabulary.domain

import evola.shared.core.common.ApiResult

/** A lesson's own vocabulary item plus this user's current SRS status (Lingvist-style flat-queue
 * engine). [status] is one of [VocabularySrs.STATUSES]. */
data class VocabularyItem(
    val itemId: String,
    val term: String,
    val meaning: String,
    val gender: String? = null,
    val exampleSentence: String? = null,
    val exampleSentenceTranslation: String? = null,
    val partOfSpeech: String? = null,
    val plural: String? = null,
    val status: String,
    val nativeMeaning: String? = null,
    val ipaPronunciation: String? = null,
    val relatedWords: List<String> = emptyList(),
    val difficultyRating: String? = null,
    val frequencyRating: String? = null,
    val memoryTip: String? = null,
    val grammarNote: String? = null,
    val isBookmarked: Boolean = false,
    val markedDifficult: Boolean = false,
)

/** The two card types the session queue can hand back - a Reword-style swipe model rather than a
 * forced ladder. A brand-new word is always [New] first: swipe left ("I already know this") fast-
 * tracks it straight into the review schedule via [VocabularyRepository.submitAlreadyKnown]; swipe
 * right ("Start learning this word") calls [VocabularyRepository.submitStartLearning] and re-queues
 * it later in the same session as a [Practice] card. A [Practice] card covers both "still learning"
 * and "due for review" - which one it is is carried by [VocabularySessionState.origin]
 * (`"due_review"` vs `"new"`/`"repeat"`), driving which pair of swipe labels the UI shows. Every
 * [Practice] card can be graded three ways: a plain self-reported swipe
 * ([VocabularyRepository.submitSelfGrade] for "Got it"/"I've memorized it", or
 * [VocabularyRepository.submitKeepShowing] for "Missed it"/"Keep showing" - deliberately NOT the
 * same call, since a due-review miss demotes the word's SRS state but "keep showing" on a still-new
 * word does not), a typed check ([VocabularyRepository.submitTyped]), or a multiple-choice check
 * ([VocabularyRepository.submitChoice] against [Practice.choices]). No answer/term field is ever
 * exposed before grading - the reveal (correct term + completed sentence) only comes back from the
 * submit call's result. */
sealed interface VocabularyCard {
    val itemId: String

    /** A word never seen before. Carries the full content (image-equivalent context: example
     * sentence, AI explanation, related words, etc.) since Evola's content model is richer than a
     * bare headword+translation. No recall is required here - it's a decide-not-grade card. */
    data class New(
        override val itemId: String,
        val term: String,
        val gender: String?,
        val partOfSpeech: String?,
        val plural: String?,
        val ipaPronunciation: String?,
        val meaning: String,
        val exampleSentence: String?,
        val exampleSentenceTranslation: String?,
        val grammarNote: String?,
        val relatedWords: List<String>,
        val difficultyRating: String?,
        val frequencyRating: String?,
        val memoryTip: String?,
        val isBookmarked: Boolean,
        val markedDifficult: Boolean,
        /** null until the learner taps "AI explain"; populated (and persisted) by
         * [VocabularyRepository.explainItem] on first request. */
        val aiExplanation: String? = null,
    ) : VocabularyCard

    /** A word being actively learned or due for review - the native-language meaning is the
     * prompt, recall of the term is graded by swipe, typed input, or multiple choice. [choices]
     * (4 options, persisted at queue-insert time so a resumed session doesn't reshuffle) is always
     * populated, ready for the UI's multiple-choice icon - the plain swipe and typed paths simply
     * ignore it. */
    data class Practice(
        override val itemId: String,
        val meaning: String,
        val grammarNote: String?,
        val exampleSentence: String?,
        val exampleSentenceTranslation: String?,
        val isBookmarked: Boolean,
        val markedDifficult: Boolean,
        val choices: List<String>,
    ) : VocabularyCard
}

/** Current position in the session queue. Exiting mid-card is always safe - the queue durably
 * tracks position, so [VocabularyRepository.startOrResumeSession] always resumes exactly where the
 * user left off. [origin] is "new" (walking its own difficulty ladder), "due_review" (skipped
 * straight to Blind), or "repeat" (re-queued after a wrong answer) - drives whether the UI shows a
 * per-word ladder step label or a plain "Review" pill. */
data class VocabularySessionState(
    val sessionId: String,
    val sessionNumber: Int,
    val cardsCompleted: Int,
    val cardsRemaining: Int,
    val card: VocabularyCard,
    val origin: String,
    /** 1-based position of the current word among the session's distinct words (a word can be
     * re-queued several times as a [VocabularyCard.Practice] card but counts as one step here), and
     * the session's total distinct word count. Drives a "word 3 of 5" readout. */
    val wordIndex: Int,
    val totalWords: Int,
)

/** [correct] is null for [VocabularyCard.New] and for [VocabularyRepository.submitKeepShowing]
 * (neither is ever graded). [correctAnswer] is the correct term, populated regardless of right/wrong
 * so the UI can always reveal it (null only where there's nothing to reveal, e.g. a plain
 * self-graded swipe). [completedSentence] is populated when the word has an example sentence.
 * [next] is null once the queue is exhausted - the caller should then call
 * [VocabularyRepository.complete]. */
data class VocabularyAnswerResult(
    val correct: Boolean?,
    val correctAnswer: String? = null,
    val completedSentence: String? = null,
    val next: VocabularySessionState?,
    /** True exactly on the answer that moves this word's status into "mastered" for the first time
     * this transition - drives a one-off celebration toast in the session screen. Never true for
     * [VocabularyRepository.submitAlreadyKnown]/[submitKeepShowing], which don't graduate a word all
     * the way to mastered themselves. */
    val justMastered: Boolean = false,
)

data class VocabularySessionSummary(
    val sessionNumber: Int,
    val wordsLearned: Int,
    val accuracy: Double,
    val timeSeconds: Long,
    val newWordsCount: Int,
    val reviewWordsCount: Int,
)

/** Home's red/yellow/green word-breakdown buckets (see [evola.shared.goals.VocabularyBreakdown]),
 * reused here to pick which words a "practice this category" session pulls in. */
enum class WordCategory { STRUGGLING, LEARNING, MASTERED }

/** Reword's Home three-way session split ("Learn new words" / "Review words" / "Mixed mode") -
 * goal-wide (every lesson in the goal), unlike [VocabularyRepository.startOrResumeSession] which is
 * a single lesson. [MIXED] is exactly [startOrResumeSession]'s own due+new selection logic, just
 * pulling from the whole goal instead of one lesson. */
enum class SessionMode { NEW_ONLY, REVIEW_ONLY, MIXED }

/** Reword's "Import words" file format: one word per line, comma-separated `term,meaning` or
 * `term,meaning,native meaning`. Blank lines are skipped; a line with fewer than 2 columns is
 * skipped too (silently, matching [VocabularyRepository.importWords]'s own per-row tolerance)
 * rather than failing the whole file over one bad line. No CSV-quoting support (no field is ever
 * expected to contain a literal comma) - kept intentionally simple for the format this is actually
 * for, not a general-purpose CSV parser. */
fun parseWordCsv(text: String): List<Triple<String, String, String?>> =
    text.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .mapNotNull { line ->
            val columns = line.split(",").map { it.trim() }
            if (columns.size < 2 || columns[0].isEmpty() || columns[1].isEmpty()) null
            else Triple(columns[0], columns[1], columns.getOrNull(2)?.ifBlank { null })
        }
        .toList()

interface VocabularyRepository {
    suspend fun startOrResumeSession(lessonId: String): ApiResult<VocabularySessionState>

    /** A one-off practice session (not resumable, always starts fresh) pulling up to [limit] words
     * from [category] across every lesson in the goal - unlike [startOrResumeSession], which is
     * scoped to a single lesson. Each word is a single [VocabularyCard.Practice] card (same as a
     * due review). Fails if the category is empty. */
    suspend fun startCategorySession(goalId: String, category: WordCategory, limit: Int = 10): ApiResult<VocabularySessionState>

    /** Reword's Home "Learn new words"/"Review words"/"Mixed mode" rows - see [SessionMode]'s own
     * doc comment. Also goal-wide and one-off, same shape as [startCategorySession]. */
    suspend fun startModeSession(goalId: String, mode: SessionMode): ApiResult<VocabularySessionState>
    suspend fun listVocabulary(lessonId: String): ApiResult<List<VocabularyItem>>

    /** [VocabularyCard.New] swipe left - "I already know this word". Fast-tracks the word straight
     * into the review schedule without ever showing a [VocabularyCard.Practice] card for it. */
    suspend fun submitAlreadyKnown(sessionId: String, itemId: String): ApiResult<VocabularyAnswerResult>

    /** [VocabularyCard.New] swipe right - "Start learning this word". Re-queues it as a
     * [VocabularyCard.Practice] card later in the same session. */
    suspend fun submitStartLearning(sessionId: String, itemId: String): ApiResult<VocabularyAnswerResult>

    /** [VocabularyCard.Practice] plain swipe, self-reported (no objective check): "Got it" for a due
     * review, or "I've memorized it" for a still-learning word - both mean the same thing to the SRS
     * engine. Use [submitKeepShowing] instead for the non-graded "not ready yet" swipe. */
    suspend fun submitSelfGrade(sessionId: String, itemId: String, correct: Boolean): ApiResult<VocabularyAnswerResult>

    /** [VocabularyCard.Practice] swipe right on a still-learning (not yet due-for-review) word -
     * "Keep showing this word". Deliberately does not touch the word's SRS state at all (unlike a
     * due-review "Missed it", which goes through [submitSelfGrade]); it only re-queues the card
     * later in the session. */
    suspend fun submitKeepShowing(sessionId: String, itemId: String): ApiResult<VocabularyAnswerResult>

    /** For [VocabularyCard.Practice] when a multiple-choice check is active - the tapped option. */
    suspend fun submitChoice(sessionId: String, itemId: String, selectedChoice: String): ApiResult<VocabularyAnswerResult>

    /** For [VocabularyCard.Practice] when a typed check is active - the typed response. */
    suspend fun submitTyped(sessionId: String, itemId: String, response: String): ApiResult<VocabularyAnswerResult>

    /** Reword's per-card undo - reverts the most recent graded [Practice] answer
     * ([submitSelfGrade]/[submitChoice]/[submitTyped] only; [submitAlreadyKnown]/[submitStartLearning]/
     * [submitKeepShowing] aren't covered by this pass) back to its pre-grade state: the word's SRS
     * progress row, the queue row's answered/correct/response fields, and any repeat row the grade
     * inserted are all rolled back, and the card becomes current again. In-memory only (not
     * DB-persisted) - available only for the single most recent grade in this process's lifetime,
     * cleared once used or once a different card is graded. Returns Success(null) as a safe no-op
     * when there's nothing to undo, never a Failure for that case. */
    suspend fun undoLastGrade(sessionId: String): ApiResult<VocabularySessionState?>
    suspend fun complete(sessionId: String, localDate: String): ApiResult<VocabularySessionSummary>
    suspend fun updateFlags(itemId: String, isBookmarked: Boolean? = null, markedDifficult: Boolean? = null): ApiResult<VocabularyItem>

    /** Edits the extracted term/meaning directly - lets the learner fix an AI extraction mistake. */
    suspend fun updateItem(itemId: String, term: String, meaning: String, nativeMeaning: String?): ApiResult<VocabularyItem>

    /** The Intro card's "AI explain" toggle: a short plain-language note on the word, generated
     * once on first request and persisted so re-viewing the card never re-calls the model. */
    suspend fun explainItem(itemId: String): ApiResult<String>

    /** Standalone equivalent of a New card's "I already know this" swipe (see [submitAlreadyKnown]),
     * reachable from the Vocabulary list's word-detail sheet rather than mid-session - fast-tracks
     * the word straight into the review schedule the same way, no session/queue involved. */
    suspend fun markAlreadyKnown(itemId: String): ApiResult<VocabularyItem>

    /** Reword's "Copy to Eigene Vokabeln" - copies this word's term/meaning/native meaning into
     * [goalId]'s single "My Words" lesson (created on first use), starting fresh at "unseen". The
     * original word and its own progress are untouched; this creates an independent second item. */
    suspend fun copyToPersonalList(goalId: String, itemId: String): ApiResult<VocabularyItem>

    /** Manually adds a word straight into [lessonId] (no AI extraction call) - Evola's content is
     * lesson-scoped rather than pre-loaded decks, so a Reword-style "add your own word" lands in
     * whichever lesson the learner is already viewing, starting at "unseen" like any other word. */
    suspend fun addCustomWord(lessonId: String, term: String, meaning: String, nativeMeaning: String?): ApiResult<VocabularyItem>

    /** Reword's "Import words" - bulk version of [addCustomWord], one row per (term, meaning,
     * native meaning) triple, all inserted in a single transaction. Blank term/meaning rows are
     * skipped rather than failing the whole import. Returns the count actually inserted. */
    suspend fun importWords(lessonId: String, rows: List<Triple<String, String, String?>>): ApiResult<Int>

    /** Reword's word-detail "Remove" - permanently deletes the word and its progress row. Cascades
     * via the DB schema's own foreign key, not a separate query. */
    suspend fun deleteItem(itemId: String): ApiResult<Unit>

    /** Reword's per-category "Reset progress" - every word in [lessonId] goes back to unseen, SRS
     * state cleared. Bookmarks/difficulty flags are untouched. */
    suspend fun resetLessonProgress(lessonId: String): ApiResult<Unit>

    /** Reword's Menu "Reset all progress" - every word for this user, across every lesson, goes
     * back to unseen. Destructive; the caller is expected to confirm with the user first. */
    suspend fun resetAllProgress(): ApiResult<Unit>

    /** Reword's onboarding level/lesson picker, adapted to this app's lesson-scoped content model -
     * see [StarterLevel]'s own doc comment. The caller (which already has the loaded [StarterLevel]/
     * [StarterLesson] data from its own JSON asset read) passes the lesson's own title and words
     * directly - the repository has no static copy of the starter content to look up by id, since
     * that content now lives in bundled JSON assets read at the composeApp layer, not hardcoded
     * Kotlin. Creates one new ordinary lesson (never merges into an existing one, even if called
     * twice) pre-seeded with [words], same as any other lesson from that point on. */
    suspend fun createStarterLesson(goalId: String, lessonTitle: String, words: List<StarterWord>): ApiResult<Unit>
}
