package evola.presentation.telegram

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

/**
 * Minimal own Ktor wrapper around Telegram's `getUpdates`/`sendMessage` (long polling — no
 * public HTTPS/webhook needed). Deliberately not a third-party bot framework, to keep this
 * adapter thin and free of a framework's own session-management opinions.
 */
class TelegramClient(private val httpClient: HttpClient, botToken: String) {
    private val baseUrl = "https://api.telegram.org/bot$botToken"
    private val fileBaseUrl = "https://api.telegram.org/file/bot$botToken"

    suspend fun getUpdates(offset: Long?, timeoutSeconds: Int = 30): List<TelegramUpdate> {
        val response: TelegramGetUpdatesResponse = httpClient.get("$baseUrl/getUpdates") {
            parameter("timeout", timeoutSeconds)
            if (offset != null) parameter("offset", offset)
        }.body()
        return response.result
    }

    suspend fun sendMessage(chatId: Long, text: String, replyMarkup: TelegramInlineKeyboardMarkup? = null) {
        httpClient.post("$baseUrl/sendMessage") {
            contentType(ContentType.Application.Json)
            setBody(TelegramSendMessageRequest(chatId, text, replyMarkup))
        }
    }

    suspend fun getFile(fileId: String): TelegramFile {
        val response: TelegramGetFileResponse = httpClient.get("$baseUrl/getFile") {
            parameter("file_id", fileId)
        }.body()
        return response.result
    }

    suspend fun downloadFile(filePath: String): ByteArray =
        httpClient.get("$fileBaseUrl/$filePath").body()
}
