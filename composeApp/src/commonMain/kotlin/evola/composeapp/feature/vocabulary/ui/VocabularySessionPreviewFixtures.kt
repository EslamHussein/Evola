package evola.composeapp.feature.vocabulary.ui

import evola.shared.feature.vocabulary.domain.VocabularyCard
import evola.shared.feature.vocabulary.domain.VocabularySessionState
import evola.shared.feature.vocabulary.domain.VocabularySessionSummary

internal val fakeNewCard = VocabularyCard.New(
    itemId = "v1", term = "Hund", gender = "der", partOfSpeech = "noun", plural = "Hunde", ipaPronunciation = "/hʊnt/",
    meaning = "dog", exampleSentence = "Der Hund läuft schnell.", exampleSentenceTranslation = "The dog runs fast.",
    grammarNote = null, relatedWords = listOf("Welpe", "Katze"), difficultyRating = null, frequencyRating = null,
    memoryTip = null, isBookmarked = false, markedDifficult = false,
)

internal val fakePracticeCard = VocabularyCard.Practice(
    itemId = "v2", meaning = "house", grammarNote = null, exampleSentence = null, exampleSentenceTranslation = null,
    isBookmarked = false, markedDifficult = false, choices = listOf("das Haus", "die Straße", "der Baum", "das Auto"),
)

internal val fakeNewCardSession = VocabularySessionState(
    sessionId = "s1", sessionNumber = 1, cardsCompleted = 2, cardsRemaining = 5,
    card = fakeNewCard, origin = "new", wordIndex = 3, totalWords = 8,
)

internal val fakePracticeCardSession = VocabularySessionState(
    sessionId = "s1", sessionNumber = 1, cardsCompleted = 4, cardsRemaining = 3,
    card = fakePracticeCard, origin = "due_review", wordIndex = 5, totalWords = 8,
)

internal val fakeSessionSummary = VocabularySessionSummary(
    sessionNumber = 1, wordsLearned = 8, accuracy = 87.5, timeSeconds = 240, newWordsCount = 5, reviewWordsCount = 3,
)

/** Named (not anonymous) so its members stay resolvable from every preview file - an anonymous
 * `object { ... }`'s member types are only denotable within its own declaring file. */
internal class VocabularySessionFakeActions {
    val onDone: () -> Unit = {}
    val onUndo: (String) -> Unit = {}
    val onRetry: () -> Unit = {}
    val onStartNextSession: () -> Unit = {}
    val onMarkSwipeTutorialSeen: () -> Unit = {}
    val onAlreadyKnown: (String, String) -> Unit = { _, _ -> }
    val onStartLearning: (String, String) -> Unit = { _, _ -> }
    val onToggleBookmark: (String, Boolean) -> Unit = { _, _ -> }
    val onToggleDifficult: (String, Boolean) -> Unit = { _, _ -> }
    val onExplain: (String) -> Unit = {}
    val onSelfGrade: (String, String, Boolean) -> Unit = { _, _, _ -> }
    val onKeepShowing: (String, String) -> Unit = { _, _ -> }
    val onSelectChoice: (String, String, String) -> Unit = { _, _, _ -> }
    val onCheckTyped: (String, String, String) -> Unit = { _, _, _ -> }
    val onContinue: (String, VocabularySessionState?) -> Unit = { _, _ -> }
}

internal val fakeVocabularySessionActions = VocabularySessionFakeActions()
