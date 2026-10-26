package octo.adapters.base

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

// Agent output is untrusted: every accessor returns null on a missing or mistyped field instead of
// throwing, so one odd message cannot end a session.

fun parseJsonObject(line: String): JsonObject? =
    try {
        Json.parseToJsonElement(line) as? JsonObject
    } catch (_: SerializationException) {
        null
    }

fun JsonElement?.obj(): JsonObject? = this as? JsonObject

fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

fun JsonObject.array(key: String): List<JsonElement> = (this[key] as? JsonArray).orEmpty()

fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

fun JsonObject.bool(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull

fun JsonObject.long(key: String): Long? = (this[key] as? JsonPrimitive)?.longOrNull

fun JsonObject.double(key: String): Double? = (this[key] as? JsonPrimitive)?.doubleOrNull
