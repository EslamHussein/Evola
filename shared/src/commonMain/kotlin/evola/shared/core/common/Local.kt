package evola.shared.core.common

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** Single fixed local "user" — the on-device app is single-user, but the ported schema/services keep
 * a user_id column so the port stayed near-mechanical. */
const val LOCAL_USER = "local"

@OptIn(ExperimentalUuidApi::class)
internal fun newId(): String = Uuid.random().toString()

internal fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()

internal val localJson = Json { ignoreUnknownKeys = true }
private val stringListSerializer = ListSerializer(String.serializer())

internal fun encodeStringList(list: List<String>): String = localJson.encodeToString(stringListSerializer, list)

internal fun decodeStringList(json: String?): List<String> =
    json?.let { runCatching { localJson.decodeFromString(stringListSerializer, it) }.getOrDefault(emptyList()) } ?: emptyList()
