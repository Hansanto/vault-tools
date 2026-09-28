package io.github.hansanto.vaulttools.search.output

import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.terminal.Terminal
import io.github.hansanto.vaulttools.commontest.listener.TerminalListener
import io.github.hansanto.vaulttools.search.render.Render
import io.github.hansanto.vaulttools.search.service.VaultPath
import io.github.hansanto.vaulttools.search.service.VaultPathSecretError
import io.github.hansanto.vaulttools.search.service.VaultPathSecretFound
import io.github.hansanto.vaulttools.search.service.VaultPathSecretNotFound
import io.github.hansanto.vaulttools.search.service.VaultSearchResult
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

class TerminalOutputTest :
    ShouldSpec({

        val terminalListener = TerminalListener()
        extension(terminalListener)

        lateinit var terminal: Terminal

        beforeTest {
            terminal = terminalListener.terminal
        }

        should("write nothing if results is empty") {
            val output = TerminalOutput(terminal = terminal, separator = "XXX", render = { _, _ ->
                "Never print"
            })

            output.write(emptyMap())
            terminalListener.terminalOutput().trim() shouldBe ""
        }

        for (namespace in listOf(
            null,
            "",
            "namespace1",
            "namespace2"
        )) {
            should("write all types of search result for specific namespace $namespace") {
                val output =
                    TerminalOutput(terminal = terminal, separator = "XXX", render = createRender())

                val results = mapOf(
                    namespace to VaultSearchResult(
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
                )

                output.write(results)

                terminalListener.terminalOutput().trim() shouldBe """
                    |Namespace: $namespace
                    |- path1: Not found
                    |- path2: My error
                    |- path3: Found with content {"key":1} at lines [1, 2]
                """.trimMargin()
            }
        }

        should("write support color and emoji in text") {
            val output = TerminalOutput(terminal = terminal, separator = "XXX", render = { _, _ ->
                "${TextColors.red("red")} ❤"
            })

            output.write(mapOf("namespace" to VaultSearchResult(emptyMap())))
            terminalListener.terminalOutput().trim() shouldBe "\u001B[31mred\u001B[39m ❤"
        }

        for ((separator, expectedOutput) in listOf(
            "\n\n" to """
                |null: 1 results
                |
                |: 2 results
                |
                |namespace1: 2 results
                |
                |namespace2: 1 results
            """.trimMargin(),
            "\n" to """
                |null: 1 results
                |: 2 results
                |namespace1: 2 results
                |namespace2: 1 results
            """.trimMargin(),
            "-" to "null: 1 results-: 2 results-namespace1: 2 results-namespace2: 1 results"
        )) {
            should("write all information for all namespaces") {
                val namespaceCapture = mutableListOf<String?>()
                val resultCapture = mutableListOf<VaultSearchResult>()

                val output =
                    TerminalOutput(terminal = terminal, separator = separator, render = { namespace, searchResult ->
                        namespaceCapture.add(namespace)
                        resultCapture.add(searchResult)
                        "$namespace: ${searchResult.result.size} results"
                    })

                val results = mapOf(
                    null to VaultSearchResult(
                        mapOf(
                            VaultPath("path1") to VaultPathSecretNotFound
                        )
                    ),
                    "" to VaultSearchResult(
                        mapOf(
                            VaultPath("path2") to VaultPathSecretNotFound,
                            VaultPath("path3") to VaultPathSecretError(Exception("Error in empty namespace")),
                        )
                    ),
                    "namespace1" to VaultSearchResult(
                        mapOf(
                            VaultPath("path4") to VaultPathSecretError(Exception("Error message")),
                            VaultPath("path5") to VaultPathSecretFound(
                                content = buildJsonObject {
                                    put("key", JsonPrimitive(1))
                                },
                                lines = listOf(1, 2)
                            )
                        )
                    ),
                    "namespace2" to VaultSearchResult(
                        mapOf(
                            VaultPath("path6") to VaultPathSecretFound(
                                content = buildJsonObject {
                                    put("key", JsonPrimitive(2))
                                },
                                lines = listOf(3, 4)
                            )
                        )
                    )
                )

                output.write(results)

                terminalListener.terminalOutput().trim() shouldBe expectedOutput
                namespaceCapture shouldBe results.keys.toList()
                resultCapture shouldBe results.values.toList()
            }
        }
    })

private fun createRender(): Render = Render { namespace, searchResult ->
    buildString {
        appendLine("Namespace: $namespace")
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
