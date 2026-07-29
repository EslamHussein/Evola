package evola.integrations.aigateway

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OpenAiAiTutorPortTest {

    @Test
    fun `generateExercise sends the word and parses the model reply`() = runTest {
        var capturedAuthHeader: String? = null
        var capturedUrl: String? = null

        val mockEngine = MockEngine { request ->
            capturedAuthHeader = request.headers[HttpHeaders.Authorization]
            capturedUrl = request.url.toString()

            respond(
                content = """{"choices":[{"message":{"role":"assistant","content":"Wir kaufen ein neues Haus."}}]}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val port = OpenAiAiTutorPort(client, apiKey = "test-key", exerciseModel = "gpt-5-nano")

        val result = port.generateExercise(
            GenerateExerciseRequest(
                germanWord = "Haus",
                englishTranslation = "house",
                cefrLevel = "A2.2",
                exerciseType = ExerciseType.EXAMPLE_SENTENCE,
            ),
        )

        assertEquals("Wir kaufen ein neues Haus.", result.content)
        assertEquals("gpt-5-nano", result.modelUsed)
        assertEquals("Bearer test-key", capturedAuthHeader)
        assertTrue(capturedUrl!!.contains("api.openai.com"))
    }
}
