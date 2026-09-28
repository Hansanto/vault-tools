package io.github.hansanto.vaulttools.search.service

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.maps.shouldContainExactly
import kotlinx.serialization.json.JsonObject

class VaultSearchResultTest :
    ShouldSpec({

        should("partition return empty maps when result is empty") {
            val (found, error) = VaultSearchResult(emptyMap()).partition()

            found.shouldContainExactly(emptyMap())
            error.shouldContainExactly(emptyMap())
        }

        should("partition return only found map when result contains only found secrets") {
            val secret1 = VaultPathSecretFound(content = JsonObject(emptyMap()), lines = listOf(1, 2))
            val secret2 = VaultPathSecretFound(content = JsonObject(emptyMap()), lines = listOf(3))

            val (found, error) = VaultSearchResult(
                mapOf(
                    VaultPath("path1") to secret1,
                    VaultPath("path2") to secret2
                )
            ).partition()

            found.shouldContainExactly(
                mapOf(
                    VaultPath("path1") to secret1,
                    VaultPath("path2") to secret2
                )
            )
            error.shouldContainExactly(emptyMap())
        }

        should("partition return only error map when result contains only errors") {
            val error1 = VaultPathSecretError(Exception("Error 1"))
            val error2 = VaultPathSecretError(Exception("Error 2"))

            val (found, error) = VaultSearchResult(
                mapOf(
                    VaultPath("path1") to error1,
                    VaultPath("path2") to error2
                )
            ).partition()

            found.shouldContainExactly(emptyMap())
            error.shouldContainExactly(
                mapOf(
                    VaultPath("path1") to error1,
                    VaultPath("path2") to error2
                )
            )
        }

        should("partition return both maps when result contains found secrets and errors") {
            val secret1 = VaultPathSecretFound(content = JsonObject(emptyMap()), lines = listOf(1, 2))
            val secret2 = VaultPathSecretFound(content = JsonObject(emptyMap()), lines = listOf(3))
            val error1 = VaultPathSecretError(Exception("Error 1"))
            val error2 = VaultPathSecretError(Exception("Error 2"))

            val (found, error) = VaultSearchResult(
                mapOf(
                    VaultPath("path1") to secret1,
                    VaultPath("path2") to error1,
                    VaultPath("path3") to secret2,
                    VaultPath("path4") to error2
                )
            ).partition()

            found.shouldContainExactly(
                mapOf(
                    VaultPath("path1") to secret1,
                    VaultPath("path3") to secret2
                )
            )
            error.shouldContainExactly(
                mapOf(
                    VaultPath("path2") to error1,
                    VaultPath("path4") to error2
                )
            )
        }
    })
