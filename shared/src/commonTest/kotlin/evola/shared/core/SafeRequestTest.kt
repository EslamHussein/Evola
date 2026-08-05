package evola.shared.core

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.io.IOException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@Serializable
private data class Widget(val id: String)

/** Exercises [safeRequest] over the REAL [createBaseHttpClient] factory (via [MockEngine]), so the
 * boundary's plugin config and error-mapping are what's under test, not a hand-rolled stand-in
 * (kmp-ktor testing rule). */
class SafeRequestTest {

    private fun client(handler: MockEngine) = createBaseHttpClient(handler)

    @Test
    fun `a 2xx response maps to Success with the parsed body`() = runTest {
        val engine = MockEngine {
            respond("""{"id":"w1"}""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val http = client(engine)
        val result = safeRequest<Widget> { http.get("https://api.test/widgets/w1") }
        assertEquals(ApiResult.Success(Widget("w1")), result)
    }

    @Test
    fun `a 500 maps to Failure(Http) carrying the server's own message`() = runTest {
        val engine = MockEngine {
            respond(
                """{"error":{"code":"BOOM","message":"It broke"}}""",
                HttpStatusCode.InternalServerError,
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val http = client(engine)
        val result = safeRequest<Widget> { http.get("https://api.test/widgets/w1") }
        assertIs<ApiResult.Failure>(result)
        val error = result.error
        assertIs<DataError.Http>(error)
        assertEquals(500, error.code)
        assertEquals("It broke", error.serverMessage)
    }

    @Test
    fun `a 404 maps to Failure(Http 404) - callers decide if absence is a valid state`() = runTest {
        val engine = MockEngine {
            respond("""{"error":"Not found"}""", HttpStatusCode.NotFound, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val http = client(engine)
        val result = safeRequest<Widget> { http.get("https://api.test/widgets/missing") }
        assertIs<ApiResult.Failure>(result)
        val error = result.error
        assertIs<DataError.Http>(error)
        assertEquals(404, error.code)
        assertEquals("Not found", error.serverMessage)
    }

    @Test
    fun `a transport IOException maps to Failure(Network), never leaking the raw exception`() = runTest {
        val engine = MockEngine { throw IOException("connection reset") }
        val http = client(engine)
        val result = safeRequest<Widget> { http.get("https://api.test/widgets/w1") }
        assertEquals(ApiResult.Failure(DataError.Network), result)
    }

    @Test
    fun `map transforms a Success and passes a Failure through untouched`() {
        assertEquals(ApiResult.Success(2), ApiResult.Success(1).map { it + 1 })
        val failure: ApiResult<Int> = ApiResult.Failure(DataError.Network)
        assertTrue(failure.map { it + 1 } is ApiResult.Failure)
    }
}
