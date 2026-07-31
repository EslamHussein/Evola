package evola.presentation.miniapp

import evola.core.kernel.LearnerId
import evola.learneridentity.application.RegisterLearnerCommand
import evola.learneridentity.application.RegisterLearnerHandler
import evola.learneridentity.domain.Channel
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.header
import io.ktor.server.response.respond

/**
 * Validates the caller's `initData` (same Telegram identity the bot already uses) and resolves
 * it to a [LearnerId] via the same [RegisterLearnerHandler] the bot uses — one canonical Learner
 * per Telegram user id regardless of which client (bot chat or Mini App) they're using.
 */
class MiniAppAuth(
    private val botToken: String,
    private val registerLearnerHandler: RegisterLearnerHandler,
) {
    /** Responds 401 and returns null when the request isn't a validly signed Telegram Mini App call. */
    suspend fun resolveLearnerId(call: ApplicationCall): LearnerId? {
        val header = call.request.header("Authorization")
        val user = TelegramInitDataValidator.validateAuthorizationHeader(header, botToken)
        if (user == null) {
            call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Missing or invalid Telegram initData"))
            return null
        }
        val displayName = user.username ?: user.firstName
        val learnerId = registerLearnerHandler.handle(
            RegisterLearnerCommand(channel = Channel.TELEGRAM, externalId = user.id.toString(), displayName = displayName),
        )
        return learnerId
    }
}
