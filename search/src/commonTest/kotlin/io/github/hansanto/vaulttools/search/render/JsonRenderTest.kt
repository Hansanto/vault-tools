package io.github.hansanto.vaulttools.search.render

import io.github.hansanto.kault.exception.VaultAPIException
import io.github.hansanto.vaulttools.search.service.VaultPath
import io.github.hansanto.vaulttools.search.service.VaultPathSecretError
import io.github.hansanto.vaulttools.search.service.VaultPathSecretFound
import io.github.hansanto.vaulttools.search.service.VaultPathSecretNotFound
import io.github.hansanto.vaulttools.search.service.VaultSearchResult
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

class JsonRenderTest :
    ShouldSpec({

        lateinit var render: JsonRender

        beforeEach {
            render = JsonRender
        }

        should("renderString display minimum information when result is empty and namespace is null") {
            val result = render.renderString(namespace = null, searchResult = VaultSearchResult(emptyMap()))
            result shouldEqualJson """
            |{
            |    "namespace": null,
            |    "totalPathsAnalyzed": 0,
            |    "secretPaths": {},
            |    "errorPaths": {}
            |}
            """.trimMargin()
        }

        should("renderString display minimum information when result is empty and namespace is not null") {
            val namespace = "test-namespace"
            val result = render.renderString(namespace = namespace, searchResult = VaultSearchResult(emptyMap()))

            result shouldEqualJson """
            |{
            |    "namespace": "$namespace",
            |    "totalPathsAnalyzed": 0,
            |    "secretPaths": {},
            |    "errorPaths": {}
            |}
            """.trimMargin()
        }

        should("renderString display when only secret paths are present") {
            val result = render.renderString(
                namespace = null,
                searchResult = VaultSearchResult(
                    mapOf(
                        VaultPath("secret1") to VaultPathSecretFound(
                            content = JsonObject(emptyMap()),
                            lines = listOf(0, 1, 2, 3)
                        ),
                        VaultPath("secret2") to VaultPathSecretFound(
                            content = buildJsonObject {
                                put("key1", JsonPrimitive("value1"))
                                put("key2", JsonPrimitive("value2"))
                            },
                            lines = listOf(1, 5)
                        ),
                        VaultPath("secret3") to VaultPathSecretFound(
                            content = buildJsonObject {
                                put("key", JsonPrimitive("value"))
                            },
                            lines = listOf(2, 4)
                        )
                    )
                )
            )

            result shouldEqualJson """
            |{
            |    "namespace": null,
            |    "totalPathsAnalyzed": 3,
            |    "secretPaths": {
            |        "secret1": [0, 1, 2, 3],
            |        "secret2": [1, 5],
            |        "secret3": [2, 4]
            |    },
            |    "errorPaths": {}
            |}
            """.trimMargin()
        }

        should("renderString display when only error paths are present") {
            val result = render.renderString(
                namespace = null,
                searchResult = VaultSearchResult(
                    mapOf(
                        VaultPath("secret1") to VaultPathSecretError(Exception("Error 1")),
                        VaultPath("secret2") to VaultPathSecretError(VaultAPIException()),
                        VaultPath("secret3") to VaultPathSecretError(
                            VaultAPIException(
                                listOf(
                                    """
                                |1 error occurred:
                                |	* permission denied
                                    """.trimMargin()
                                )
                            )
                        )
                    )
                )
            )

            result shouldEqualJson """
            |{
            |    "namespace": null,
            |    "totalPathsAnalyzed": 3,
            |    "secretPaths": {},
            |    "errorPaths": {
            |        "secret1": "Error 1",
            |        "secret2": "Unknown error",
            |        "secret3": "1 error occurred: * permission denied"
            |    }
            |}
            """.trimMargin()
        }

        should("renderString display when only not found paths are present") {
            val result = render.renderString(
                namespace = null,
                searchResult = VaultSearchResult(
                    mapOf(
                        VaultPath("secret1") to VaultPathSecretNotFound,
                        VaultPath("secret2") to VaultPathSecretNotFound,
                        VaultPath("secret3") to VaultPathSecretNotFound
                    )
                )
            )

            result shouldBe """
            |{
            |    "namespace": null,
            |    "totalPathsAnalyzed": 3,
            |    "secretPaths": {},
            |    "errorPaths": {}
            |}
            """.trimMargin()
        }

        should("renderString display when both secret and error paths are present") {
            val result = render.renderString(
                namespace = null,
                searchResult = VaultSearchResult(
                    mapOf(
                        VaultPath("secret1") to VaultPathSecretFound(
                            content = JsonObject(emptyMap()),
                            lines = listOf(1, 2, 3)
                        ),
                        VaultPath("secret2") to VaultPathSecretError(Exception("Error 2")),
                        VaultPath("secret3") to VaultPathSecretError(
                            VaultAPIException(
                                listOf(
                                    """
                            |1 error occurred:
                            |	* permission denied
                                    """.trimMargin()
                                )
                            )
                        ),
                        VaultPath("secret4") to VaultPathSecretFound(
                            content = JsonObject(emptyMap()),
                            lines = listOf(1, 5)
                        )
                    )
                )
            )

            result shouldEqualJson """
            |{
            |    "namespace": null,
            |    "totalPathsAnalyzed": 4,
            |    "secretPaths": {
            |        "secret1": [1, 2, 3],
            |        "secret4": [1, 5]
            |    },
            |    "errorPaths": {
            |        "secret2": "Error 2",
            |        "secret3": "1 error occurred: * permission denied"
            |    }
            |}
            """.trimMargin()
        }
    })
