package evola.server

import com.anthropic.models.messages.Message

/** Shared by every worker that expects a strict-JSON response from the model (lesson segmentation,
 * vocabulary extraction, and grammar extraction in M7). */
internal fun extractModelText(response: Message): String? =
    response.content()
        .asSequence()
        .mapNotNull { block -> block.text().orElse(null)?.text() }
        .firstOrNull()
        .let { normalizeModelJsonText(it) }

/** The model occasionally wraps otherwise-valid JSON in markdown fences despite instructions not
 * to - strip them rather than failing the call over a formatting slip. */
internal fun normalizeModelJsonText(text: String?): String? =
    text?.trim()?.removePrefix("```json")?.removePrefix("```")?.removeSuffix("```")?.trim()
