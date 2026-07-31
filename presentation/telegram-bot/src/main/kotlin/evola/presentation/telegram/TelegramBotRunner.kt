package evola.presentation.telegram

import evola.core.application.UseCase
import evola.core.kernel.DomainResult
import evola.core.kernel.LearnerId
import evola.core.kernel.LearningResourceId
import evola.core.kernel.LearningSessionRunId
import evola.core.kernel.TutoringSessionId
import evola.integrations.aigateway.GeneratedLearningContent
import evola.learneridentity.application.RegisterLearnerCommand
import evola.learneridentity.domain.Channel
import evola.learningresources.application.AnalyzeResourceCommand
import evola.learningresources.application.ExtractVocabularyCommand
import evola.learningresources.application.ExtractVocabularyResult
import evola.learningresources.application.GenerateResourceContentQuery
import evola.learningresources.application.UploadResourceCommand
import evola.learningresources.domain.ResourceOverview
import evola.learningresources.domain.SourceType
import evola.tutoring.application.ComposeDailySessionPlanQuery
import evola.tutoring.application.SetLearningModeCommand
import evola.tutoring.application.StartDrillResult
import evola.tutoring.application.StartGrammarExerciseCommand
import evola.tutoring.application.StartLearningSessionCommand
import evola.tutoring.application.StartSessionResult
import evola.tutoring.application.StartSpeakingScenarioCommand
import evola.tutoring.application.StartVocabularyDrillCommand
import evola.tutoring.application.SubmitDrillAnswerCommand
import evola.tutoring.application.SubmitDrillAnswerResult
import evola.tutoring.application.SubmitGrammarAnswerCommand
import evola.tutoring.application.SubmitGrammarAnswerResult
import evola.tutoring.application.SessionSummary
import evola.tutoring.application.SubmitSessionAnswerCommand
import evola.tutoring.application.SubmitSessionAnswerResult
import evola.tutoring.application.SubmitSpeakingReplyCommand
import evola.tutoring.application.SubmitSpeakingReplyResult
import evola.tutoring.domain.DailySessionPlan
import evola.tutoring.domain.LearningMode
import evola.tutoring.domain.SessionBudgetType
import evola.vocabulary.application.DueReviewItem
import evola.vocabulary.application.GetDueReviewsQuery
import kotlinx.coroutines.CancellationException
import evola.vocabulary.application.SubmitReviewAnswerCommand
import evola.vocabulary.application.SubmitReviewAnswerResult
import java.util.concurrent.ConcurrentHashMap

private data class PendingReview(val current: DueReviewItem, val remaining: List<DueReviewItem>)

/** Which multi-turn tutoring conversation (if any) this chat is currently mid-answering. */
private sealed interface PendingTutoringInteraction {
    data object AwaitingSessionBudget : PendingTutoringInteraction
    data class LearningSessionDrill(val sessionRunId: LearningSessionRunId, val tutoringSessionId: TutoringSessionId) : PendingTutoringInteraction
    data class VocabularyDrill(val sessionId: TutoringSessionId) : PendingTutoringInteraction
    data class GrammarDrill(val sessionId: TutoringSessionId) : PendingTutoringInteraction
    data class SpeakingConversation(val sessionId: TutoringSessionId) : PendingTutoringInteraction
}

/** Which step of the /resource upload-and-practice flow this chat is currently in. */
private sealed interface PendingResourceStep {
    data object AwaitingUpload : PendingResourceStep
    data class AwaitingGoal(val resourceId: LearningResourceId) : PendingResourceStep
}

/**
 * Telegram adapter. Every piece of tutoring logic below is a Command/Query dispatched to the
 * Application layer — the same contracts a future Android/Web client would use. The only state
 * kept here is per-chat "which turn of a conversation is this" bookkeeping — permitted UI
 * dialog-flow state (ADR D2), not tutoring logic. /learn and /review (Milestone 1) are unchanged;
 * /practice, /grammar, /speaking, /daily, /mode (Milestone 3) are additive.
 */
