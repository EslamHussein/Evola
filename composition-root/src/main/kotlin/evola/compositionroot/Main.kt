package evola.compositionroot

import evola.integrations.aigateway.AnthropicAiTutorPort
import evola.integrations.persistence.DatabaseFactory
import evola.learneridentity.application.RegisterLearnerHandler
import evola.learneridentity.infrastructure.ExposedExternalIdentityRepository
import evola.learneridentity.infrastructure.ExposedLearnerRepository
import evola.presentation.telegram.TelegramBotRunner
import evola.presentation.telegram.TelegramClient
import evola.vocabulary.application.GetDueReviewsHandler
import evola.vocabulary.application.SubmitReviewAnswerHandler
import evola.vocabulary.infrastructure.ExposedLearnerVocabularyStateRepository
import evola.vocabulary.infrastructure.ExposedReviewHistoryRepository
import evola.vocabulary.infrastructure.ExposedVocabularyItemRepository
import evola.tutoring.application.ComposeDailySessionPlanHandler
import evola.tutoring.application.GetOrGeneratePracticeExerciseHandler
import evola.tutoring.application.SetLearningModeHandler
import evola.tutoring.application.StartGrammarExerciseHandler
import evola.tutoring.application.StartLearningSessionHandler
import evola.tutoring.application.StartSpeakingScenarioHandler
import evola.tutoring.application.StartVocabularyDrillHandler
import evola.tutoring.application.SubmitDrillAnswerHandler
import evola.tutoring.application.SubmitGrammarAnswerHandler
import evola.tutoring.application.SubmitSessionAnswerHandler
import evola.tutoring.application.SubmitSpeakingReplyHandler
import evola.tutoring.infrastructure.ExposedDailySessionPlanRepository
import evola.tutoring.infrastructure.ExposedDialogueTurnRepository
import evola.tutoring.infrastructure.ExposedLearnerTutoringProfileRepository
import evola.tutoring.infrastructure.ExposedLearningSessionRunRepository
import evola.tutoring.infrastructure.ExposedTutoringGrammarContentCache
import evola.tutoring.infrastructure.ExposedTutoringSessionRepository
import evola.tutoring.infrastructure.ExposedTutoringWordContentCache
import evola.learningresources.application.AnalyzeResourceHandler
import evola.learningresources.application.ExtractVocabularyHandler
import evola.learningresources.application.GenerateResourceContentHandler
import evola.learningresources.application.UploadResourceHandler
import evola.learningresources.infrastructure.CompositeResourceTextExtractor
import evola.learningresources.infrastructure.ExposedLearningResourceContentCache
import evola.learningresources.infrastructure.ExposedLearningResourceRepository
import evola.learningresources.infrastructure.LocalDiskResourceFileStorage
import evola.presentation.miniapp.buildMiniAppServer
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import kotlinx.serialization.json.Json

private fun requiredEnv(name: String): String =
    System.getenv(name) ?: error("Missing required environment variable: $name")

