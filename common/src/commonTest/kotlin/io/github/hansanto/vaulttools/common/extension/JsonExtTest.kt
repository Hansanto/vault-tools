package io.github.hansanto.vaulttools.common.extension

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.matchers.types.shouldNotBeSameInstanceAs
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class JsonExtTest :
    ShouldSpec({

        should("JsonObject walk return empty object if the object is empty") {
            val json = buildJsonObject { }
            val result = json.walk { _, _ ->
                error("should not be called")
            }
            result shouldBe JsonObject(emptyMap())
        }

        should("JsonObject walk apply modifier on primitive values") {
            val jsonPrimitiveString = JsonPrimitive("value")
            val jsonPrimitiveNumber = JsonPrimitive(42)
            val jsonPrimitiveBoolean = JsonPrimitive(true)
            val jsonPrimitiveNull = JsonPrimitive(null)
            val json = buildJsonObject {
                put("string", jsonPrimitiveString)
                put("number", jsonPrimitiveNumber)
                put("boolean", jsonPrimitiveBoolean)
                put("null", jsonPrimitiveNull)
            }

            var counter = 0
            val result = json.walk { key, value ->
                when (counter++) {
                    0 -> {
                        key shouldBe "string"
                        value.jsonPrimitive shouldBeSameInstanceAs jsonPrimitiveString
                    }

                    1 -> {
                        key shouldBe "number"
                        value.jsonPrimitive shouldBeSameInstanceAs jsonPrimitiveNumber
                    }

                    2 -> {
                        key shouldBe "boolean"
                        value.jsonPrimitive shouldBeSameInstanceAs jsonPrimitiveBoolean
                    }

                    3 -> {
                        key shouldBe "null"
                        value.jsonPrimitive shouldBeSameInstanceAs jsonPrimitiveNull
                    }

                    else -> error("should not be called more than 4 times")
                }
                JsonPrimitive("modified - ${value.jsonPrimitive.content}")
            }

            result shouldBe buildJsonObject {
                put("string", JsonPrimitive("modified - value"))
                put("number", JsonPrimitive("modified - 42"))
                put("boolean", JsonPrimitive("modified - true"))
                put("null", JsonPrimitive("modified - null"))
            }
        }

        should("JsonObject walk apply modifier on array values") {
            val json = buildJsonObject {
                put(
                    "array",
                    buildJsonArray {
                        add(JsonPrimitive("value"))
                        add(JsonPrimitive(42))
                    }
                )
            }

            val result = json.walk { key, value ->
                key shouldBe null
                JsonPrimitive("modified - ${value.jsonPrimitive.content}")
            }

            result shouldBe buildJsonObject {
                put(
                    "array",
                    buildJsonArray {
                        add(JsonPrimitive("modified - value"))
                        add(JsonPrimitive("modified - 42"))
                    }
                )
            }
        }

        should("JsonObject walk apply modifier on nested objects") {
            val json = buildJsonObject {
                put(
                    "object",
                    buildJsonObject {
                        put("string", JsonPrimitive("value"))
                    }
                )
            }

            val result = json.walk { key, value ->
                key shouldBe "string"
                JsonPrimitive("modified - ${value.jsonPrimitive.content}")
            }

            result shouldBe JsonObject(
                mapOf(
                    "object" to JsonObject(
                        mapOf(
                            "string" to JsonPrimitive("modified - value")
                        )
                    )
                )
            )
        }

        should("JsonObject walk apply on nested objects inside arrays") {
            val json = buildJsonObject {
                put(
                    "array",
                    buildJsonArray {
                        add(
                            buildJsonObject {
                                put("string", JsonPrimitive("value"))
                            }
                        )
                    }
                )
            }

            val result = json.walk { key, value ->
                key shouldBe "string"
                JsonPrimitive("modified - ${value.jsonPrimitive.content}")
            }

            result shouldBe buildJsonObject {
                put(
                    "array",
                    buildJsonArray {
                        add(
                            buildJsonObject {
                                put("string", JsonPrimitive("modified - value"))
                            }
                        )
                    }
                )
            }
        }

        should("JsonObject walk keep the same reference for unchanged objects") {
            val jsonPrimitiveString = JsonPrimitive("value")
            val jsonPrimitiveNumber = JsonPrimitive(42)
            val jsonPrimitiveBoolean = JsonPrimitive(true)
            val jsonPrimitiveNull = JsonPrimitive(null)

            val jsonArrayPrimitiveString = JsonPrimitive("array value")
            val jsonArrayPrimitiveNumber = JsonPrimitive(43)
            val jsonArray = buildJsonArray {
                add(jsonArrayPrimitiveString)
                add(jsonArrayPrimitiveNumber)
            }

            val jsonNestedPrimitiveString = JsonPrimitive("nested value")
            val jsonObject = buildJsonObject {
                put("string", jsonNestedPrimitiveString)
            }

            val json = buildJsonObject {
                put("string", jsonPrimitiveString)
                put("number", jsonPrimitiveNumber)
                put("boolean", jsonPrimitiveBoolean)
                put("null", jsonPrimitiveNull)
                put("array", jsonArray)
                put("object", jsonObject)
            }

            val result = json.walk { _, value ->
                value
            }

            result["string"] shouldBeSameInstanceAs jsonPrimitiveString
            result["number"] shouldBeSameInstanceAs jsonPrimitiveNumber
            result["boolean"] shouldBeSameInstanceAs jsonPrimitiveBoolean
            result["null"] shouldBeSameInstanceAs jsonPrimitiveNull

            result["array"] shouldNotBeSameInstanceAs jsonArray
            result["array"]!!.jsonArray[0] shouldBeSameInstanceAs jsonArrayPrimitiveString
            result["array"]!!.jsonArray[1] shouldBeSameInstanceAs jsonArrayPrimitiveNumber

            result["object"] shouldNotBeSameInstanceAs jsonObject
            result["object"]!!.jsonObject["string"] shouldBeSameInstanceAs jsonNestedPrimitiveString
        }

        should("JsonArray walk return empty array if the array is empty") {
            val json = buildJsonArray { }
            val result = json.walk { _, _ ->
                error("should not be called")
            }
            result shouldBe buildJsonArray { }
        }

        should("JsonArray walk apply modifier on primitive values") {
            val jsonPrimitiveString = JsonPrimitive("value")
            val jsonPrimitiveNumber = JsonPrimitive(42)
            val jsonPrimitiveBoolean = JsonPrimitive(true)
            val jsonPrimitiveNull = JsonPrimitive(null)
            val json = buildJsonArray {
                add(jsonPrimitiveString)
                add(jsonPrimitiveNumber)
                add(jsonPrimitiveBoolean)
                add(jsonPrimitiveNull)
            }

            var counter = 0
            val result = json.walk { key, value ->
                key shouldBe null
                when (counter++) {
                    0 -> value.jsonPrimitive shouldBeSameInstanceAs jsonPrimitiveString
                    1 -> value.jsonPrimitive shouldBeSameInstanceAs jsonPrimitiveNumber
                    2 -> value.jsonPrimitive shouldBeSameInstanceAs jsonPrimitiveBoolean
                    3 -> value.jsonPrimitive shouldBeSameInstanceAs jsonPrimitiveNull
                    else -> error("should not be called more than 4 times")
                }
                JsonPrimitive("modified - ${value.jsonPrimitive.content}")
            }

            result shouldBe buildJsonArray {
                add(JsonPrimitive("modified - value"))
                add(JsonPrimitive("modified - 42"))
                add(JsonPrimitive("modified - true"))
                add(JsonPrimitive("modified - null"))
            }
        }

        should("JsonArray walk apply modifier on nested objects") {
            val json = buildJsonArray {
                add(
                    buildJsonObject {
                        put("string", JsonPrimitive("value"))
                    }
                )
            }

            val result = json.walk { key, value ->
                key shouldBe "string"
                JsonPrimitive("modified - ${value.jsonPrimitive.content}")
            }

            result shouldBe buildJsonArray {
                add(
                    buildJsonObject {
                        put("string", JsonPrimitive("modified - value"))
                    }
                )
            }
        }
    })
