package evola.presentation.telegram

import evola.core.application.UseCase
import evola.core.kernel.DomainResult
import evola.core.kernel.LearnerId
import evola.exercisegeneration.application.GenerateExerciseForWordQuery
import evola.integrations.aigateway.GeneratedExercise
import evola.learneridentity.application.RegisterLearnerCommand
import evola.learneridentity.domain.Channel
import evola.vocabulary.application.AddVocabularyItemCommand
import evola.vocabulary.application.AddVocabularyItemResult
import evola.vocabulary.application.DueReviewItem
import evola.vocabulary.application.GetDueReviewsQuery
import evola.vocabulary.application.SubmitReviewAnswerCommand
import evola.vocabulary.application.SubmitReviewAnswerResult
import java.util.concurrent.ConcurrentHashMap

private data class PendingReview(val current: DueReviewItem, val remaining: List<DueReviewItem>)

/**
 * Telegram adapter. Every piece of tutoring logic below is a Command/Query dispatched to the
 * Application layer — the same contracts a future Android/Web client would use through
 * client-api. The only state kept here is [pendingReviews], the ephemeral per-chat "which review
 * is this chat mid-answering" map — permitted UI dialog-flow state (ADR D2), not tutoring logic.
 */
class TelegramBotRunner(
    private val telegramClient: TelegramClient,
    private val registerLearnerHandler: UseCase<RegisterLearnerCommand, LearnerId>,
    private val addVocabularyItemHandler: UseCase<AddVocabularyItemCommand, DomainResult<AddVocabularyItemResult>>,
    private val getDueReviewsHandler: UseCase<GetDueReviewsQuery, List<DueReviewItem>>,
    private val submitReviewAnswerHandler: UseCase<SubmitReviewAnswerCommand, DomainResult<SubmitReviewAnswerResult>>,
    private val generateExerciseHandler: UseCase<GenerateExerciseForWordQuery, GeneratedExercise>,
) {
    private val pendingReviews = ConcurrentHashMap<Long, PendingReview>()

    suspend fun run() {
        var offset: Long? = null
        while (true) {
            val updates = runCatching { telegramClient.getUpdates(offset) }.getOrDefault(emptyList())
            for (update in updates) {
                offset = update.updateId + 1
                runCatching { handleUpdate(update) }.onFailure { it.printStackTrace() }
            }
        }
    }

    private suspend fun handleUpdate(update: TelegramUpdate) {
        val message = update.message ?: return
        val text = message.text?.trim().orEmpty()
        if (text.isEmpty()) return

        val chatId = message.chat.id
        val telegramUserId = (message.from?.id ?: chatId).toString()
        val displayName = message.from?.username ?: message.from?.firstName

        val learnerId = registerLearnerHandler.handle(
            RegisterLearnerCommand(Channel.TELEGRAM, telegramUserId, displayName),
        )

        when {
            text.equals("/start", ignoreCase = true) ->
                telegramClient.sendMessage(
                    chatId,
                    "Willkommen bei Evola! Nutze /learn für ein neues Wort oder /review für fällige Wiederholungen.",
                )

            text.equals("/learn", ignoreCase = true) -> handleLearn(chatId, learnerId)
            text.equals("/review", ignoreCase = true) -> handleReview(chatId, learnerId)
            pendingReviews.containsKey(chatId) -> handleReviewAnswer(chatId, learnerId, text)
            else -> telegramClient.sendMessage(chatId, "Nutze /learn oder /review.")
        }
    }

    private suspend fun handleLearn(chatId: Long, learnerId: LearnerId) {
        when (val result = addVocabularyItemHandler.handle(AddVocabularyItemCommand(learnerId))) {
            is DomainResult.Ok -> {
                val item = result.value.vocabularyItem
                val exercise = generateExerciseHandler.handle(
                    GenerateExerciseForWordQuery(
                        vocabularyItemId = item.id,
                        germanWord = item.germanWord,
                        englishTranslation = item.englishTranslation,
                        cefrLevel = item.cefrLevel.code,
                    ),
                )
                telegramClient.sendMessage(
                    chatId,
                    "Neues Wort: ${item.germanWord} (${item.englishTranslation})\nBeispiel: ${exercise.content}",
                )
            }

            is DomainResult.Err -> telegramClient.sendMessage(chatId, "Keine neuen Wörter mehr verfügbar.")
        }
    }

    private suspend fun handleReview(chatId: Long, learnerId: LearnerId) {
        val due = getDueReviewsHandler.handle(GetDueReviewsQuery(learnerId, limit = 5))
        if (due.isEmpty()) {
            pendingReviews.remove(chatId)
            telegramClient.sendMessage(chatId, "Keine Wiederholungen fällig — nutze /learn für ein neues Wort.")
            return
        }
        presentNextReview(chatId, due)
    }

    private suspend fun presentNextReview(chatId: Long, queue: List<DueReviewItem>) {
        val current = queue.first()
        pendingReviews[chatId] = PendingReview(current, queue.drop(1))
        telegramClient.sendMessage(chatId, "Was bedeutet \"${current.vocabularyItem.germanWord}\"?")
    }

    private suspend fun handleReviewAnswer(chatId: Long, learnerId: LearnerId, answer: String) {
        val pending = pendingReviews.remove(chatId) ?: return

        when (
            val result = submitReviewAnswerHandler.handle(
                SubmitReviewAnswerCommand(learnerId, pending.current.learnerVocabularyStateId, answer),
            )
        ) {
            is DomainResult.Ok -> {
                val feedback = if (result.value.wasCorrect) {
                    "Richtig!"
                } else {
                    "Nicht ganz — richtige Antwort: ${result.value.correctAnswer}"
                }
                telegramClient.sendMessage(chatId, feedback)
            }

            is DomainResult.Err -> telegramClient.sendMessage(chatId, "Es gab ein Problem bei der Auswertung.")
        }

        if (pending.remaining.isEmpty()) {
            telegramClient.sendMessage(chatId, "Fertig für heute!")
        } else {
            presentNextReview(chatId, pending.remaining)
        }
    }
}