fun main(): Unit = runBlocking {
    val databaseUrl = requiredEnv("DATABASE_URL")
    val databaseUser = System.getenv("DATABASE_USER") ?: "evola"
    val databasePassword = System.getenv("DATABASE_PASSWORD") ?: "evola"
    val telegramBotToken = requiredEnv("TELEGRAM_BOT_TOKEN")
    val anthropicApiKey = requiredEnv("ANTHROPIC_API_KEY")
    val exerciseModel = System.getenv("ANTHROPIC_EXERCISE_MODEL") ?: AnthropicAiTutorPort.DEFAULT_EXERCISE_MODEL
    val evaluationModel = System.getenv("ANTHROPIC_EVALUATION_MODEL") ?: AnthropicAiTutorPort.DEFAULT_EVALUATION_MODEL
    val speakingModel = System.getenv("ANTHROPIC_SPEAKING_MODEL") ?: AnthropicAiTutorPort.DEFAULT_SPEAKING_MODEL
    val resourceStorageDir = System.getenv("RESOURCE_STORAGE_DIR") ?: "./resources"
    val miniAppPort = (System.getenv("MINI_APP_PORT") ?: "8081").toInt()
    val allowedOrigins = System.getenv("ALLOWED_ORIGINS")
        ?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }
        ?: emptyList()
    val miniAppPublicUrl = System.getenv("MINI_APP_PUBLIC_URL")

    val database = DatabaseFactory.connect(databaseUrl, databaseUser, databasePassword)

    val learnerRepository = ExposedLearnerRepository(database)
    val externalIdentityRepository = ExposedExternalIdentityRepository(database)
    val vocabularyItemRepository = ExposedVocabularyItemRepository(database)
    val learnerVocabularyStateRepository = ExposedLearnerVocabularyStateRepository(database)
    val reviewHistoryRepository = ExposedReviewHistoryRepository(database)

    val tutoringSessionRepository = ExposedTutoringSessionRepository(database)
    val dialogueTurnRepository = ExposedDialogueTurnRepository(database)
    val tutoringWordContentCache = ExposedTutoringWordContentCache(database)
    val tutoringGrammarContentCache = ExposedTutoringGrammarContentCache(database)
    val learnerTutoringProfileRepository = ExposedLearnerTutoringProfileRepository(database)
    val dailySessionPlanRepository = ExposedDailySessionPlanRepository(database)
    val learningSessionRunRepository = ExposedLearningSessionRunRepository(database)

    val learningResourceRepository = ExposedLearningResourceRepository(database)
    val learningResourceContentCache = ExposedLearningResourceContentCache(database)
    val resourceTextExtractor = CompositeResourceTextExtractor()
    val resourceFileStorage = LocalDiskResourceFileStorage(resourceStorageDir)

    val aiTutorPort = AnthropicAiTutorPort.create(anthropicApiKey, exerciseModel, evaluationModel, speakingModel)

    val registerLearnerHandler = RegisterLearnerHandler(learnerRepository, externalIdentityRepository)
    val getDueReviewsHandler = GetDueReviewsHandler(learnerVocabularyStateRepository, vocabularyItemRepository)
    val submitReviewAnswerHandler = SubmitReviewAnswerHandler(
        learnerVocabularyStateRepository,
        vocabularyItemRepository,
        reviewHistoryRepository,
    )

    val practiceExerciseProvider = GetOrGeneratePracticeExerciseHandler(tutoringWordContentCache, tutoringGrammarContentCache, aiTutorPort)
    val startVocabularyDrillHandler = StartVocabularyDrillHandler(
        learnerVocabularyStateRepository, vocabularyItemRepository, reviewHistoryRepository,
        tutoringSessionRepository, dialogueTurnRepository, practiceExerciseProvider,
    )
    val submitDrillAnswerHandler = SubmitDrillAnswerHandler(
        tutoringSessionRepository, dialogueTurnRepository, learnerVocabularyStateRepository,
        vocabularyItemRepository, reviewHistoryRepository, practiceExerciseProvider, aiTutorPort,
    )
    val startGrammarExerciseHandler = StartGrammarExerciseHandler(tutoringSessionRepository, dialogueTurnRepository, practiceExerciseProvider)
    val submitGrammarAnswerHandler = SubmitGrammarAnswerHandler(tutoringSessionRepository, dialogueTurnRepository)
    val startSpeakingScenarioHandler = StartSpeakingScenarioHandler(tutoringSessionRepository, dialogueTurnRepository, aiTutorPort)
    val submitSpeakingReplyHandler = SubmitSpeakingReplyHandler(tutoringSessionRepository, dialogueTurnRepository, aiTutorPort)
    val setLearningModeHandler = SetLearningModeHandler(learnerTutoringProfileRepository)
    val composeDailySessionPlanHandler = ComposeDailySessionPlanHandler(
        dailySessionPlanRepository, learnerVocabularyStateRepository, dialogueTurnRepository,
    )

    val uploadResourceHandler = UploadResourceHandler(learningResourceRepository, resourceTextExtractor, resourceFileStorage)
    val analyzeResourceHandler = AnalyzeResourceHandler(learningResourceRepository, aiTutorPort)
    val generateResourceContentHandler = GenerateResourceContentHandler(learningResourceRepository, learningResourceContentCache, aiTutorPort)
    val extractVocabularyHandler = ExtractVocabularyHandler(
        learningResourceRepository, vocabularyItemRepository, learnerVocabularyStateRepository, aiTutorPort,
    )

    val startLearningSessionHandler = StartLearningSessionHandler(learningSessionRunRepository, startVocabularyDrillHandler)
    val submitSessionAnswerHandler = SubmitSessionAnswerHandler(
        learningSessionRunRepository, tutoringSessionRepository, submitDrillAnswerHandler,
        startVocabularyDrillHandler, learnerVocabularyStateRepository,
    )

    val telegramHttpClient = HttpClient(CIO) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }
    val telegramClient = TelegramClient(telegramHttpClient, telegramBotToken)

    val miniAppServer = buildMiniAppServer(
        port = miniAppPort,
        botToken = telegramBotToken,
        allowedOrigins = allowedOrigins,
        registerLearnerHandler = registerLearnerHandler,
        startLearningSessionHandler = startLearningSessionHandler,
        submitSessionAnswerHandler = submitSessionAnswerHandler,
        setLearningModeHandler = setLearningModeHandler,
    )

    val botRunner = TelegramBotRunner(
        telegramClient = telegramClient,
        registerLearnerHandler = registerLearnerHandler,
        getDueReviewsHandler = getDueReviewsHandler,
        submitReviewAnswerHandler = submitReviewAnswerHandler,
        startVocabularyDrillHandler = startVocabularyDrillHandler,
        submitDrillAnswerHandler = submitDrillAnswerHandler,
        startGrammarExerciseHandler = startGrammarExerciseHandler,
        submitGrammarAnswerHandler = submitGrammarAnswerHandler,
        startSpeakingScenarioHandler = startSpeakingScenarioHandler,
        submitSpeakingReplyHandler = submitSpeakingReplyHandler,
        setLearningModeHandler = setLearningModeHandler,
        composeDailySessionPlanHandler = composeDailySessionPlanHandler,
        uploadResourceHandler = uploadResourceHandler,
        analyzeResourceHandler = analyzeResourceHandler,
        generateResourceContentHandler = generateResourceContentHandler,
        extractVocabularyHandler = extractVocabularyHandler,
        startLearningSessionHandler = startLearningSessionHandler,
        submitSessionAnswerHandler = submitSessionAnswerHandler,
        miniAppUrl = miniAppPublicUrl,
    )

    println("Evola bot starting (long polling)...")
    // supervisorScope, not coroutineScope: the bot and the Mini App API are independent subsystems —
    // a startup/runtime failure in one (e.g. a bad CORS host) must not cancel the other.
    supervisorScope {
        launch {
            runCatching { botRunner.run() }.onFailure { it.printStackTrace() }
        }
        launch {
            println("Mini App API starting on 127.0.0.1:$miniAppPort ...")
            runCatching { miniAppServer.start(wait = true) }.onFailure { it.printStackTrace() }
        }
    }
}
