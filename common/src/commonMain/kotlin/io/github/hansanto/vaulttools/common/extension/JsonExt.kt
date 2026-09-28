package io.github.hansanto.vaulttools.common.extension

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.contracts.contract

/**
 * Walk through the JSON object and apply the modifier function on each primitive value.
 *
 * @param modifier Function that takes the key and value of a JSON element and returns a modified JSON element.
 * @receiver The JSON object to walk through.
 * @return A new JSON object with the same structure as the original, but with the primitive values modified by the modifier function.
 */
fun JsonObject.walk(modifier: (String?, JsonPrimitive) -> JsonPrimitive): JsonObject {
    contract {
        callsInPlace(modifier, kotlin.contracts.InvocationKind.UNKNOWN)
    }

    return JsonObject(
        mapValues { (key, value) ->
            when (value) {
                is JsonObject -> value.walk(modifier)
                is JsonArray -> value.walk(modifier)
                is JsonPrimitive -> modifier(key, value)
            }
        }
    )
}

/**
 * Walk through the JSON array and apply the modifier function on each primitive value.
 *
 * @param modifier Function that takes the key and value of a JSON element and returns a modified JSON element.
 * @receiver The JSON array to walk through.
 * @return A new JSON array with the same structure as the original, but with the primitive values modified by the modifier function.
 */
fun JsonArray.walk(modifier: (String?, JsonPrimitive) -> JsonPrimitive): JsonArray {
    contract {
        callsInPlace(modifier, kotlin.contracts.InvocationKind.UNKNOWN)
    }

    return JsonArray(
        map { element ->
            when (element) {
                is JsonObject -> element.walk(modifier)
                is JsonArray -> element.walk(modifier)
                is JsonPrimitive -> modifier(null, element)
            }
        }
    )
}
