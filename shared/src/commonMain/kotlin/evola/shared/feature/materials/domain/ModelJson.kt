package evola.shared.feature.materials.domain

import kotlinx.serialization.json.Json

internal val extractionJson = Json { ignoreUnknownKeys = true; isLenient = true }

/** The model occasionally wraps otherwise-valid JSON in markdown fences despite instructions not to
 * — strip them rather than failing the call over a formatting slip (ported from the server's
 * `normalizeModelJsonText`). */
internal fun normalizeModelJson(text: String): String =
    text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
