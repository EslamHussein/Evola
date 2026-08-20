package evola.shared.core.network

import evola.shared.core.common.ApiResult
import evola.shared.core.common.DataError
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AnthropicClientTest {

    @Test
    fun `complete sends the key header and returns concatenated text`() = runTest {
        var sawKey: String? = null
        val engine = MockEngine { request ->
            sawKey = request.headers["x-api-key"]
            assertEquals("2023-06-01", request.headers["anthropic-version"])
            respond(
                """{"content":[{"type":"text","text":"Hello "},{"type":"text","text":"world"}]}""",
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = AnthropicClient(engine) { "sk-test" }
        val result = client.complete(AnthropicModels.SMALL, 100, system = "sys", userMessage = "hi")
        assertEquals(ApiResult.Success("Hello world"), result)
        assertEquals("sk-test", sawKey)
    }

    @Test
    fun `a missing key short-circuits to Http(401) without a network call`() = runTest {
        var called = false
        val engine = MockEngine { called = true; respond("{}", HttpStatusCode.OK) }
        val client = AnthropicClient(engine) { null }
        val result = client.complete(AnthropicModels.SMALL, 100, null, "hi")
        assertIs<ApiResult.Failure>(result)
        assertIs<DataError.Http>(result.error)
        assertEquals(401, (result.error as DataError.Http).code)
        assertTrue(!called)
    }
}
