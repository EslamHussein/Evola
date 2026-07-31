package evola.presentation.telegram

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TelegramUser(
    val id: Long,
    @SerialName("first_name") val firstName: String? = null,
    val username: String? = null,
)

@Serializable
data class TelegramChat(val id: Long)

@Serializable
data class TelegramDocument(
    @SerialName("file_id") val fileId: String,
    @SerialName("file_name") val fileName: String? = null,
    @SerialName("mime_type") val mimeType: String? = null,
    @SerialName("file_size") val fileSize: Long? = null,
)

@Serializable
data class TelegramMessage(
    @SerialName("message_id") val messageId: Long,
    val from: TelegramUser? = null,
    val chat: TelegramChat,
    val text: String? = null,
    val document: TelegramDocument? = null,
)

@Serializable
data class TelegramFile(
    @SerialName("file_id") val fileId: String,
    @SerialName("file_path") val filePath: String? = null,
)

@Serializable
internal data class TelegramGetFileResponse(
    val ok: Boolean,
    val result: TelegramFile,
)

@Serializable
data class TelegramUpdate(
    @SerialName("update_id") val updateId: Long,
    val message: TelegramMessage? = null,
)

@Serializable
data class TelegramGetUpdatesResponse(
    val ok: Boolean,
    val result: List<TelegramUpdate> = emptyList(),
)

@Serializable
data class TelegramWebAppInfo(val url: String)

@Serializable
data class TelegramInlineKeyboardButton(
    val text: String,
    @SerialName("web_app") val webApp: TelegramWebAppInfo? = null,
)

@Serializable
data class TelegramInlineKeyboardMarkup(
    @SerialName("inline_keyboard") val inlineKeyboard: List<List<TelegramInlineKeyboardButton>>,
)

@Serializable
internal data class TelegramSendMessageRequest(
    @SerialName("chat_id") val chatId: Long,
    val text: String,
    @SerialName("reply_markup") val replyMarkup: TelegramInlineKeyboardMarkup? = null,
)
