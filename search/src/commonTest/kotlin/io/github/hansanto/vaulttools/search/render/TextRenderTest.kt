package io.github.hansanto.vaulttools.search.render

import com.github.ajalt.mordant.rendering.TextColors.gray
import com.github.ajalt.mordant.rendering.TextColors.green
import com.github.ajalt.mordant.rendering.TextColors.red
import com.github.ajalt.mordant.rendering.TextColors.yellow
import io.github.hansanto.kault.exception.VaultAPIException
import io.github.hansanto.vaulttools.search.service.VaultPath
import io.github.hansanto.vaulttools.search.service.VaultPathSecretError
import io.github.hansanto.vaulttools.search.service.VaultPathSecretFound
import io.github.hansanto.vaulttools.search.service.VaultPathSecretNotFound
import io.github.hansanto.vaulttools.search.service.VaultSearchResult
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

class TextRenderTest :
    ShouldSpec({

        lateinit var render: TextRender

        beforeEach {
            render = TextRender
        }

        should("renderString display minimum information when result is empty and namespace is null") {
            val result = render.renderString(namespace = null, searchResult = VaultSearchResult(emptyMap()))

            result shouldBe """
            Namespace: ${gray("null")}
            Total paths analyzed: ${gray("0")}
            Secret paths: ${green("0")}
            Error paths: ${red("0")}
            """.trimIndent()
        }

        should("renderString display minimum information when result is empty and namespace is not null") {
            val namespace = "test-namespace"
            val result = render.renderString(namespace = namespace, searchResult = VaultSearchResult(emptyMap()))

            result shouldBe """
            Namespace: $namespace
            Total paths analyzed: ${gray("0")}
            Secret paths: ${green("0")}
            Error paths: ${red("0")}
            """.trimIndent()
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

            result shouldBe """
            Namespace: ${gray("null")}
            Total paths analyzed: ${gray("3")}
            Secret paths: ${green("3")}
            - '${yellow("secret1")}' (L. 0, 1, 2, 3)
            - '${yellow("secret2")}' (L. 1, 5)
            - '${yellow("secret3")}' (L. 2, 4)
            Error paths: ${red("0")}
            """.trimIndent()
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

            result shouldBe """
            Namespace: ${gray("null")}
            Total paths analyzed: ${gray("3")}
            Secret paths: ${green("0")}
            Error paths: ${red("3")}
            - '${red("secret1")}' (${gray("Error 1")})
            - '${red("secret2")}' (${gray("Unknown error")})
            - '${red("secret3")}' (${gray("1 error occurred: * permission denied")})
            """.trimIndent()
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
            Namespace: ${gray("null")}
            Total paths analyzed: ${gray("3")}
            Secret paths: ${green("0")}
            Error paths: ${red("0")}
            """.trimIndent()
        }

        should("renderString display when all types of paths are present") {
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
                        ),
                        VaultPath("secret5") to VaultPathSecretNotFound,
                        VaultPath("secret6") to VaultPathSecretNotFound
                    )
                )
            )

            result shouldBe """
            Namespace: ${gray("null")}
            Total paths analyzed: ${gray("6")}
            Secret paths: ${green("2")}
            - '${yellow("secret1")}' (L. 1, 2, 3)
            - '${yellow("secret4")}' (L. 1, 5)
            Error paths: ${red("2")}
            - '${red("secret2")}' (${gray("Error 2")})
            - '${red("secret3")}' (${gray("1 error occurred: * permission denied")})
            """.trimIndent()
        }
    })
