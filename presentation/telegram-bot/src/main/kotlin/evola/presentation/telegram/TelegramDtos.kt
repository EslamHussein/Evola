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
data class TelegramMessage(
    @SerialName("message_id") val messageId: Long,
    val from: TelegramUser? = null,
    val chat: TelegramChat,
    val text: String? = null,
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
internal data class TelegramSendMessageRequest(
    @SerialName("chat_id") val chatId: Long,
    val text: String,
)
