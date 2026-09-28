package io.github.hansanto.vaulttools.search.render

import io.github.hansanto.vaulttools.search.service.VaultPath
import io.github.hansanto.vaulttools.search.service.VaultPathSecretError
import io.github.hansanto.vaulttools.search.service.VaultPathSecretFound
import io.github.hansanto.vaulttools.search.service.VaultPathSecretNotFound
import io.github.hansanto.vaulttools.search.service.VaultSearchResult
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

class CompoundRenderTest :
    ShouldSpec({

        should("render empty string when no renderers are provided") {
            val compoundRender = CompoundRender(emptyList(), separator = "XXX")
            val result = compoundRender.renderString(
                namespace = "",
                searchResult = VaultSearchResult(
                    mapOf(VaultPath("secret1") to VaultPathSecretNotFound)
                )
            )
            result shouldBe ""
        }

        for (namespace in listOf(null, "", "namespace1", "namespace2")) {
            should("render with single renderer when only one renderer is provided (namespace: $namespace)") {
                val singleRender = createRender("Single Renderer")

                val compoundRender = CompoundRender(listOf(singleRender), separator = "XXX")
                val result = compoundRender.renderString(
                    namespace = namespace,
                    searchResult = getVaultSearchResult()
                )
                result shouldBe """
                    |Single Renderer - Namespace: $namespace
                    |- path1: Not found
                    |- path2: My error
                    |- path3: Found with content {"key":1} at lines [1, 2]
                """.trimMargin()
            }

            for ((separator, expectedOutput) in listOf(
                "" to """
                    |Renderer 1 - Namespace: $namespace
                    |- path1: Not found
                    |- path2: My error
                    |- path3: Found with content {"key":1} at lines [1, 2]Renderer 2 - Namespace: $namespace
                    |- path1: Not found
                    |- path2: My error
                    |- path3: Found with content {"key":1} at lines [1, 2]
                """.trimMargin(),
                "\n" to """
                    |Renderer 1 - Namespace: $namespace
                    |- path1: Not found
                    |- path2: My error
                    |- path3: Found with content {"key":1} at lines [1, 2]
                    |Renderer 2 - Namespace: $namespace
                    |- path1: Not found
                    |- path2: My error
                    |- path3: Found with content {"key":1} at lines [1, 2]
                """.trimMargin()
            )) {
                should(
                    "render with multiple renderers when multiple renderers are provided (namespace: $namespace, separator: '$separator')"
                ) {
                    val renderer1 = createRender("Renderer 1")
                    val renderer2 = createRender("Renderer 2")

                    val compoundRender = CompoundRender(listOf(renderer1, renderer2), separator = separator)
                    val result = compoundRender.renderString(
                        namespace = namespace,
                        searchResult = getVaultSearchResult()
                    )
                    result shouldBe expectedOutput
                }
            }
        }
    })

private fun createRender(prefix: String): Render = Render { namespace, searchResult ->
    buildString {
        appendLine("$prefix - Namespace: $namespace")
        val (pathsSecretFound, pathsSecretError, pathsNotFound) = searchResult.partition()
        pathsNotFound.forEach { path ->
            appendLine("- ${path.value}: Not found")
        }
        pathsSecretError.forEach { (path, error) ->
            appendLine("- ${path.value}: ${error.error.message}")
        }
        pathsSecretFound.forEach { (path, secret) ->
            appendLine("- ${path.value}: Found with content ${secret.content} at lines ${secret.lines}")
        }
        setLength(length - 1)
    }
}

private fun getVaultSearchResult(): VaultSearchResult = VaultSearchResult(
    mapOf(
        VaultPath("path1") to VaultPathSecretNotFound,
        VaultPath("path2") to VaultPathSecretError(Exception("My error")),
        VaultPath("path3") to VaultPathSecretFound(
            content = buildJsonObject {
                put("key", JsonPrimitive(1))
            },
            lines = listOf(1, 2)
        )
    )
)
