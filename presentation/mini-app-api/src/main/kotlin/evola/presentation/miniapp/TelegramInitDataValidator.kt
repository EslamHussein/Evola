package evola.presentation.miniapp

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URLDecoder
import java.security.MessageDigest
import java.time.Instant
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

data class TelegramWebAppUser(val id: Long, val firstName: String?, val username: String?)

@Serializable
private data class TelegramInitDataUserJson(
    val id: Long,
    val first_name: String? = null,
    val username: String? = null,
)

/**
 * Validates a Telegram Mini App `initData` payload per Telegram's documented algorithm:
 * https://core.telegram.org/bots/webapps#validating-data-received-via-the-mini-app
 *
 * secret_key = HMAC_SHA256(key = "WebAppData", message = botToken)
 * expected_hash = HEX(HMAC_SHA256(key = secret_key, message = dataCheckString))
 *
 * `dataCheckString` is every field except `hash`, sorted by key, joined as "key=value" with "\n".
 */
object TelegramInitDataValidator {
    private const val HMAC_ALGORITHM = "HmacSHA256"
    private const val MAX_AGE_SECONDS = 24 * 60 * 60L
    private val LENIENT_JSON = Json { ignoreUnknownKeys = true }

    fun validate(initData: String, botToken: String, now: Instant = Instant.now()): TelegramWebAppUser? {
        val fields = parseFields(initData)
        val hash = fields["hash"] ?: return null
        val authDateEpochSeconds = fields["auth_date"]?.toLongOrNull() ?: return null
        if (now.epochSecond - authDateEpochSeconds > MAX_AGE_SECONDS) return null

        val dataCheckString = fields.entries
            .filter { it.key != "hash" }
            .sortedBy { it.key }
            .joinToString("\n") { "${it.key}=${it.value}" }

        val secretKey = hmacSha256(message = botToken.toByteArray(Charsets.UTF_8), key = "WebAppData".toByteArray(Charsets.UTF_8))
        val expectedHash = hmacSha256(message = dataCheckString.toByteArray(Charsets.UTF_8), key = secretKey).toHex()
        if (!constantTimeEquals(expectedHash, hash)) return null

        val userJson = fields["user"] ?: return null
        return runCatching { LENIENT_JSON.decodeFromString(TelegramInitDataUserJson.serializer(), userJson) }
            .getOrNull()
            ?.let { TelegramWebAppUser(it.id, it.first_name, it.username) }
    }

    /** Accepts either the raw initData query string or a Bearer/`tma`-prefixed Authorization header value. */
    fun validateAuthorizationHeader(header: String?, botToken: String, now: Instant = Instant.now()): TelegramWebAppUser? {
        val initData = header
            ?.removePrefix("tma ")
            ?.removePrefix("Tma ")
            ?: return null
        return validate(initData, botToken, now)
    }

    private fun parseFields(initData: String): Map<String, String> =
        initData.split("&")
            .mapNotNull { pair ->
                val separatorIndex = pair.indexOf("=")
                if (separatorIndex < 0) return@mapNotNull null
                val key = pair.substring(0, separatorIndex)
                val value = URLDecoder.decode(pair.substring(separatorIndex + 1), "UTF-8")
                key to value
            }
            .toMap()

    private fun hmacSha256(message: ByteArray, key: ByteArray): ByteArray {
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(SecretKeySpec(key, HMAC_ALGORITHM))
        return mac.doFinal(message)
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun constantTimeEquals(a: String, b: String): Boolean =
        MessageDigest.isEqual(a.toByteArray(Charsets.UTF_8), b.toByteArray(Charsets.UTF_8))
}