class TelegramBotRunner(
    private val telegramClient: TelegramClient,
    private val registerLearnerHandler: UseCase<RegisterLearnerCommand, LearnerId>,
    private val getDueReviewsHandler: UseCase<GetDueReviewsQuery, List<DueReviewItem>>,
    private val submitReviewAnswerHandler: UseCase<SubmitReviewAnswerCommand, DomainResult<SubmitReviewAnswerResult>>,
    private val startVocabularyDrillHandler: UseCase<StartVocabularyDrillCommand, DomainResult<StartDrillResult>>,
    private val submitDrillAnswerHandler: UseCase<SubmitDrillAnswerCommand, DomainResult<SubmitDrillAnswerResult>>,
    private val startGrammarExerciseHandler: UseCase<StartGrammarExerciseCommand, DomainResult<StartDrillResult>>,
    private val submitGrammarAnswerHandler: UseCase<SubmitGrammarAnswerCommand, DomainResult<SubmitGrammarAnswerResult>>,
    private val startSpeakingScenarioHandler: UseCase<StartSpeakingScenarioCommand, DomainResult<StartDrillResult>>,
    private val submitSpeakingReplyHandler: UseCase<SubmitSpeakingReplyCommand, DomainResult<SubmitSpeakingReplyResult>>,
    private val setLearningModeHandler: UseCase<SetLearningModeCommand, Unit>,
    private val composeDailySessionPlanHandler: UseCase<ComposeDailySessionPlanQuery, DailySessionPlan>,
    private val uploadResourceHandler: UseCase<UploadResourceCommand, LearningResourceId>,
    private val analyzeResourceHandler: UseCase<AnalyzeResourceCommand, DomainResult<ResourceOverview>>,
    private val generateResourceContentHandler: UseCase<GenerateResourceContentQuery, DomainResult<GeneratedLearningContent>>,
    private val extractVocabularyHandler: UseCase<ExtractVocabularyCommand, DomainResult<ExtractVocabularyResult>>,
    private val startLearningSessionHandler: UseCase<StartLearningSessionCommand, DomainResult<StartSessionResult>>,
    private val submitSessionAnswerHandler: UseCase<SubmitSessionAnswerCommand, DomainResult<SubmitSessionAnswerResult>>,
    /** HTTPS URL of the Mini App (Caddy-fronted); null while it isn't deployed yet — /start just omits the button. */
    private val miniAppUrl: String? = null,
) {
    private val pendingReviews = ConcurrentHashMap<Long, PendingReview>()
    private val pendingInteractions = ConcurrentHashMap<Long, PendingTutoringInteraction>()
    private val pendingResourceSteps = ConcurrentHashMap<Long, PendingResourceStep>()

    suspend fun run() {
        var offset: Long? = null
        while (true) {
            // Never swallow CancellationException here: doing so would let this loop spin forever
            // re-issuing (and instantly re-cancelling) requests once the enclosing scope is cancelled,
            // instead of promptly exiting — a runaway-CPU failure mode, not just a missed poll.
            val updates = try {
                telegramClient.getUpdates(offset)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            }
            for (update in updates) {
                offset = update.updateId + 1
                runCatching { handleUpdate(update) }.onFailure { it.printStackTrace() }
            }
        }
    }

    private suspend fun handleUpdate(update: TelegramUpdate) {
        val message = update.message ?: return
        val text = message.text?.trim().orEmpty()
        val document = message.document
        if (text.isEmpty() && document == null) return

        val chatId = message.chat.id
        val telegramUserId = (message.from?.id ?: chatId).toString()
        val displayName = message.from?.username ?: message.from?.firstName

        val learnerId = registerLearnerHandler.handle(
            RegisterLearnerCommand(Channel.TELEGRAM, telegramUserId, displayName),
        )

        when {
            document != null && pendingResourceSteps[chatId] == PendingResourceStep.AwaitingUpload ->
                handleResourceDocument(chatId, learnerId, document)

            text.equals("/start", ignoreCase = true) ->
                telegramClient.sendMessage(
                    chatId,
                    "Willkommen bei Evola! Nutze /learn oder /review für Vokabeln, /practice für adaptives " +
                        "Üben, /grammar, /speaking, /daily für dein Tagesprogramm, /resource zum Hochladen " +
                        "eigener Materialien, oder /mode zum Wechseln." +
                        (if (miniAppUrl != null) "\n\nOder öffne die Evola Mini App für ein interaktives Lernerlebnis." else ""),
                    miniAppUrl?.let {
                        TelegramInlineKeyboardMarkup(
                            listOf(listOf(TelegramInlineKeyboardButton(text = "Evola öffnen", webApp = TelegramWebAppInfo(it)))),
                        )
                    },
                )

            text.equals("/learn", ignoreCase = true) -> handleStartLearningSession(chatId)
            text.equals("/review", ignoreCase = true) -> handleReview(chatId, learnerId)
            text.equals("/cancel", ignoreCase = true) -> handleCancel(chatId)
            text.equals("/practice", ignoreCase = true) -> handleStartPractice(chatId, learnerId)
            text.equals("/grammar", ignoreCase = true) -> handleStartGrammar(chatId, learnerId)
            text.equals("/speaking", ignoreCase = true) -> handleStartSpeaking(chatId, learnerId)
            text.equals("/daily", ignoreCase = true) -> handleDaily(chatId, learnerId)
            text.equals("/resource", ignoreCase = true) -> handleStartResourceUpload(chatId)
            text.startsWith("/mode", ignoreCase = true) -> handleSetMode(chatId, learnerId, text)
            pendingResourceSteps.containsKey(chatId) -> handleResourceStepText(chatId, learnerId, text)
            pendingInteractions.containsKey(chatId) -> handlePendingInteraction(chatId, learnerId, text)
            pendingReviews.containsKey(chatId) -> handleReviewAnswer(chatId, learnerId, text)
            else -> telegramClient.sendMessage(chatId, "Nutze /learn, /review, /practice, /grammar, /speaking, /daily oder /resource.")
        }
    }

    private fun handleCancel(chatId: Long) {
        pendingInteractions.remove(chatId)
        pendingReviews.remove(chatId)
        pendingResourceSteps.remove(chatId)
    }

    // ---- Milestone 1 (unchanged) ----

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

    // ---- Milestone 3: adaptive tutor ----

    private suspend fun handleStartPractice(chatId: Long, learnerId: LearnerId) {
        when (val result = startVocabularyDrillHandler.handle(StartVocabularyDrillCommand(learnerId))) {
            is DomainResult.Ok -> {
                pendingInteractions[chatId] = PendingTutoringInteraction.VocabularyDrill(result.value.sessionId)
                telegramClient.sendMessage(chatId, result.value.promptText)
            }
            is DomainResult.Err -> telegramClient.sendMessage(chatId, "Keine Vokabeln zum Üben verfügbar.")
        }
    }

    private suspend fun handleStartGrammar(chatId: Long, learnerId: LearnerId) {
        when (val result = startGrammarExerciseHandler.handle(StartGrammarExerciseCommand(learnerId))) {
            is DomainResult.Ok -> {
                pendingInteractions[chatId] = PendingTutoringInteraction.GrammarDrill(result.value.sessionId)
                telegramClient.sendMessage(chatId, result.value.promptText)
            }
            is DomainResult.Err -> telegramClient.sendMessage(chatId, "Grammatikübung konnte nicht erstellt werden.")
        }
    }

    private suspend fun handleStartSpeaking(chatId: Long, learnerId: LearnerId) {
        when (val result = startSpeakingScenarioHandler.handle(StartSpeakingScenarioCommand(learnerId))) {
            is DomainResult.Ok -> {
                pendingInteractions[chatId] = PendingTutoringInteraction.SpeakingConversation(result.value.sessionId)
                telegramClient.sendMessage(chatId, result.value.promptText)
            }
            is DomainResult.Err -> telegramClient.sendMessage(chatId, "Sprechübung konnte nicht erstellt werden.")
        }
    }

    private suspend fun handleDaily(chatId: Long, learnerId: LearnerId) {
        val plan = composeDailySessionPlanHandler.handle(ComposeDailySessionPlanQuery(learnerId))
        val message = buildString {
            appendLine("Dein heutiges Programm:")
            appendLine()
            appendLine("Wiederholungen fällig: ${plan.dueReviewCount}")
            appendLine("Schwache Wörter: ${plan.weakVocabularyItemIds.size}")
            appendLine("Grammatik: ${plan.grammarFocusTopic ?: "keine offenen Fehler"}")
            append("Sprechen: ${plan.speakingScenarioTitle ?: "kein Szenario"}")
        }
        telegramClient.sendMessage(chatId, message)
    }

    private suspend fun handleSetMode(chatId: Long, learnerId: LearnerId, text: String) {
        val arg = text.substringAfter("/mode").trim().uppercase().replace(" ", "_")
        val mode = LearningMode.entries.firstOrNull { it.name == arg }
        if (mode == null) {
            telegramClient.sendMessage(
                chatId,
                "Nutze /mode gefolgt von: vocabulary, grammar, speaking, exam_preparation oder daily_challenge.",
            )
            return
        }
        setLearningModeHandler.handle(SetLearningModeCommand(learnerId, mode))
        telegramClient.sendMessage(chatId, "Modus gesetzt: ${mode.name}")
    }

    // ---- Milestone 4: bounded /learn sessions ----

    private suspend fun handleStartLearningSession(chatId: Long) {
        pendingInteractions[chatId] = PendingTutoringInteraction.AwaitingSessionBudget
        telegramClient.sendMessage(
            chatId,
            "Wie möchtest du lernen? Antworte mit: 10min, 20min, 30min, 10words, 20words oder 50words.",
        )
    }

    private val budgetPattern = Regex("^(\\d+)(min|words)$")

    private suspend fun handleSessionBudgetChoice(chatId: Long, learnerId: LearnerId, text: String) {
        val match = budgetPattern.matchEntire(text.trim().lowercase())
        if (match == null) {
            telegramClient.sendMessage(chatId, "Bitte antworte mit z.B. 10min, 20min, 30min, 10words, 20words oder 50words.")
            return
        }
        val value = match.groupValues[1].toInt()
        val budgetType = if (match.groupValues[2] == "min") SessionBudgetType.DURATION_MINUTES else SessionBudgetType.WORD_COUNT

        when (val result = startLearningSessionHandler.handle(StartLearningSessionCommand(learnerId, budgetType, value))) {
            is DomainResult.Ok -> {
                pendingInteractions[chatId] = PendingTutoringInteraction.LearningSessionDrill(result.value.sessionRunId, result.value.tutoringSessionId)
                telegramClient.sendMessage(chatId, result.value.promptText)
            }
            is DomainResult.Err -> {
                pendingInteractions.remove(chatId)
                telegramClient.sendMessage(chatId, "Keine Vokabeln zum Lernen verfügbar.")
            }
        }
    }

    private suspend fun handleSessionAnswer(chatId: Long, learnerId: LearnerId, pending: PendingTutoringInteraction.LearningSessionDrill, text: String) {
        when (val result = submitSessionAnswerHandler.handle(SubmitSessionAnswerCommand(learnerId, pending.sessionRunId, pending.tutoringSessionId, text))) {
            is DomainResult.Ok -> {
                telegramClient.sendMessage(chatId, result.value.feedback)
                val summary = result.value.summary
                if (summary != null) {
                    pendingInteractions.remove(chatId)
                    telegramClient.sendMessage(chatId, formatSessionSummary(summary))
                } else {
                    val nextPrompt = result.value.nextPrompt
                    val nextTutoringSessionId = result.value.nextTutoringSessionId
                    if (nextPrompt != null && nextTutoringSessionId != null) {
                        pendingInteractions[chatId] = PendingTutoringInteraction.LearningSessionDrill(pending.sessionRunId, nextTutoringSessionId)
                        telegramClient.sendMessage(chatId, nextPrompt)
                    }
                }
            }
            is DomainResult.Err -> {
                pendingInteractions.remove(chatId)
                telegramClient.sendMessage(chatId, "Es gab ein Problem bei der Auswertung.")
            }
        }
    }

    private fun formatSessionSummary(summary: SessionSummary): String = buildString {
        appendLine("Session Complete")
        appendLine()
        appendLine("Dauer: ${summary.durationMinutes} Minuten")
        appendLine("Fragen: ${summary.questionsAsked}")
        appendLine("Richtig: ${summary.correctCount}")
        appendLine("Falsch: ${summary.incorrectCount}")
        appendLine("Genauigkeit: ${summary.accuracyPercent}%")
        appendLine("Wörter gemeistert: ${summary.wordsMastered}")
        appendLine("Wörter zum Üben: ${summary.wordsNeedingPractice}")
        appendLine()
        append("Nutze /learn für die nächste Runde.")
    }

    private suspend fun handlePendingInteraction(chatId: Long, learnerId: LearnerId, text: String) {
        when (val pending = pendingInteractions[chatId]) {
            is PendingTutoringInteraction.AwaitingSessionBudget -> handleSessionBudgetChoice(chatId, learnerId, text)
            is PendingTutoringInteraction.LearningSessionDrill -> handleSessionAnswer(chatId, learnerId, pending, text)

            is PendingTutoringInteraction.VocabularyDrill -> {
                when (val result = submitDrillAnswerHandler.handle(SubmitDrillAnswerCommand(learnerId, pending.sessionId, text))) {
                    is DomainResult.Ok -> {
                        telegramClient.sendMessage(chatId, result.value.feedback)
                        val followUpPrompt = result.value.followUpPrompt
                        if (followUpPrompt != null) {
                            telegramClient.sendMessage(chatId, followUpPrompt)
                        } else {
                            pendingInteractions.remove(chatId)
                        }
                    }
                    is DomainResult.Err -> {
                        pendingInteractions.remove(chatId)
                        telegramClient.sendMessage(chatId, "Es gab ein Problem bei der Auswertung.")
                    }
                }
            }

            is PendingTutoringInteraction.GrammarDrill -> {
                pendingInteractions.remove(chatId)
                when (val result = submitGrammarAnswerHandler.handle(SubmitGrammarAnswerCommand(learnerId, pending.sessionId, text))) {
                    is DomainResult.Ok -> telegramClient.sendMessage(chatId, result.value.feedback)
                    is DomainResult.Err -> telegramClient.sendMessage(chatId, "Es gab ein Problem bei der Auswertung.")
                }
            }

            is PendingTutoringInteraction.SpeakingConversation -> {
                when (val result = submitSpeakingReplyHandler.handle(SubmitSpeakingReplyCommand(learnerId, pending.sessionId, text))) {
                    is DomainResult.Ok -> {
                        result.value.correctedReply?.let {
                            telegramClient.sendMessage(chatId, "Korrektur: $it\n${result.value.correctionExplanation.orEmpty()}")
                        }
                        telegramClient.sendMessage(chatId, result.value.aiNextLine)
                        if (result.value.scenarioCompleted) {
                            pendingInteractions.remove(chatId)
                            telegramClient.sendMessage(chatId, "Szenario beendet!")
                        }
                    }
                    is DomainResult.Err -> {
                        pendingInteractions.remove(chatId)
                        telegramClient.sendMessage(chatId, "Es gab ein Problem beim Gespräch.")
                    }
                }
            }

            null -> Unit
        }
    }

    // ---- Milestone 2: resource library ----

    private suspend fun handleStartResourceUpload(chatId: Long) {
        pendingResourceSteps[chatId] = PendingResourceStep.AwaitingUpload
        telegramClient.sendMessage(chatId, "Sende mir ein PDF oder füge einen Text ein, den du lernen möchtest.")
    }

    private suspend fun handleResourceDocument(chatId: Long, learnerId: LearnerId, document: TelegramDocument) {
        if (document.mimeType != "application/pdf") {
            pendingResourceSteps.remove(chatId)
            telegramClient.sendMessage(chatId, "Nur PDF oder eingefügter Text werden aktuell unterstützt.")
            return
        }
        val filePath = telegramClient.getFile(document.fileId).filePath
        if (filePath == null) {
            pendingResourceSteps.remove(chatId)
            telegramClient.sendMessage(chatId, "Datei konnte nicht geladen werden.")
            return
        }
        val bytes = telegramClient.downloadFile(filePath)
        val resourceId = uploadResourceHandler.handle(
            UploadResourceCommand(learnerId, document.fileName ?: "document.pdf", SourceType.PDF, bytes),
        )
        finishResourceAnalysis(chatId, resourceId)
    }

    private suspend fun handleResourceStepText(chatId: Long, learnerId: LearnerId, text: String) {
        when (val step = pendingResourceSteps[chatId]) {
            is PendingResourceStep.AwaitingUpload -> {
                val resourceId = uploadResourceHandler.handle(
                    UploadResourceCommand(learnerId, "Note", SourceType.TEXT_NOTE, text.toByteArray()),
                )
                finishResourceAnalysis(chatId, resourceId)
            }

            is PendingResourceStep.AwaitingGoal -> handleResourceGoalChoice(chatId, step.resourceId, text)
            null -> Unit
        }
    }

    private suspend fun finishResourceAnalysis(chatId: Long, resourceId: LearningResourceId) {
        when (val result = analyzeResourceHandler.handle(AnalyzeResourceCommand(resourceId))) {
            is DomainResult.Ok -> {
                val overview = result.value
                val truncatedNote = if (overview.wasTruncated) {
                    "\n(Hinweis: nur der Anfang des Dokuments wurde analysiert.)"
                } else {
                    ""
                }
                telegramClient.sendMessage(
                    chatId,
                    "Sprache: ${overview.language}\nNiveau: ${overview.cefrLevel}\n" +
                        "Themen: ${overview.topics.joinToString(", ")}\n${overview.summary}$truncatedNote\n\n" +
                        "Worauf möchtest du dich konzentrieren? Antworte mit: vocabulary, grammar, speaking oder exam.",
                )
                pendingResourceSteps[chatId] = PendingResourceStep.AwaitingGoal(resourceId)

                when (val extraction = extractVocabularyHandler.handle(ExtractVocabularyCommand(resourceId))) {
                    is DomainResult.Ok -> if (extraction.value.addedCount > 0) {
                        telegramClient.sendMessage(chatId, "${extraction.value.addedCount} Wörter zu deiner Bibliothek hinzugefügt.")
                    }
                    is DomainResult.Err -> Unit // non-fatal — the overview/goal flow already succeeded
                }
            }

            is DomainResult.Err -> {
                pendingResourceSteps.remove(chatId)
                telegramClient.sendMessage(chatId, "Die Analyse ist fehlgeschlagen.")
            }
        }
    }

    private suspend fun handleResourceGoalChoice(chatId: Long, resourceId: LearningResourceId, text: String) {
        val goal = when (text.trim().lowercase()) {
            "vocabulary" -> "VOCABULARY"
            "grammar" -> "GRAMMAR"
            "speaking" -> "SPEAKING"
            "exam", "exam_preparation" -> "EXAM_PREPARATION"
            else -> null
        }
        if (goal == null) {
            telegramClient.sendMessage(chatId, "Bitte antworte mit: vocabulary, grammar, speaking oder exam.")
            return
        }
        // Stays in AwaitingGoal afterward — the same resource can be revisited with a different goal.
        when (val result = generateResourceContentHandler.handle(GenerateResourceContentQuery(resourceId, goal))) {
            is DomainResult.Ok -> telegramClient.sendMessage(chatId, result.value.content)
            is DomainResult.Err -> telegramClient.sendMessage(chatId, "Inhalt konnte nicht erstellt werden.")
        }
    }
}
