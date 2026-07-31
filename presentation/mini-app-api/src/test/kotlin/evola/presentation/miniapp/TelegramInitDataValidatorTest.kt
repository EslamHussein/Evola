package evola.presentation.miniapp

import java.net.URLEncoder
import java.time.Instant
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TelegramInitDataValidatorTest {

    private val botToken = "123456:ABC-DEF-test-token"

    /** Independently reimplements Telegram's signing algorithm (not the code under test) to build a valid fixture. */
    private fun buildInitData(fields: Map<String, String>, botToken: String = this.botToken): String {
        val dataCheckString = fields.entries.sortedBy { it.key }.joinToString("\n") { "${it.key}=${it.value}" }
        val secretKey = hmac("WebAppData".toByteArray(), botToken.toByteArray())
        val hash = hmac(secretKey, dataCheckString.toByteArray()).joinToString("") { "%02x".format(it) }
        val allFields = fields + ("hash" to hash)
        return allFields.entries.joinToString("&") { "${it.key}=${URLEncoder.encode(it.value, "UTF-8")}" }
    }

    private fun hmac(key: ByteArray, message: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(message)
    }

    private fun validFields(authDate: Instant = Instant.now()) = mapOf(
        "user" to """{"id":987654321,"first_name":"Ada","username":"ada_dev"}""",
        "auth_date" to authDate.epochSecond.toString(),
        "query_id" to "AAEAAA",
    )

    @Test
    fun `accepts correctly signed initData and extracts the user`() {
        val initData = buildInitData(validFields())
        val user = TelegramInitDataValidator.validate(initData, botToken)
        assertEquals(TelegramWebAppUser(987654321, "Ada", "ada_dev"), user)
    }

    @Test
    fun `accepts via the tma-prefixed Authorization header form`() {
        val initData = buildInitData(validFields())
        val user = TelegramInitDataValidator.validateAuthorizationHeader("tma $initData", botToken)
        assertEquals(987654321L, user?.id)
    }

    @Test
    fun `rejects a tampered hash`() {
        val initData = buildInitData(validFields()).replace(Regex("hash=[0-9a-f]+"), "hash=deadbeef")
        assertNull(TelegramInitDataValidator.validate(initData, botToken))
    }

    @Test
    fun `rejects a payload signed with a different bot token`() {
        val initData = buildInitData(validFields(), botToken = "other-token")
        assertNull(TelegramInitDataValidator.validate(initData, botToken))
    }

    @Test
    fun `rejects stale auth_date beyond the freshness window`() {
        val initData = buildInitData(validFields(authDate = Instant.now().minusSeconds(25 * 60 * 60)))
        assertNull(TelegramInitDataValidator.validate(initData, botToken))
    }

    @Test
    fun `rejects payload missing the user field`() {
        val initData = buildInitData(mapOf("auth_date" to Instant.now().epochSecond.toString()))
        assertNull(TelegramInitDataValidator.validate(initData, botToken))
    }

    @Test
    fun `rejects payload missing the hash entirely`() {
        assertNull(TelegramInitDataValidator.validate("auth_date=${Instant.now().epochSecond}", botToken))
    }
}
