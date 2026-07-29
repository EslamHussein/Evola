package evola.compositionroot

import evola.exercisegeneration.application.GenerateExerciseForWordHandler
import evola.exercisegeneration.infrastructure.ExposedExerciseCache
import evola.integrations.aigateway.AnthropicAiTutorPort
import evola.integrations.persistence.DatabaseFactory
import evola.learneridentity.application.RegisterLearnerHandler
import evola.learneridentity.infrastructure.ExposedExternalIdentityRepository
import evola.learneridentity.infrastructure.ExposedLearnerRepository
import evola.presentation.telegram.TelegramBotRunner
import evola.presentation.telegram.TelegramClient
import evola.vocabulary.application.AddVocabularyItemHandler
import evola.vocabulary.application.GetDueReviewsHandler
import evola.vocabulary.application.SubmitReviewAnswerHandler
import evola.vocabulary.infrastructure.ExposedLearnerVocabularyStateRepository
import evola.vocabulary.infrastructure.ExposedReviewHistoryRepository
import evola.vocabulary.infrastructure.ExposedVocabularyItemRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json

private fun requiredEnv(name: String): String =
    System.getenv(name) ?: error("Missing required environment variable: $name")

fun main() = runBlocking {
    val databaseUrl = requiredEnv("DATABASE_URL")
    val databaseUser = System.getenv("DATABASE_USER") ?: "evola"
    val databasePassword = System.getenv("DATABASE_PASSWORD") ?: "evola"
    val telegramBotToken = requiredEnv("TELEGRAM_BOT_TOKEN")
    val anthropicApiKey = requiredEnv("ANTHROPIC_API_KEY")
    val exerciseModel = System.getenv("ANTHROPIC_EXERCISE_MODEL") ?: AnthropicAiTutorPort.DEFAULT_EXERCISE_MODEL

    val database = DatabaseFactory.connect(databaseUrl, databaseUser, databasePassword)

    val learnerRepository = ExposedLearnerRepository(database)
    val externalIdentityRepository = ExposedExternalIdentityRepository(database)
    val vocabularyItemRepository = ExposedVocabularyItemRepository(database)
    val learnerVocabularyStateRepository = ExposedLearnerVocabularyStateRepository(database)
    val reviewHistoryRepository = ExposedReviewHistoryRepository(database)
    val exerciseCache = ExposedExerciseCache(database)

    val aiTutorPort = AnthropicAiTutorPort.create(anthropicApiKey, exerciseModel)

    val registerLearnerHandler = RegisterLearnerHandler(learnerRepository, externalIdentityRepository)
    val addVocabularyItemHandler = AddVocabularyItemHandler(vocabularyItemRepository, learnerVocabularyStateRepository)
    val getDueReviewsHandler = GetDueReviewsHandler(learnerVocabularyStateRepository, vocabularyItemRepository)
    val submitReviewAnswerHandler = SubmitReviewAnswerHandler(
        learnerVocabularyStateRepository,
        vocabularyItemRepository,
        reviewHistoryRepository,
    )
    val generateExerciseHandler = GenerateExerciseForWordHandler(exerciseCache, aiTutorPort)

    val telegramHttpClient = HttpClient(CIO) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }
    val telegramClient = TelegramClient(telegramHttpClient, telegramBotToken)

    val botRunner = TelegramBotRunner(
        telegramClient = telegramClient,
        registerLearnerHandler = registerLearnerHandler,
        addVocabularyItemHandler = addVocabularyItemHandler,
        getDueReviewsHandler = getDueReviewsHandler,
        submitReviewAnswerHandler = submitReviewAnswerHandler,
        generateExerciseHandler = generateExerciseHandler,
    )

    println("Evola bot starting (long polling)...")
    botRunner.run()
}
