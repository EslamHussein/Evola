package evola.integrations.aigateway

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class ChatMessage(val role: String, val content: String)

@Serializable
internal data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double = 0.7,
    @SerialName("max_tokens") val maxTokens: Int = 60,
)

@Serializable
internal data class ChatCompletionChoice(val message: ChatMessage)

@Serializable
internal data class ChatCompletionResponse(val choices: List<ChatCompletionChoice>)
