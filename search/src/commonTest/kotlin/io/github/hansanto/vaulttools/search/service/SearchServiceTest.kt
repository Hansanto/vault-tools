package io.github.hansanto.vaulttools.search.service

import io.github.hansanto.kault.VaultClient
import io.github.hansanto.kault.auth.token.createToken
import io.github.hansanto.kault.engine.kv.v2.createOrUpdateSecret
import io.github.hansanto.kault.exception.VaultAPIException
import io.github.hansanto.kault.extension.toJsonPrimitive
import io.github.hansanto.vaulttools.common.UnlimitedExecutor
import io.github.hansanto.vaulttools.commontest.configureKV2
import io.github.hansanto.vaulttools.commontest.createVaultEnterpriseClient
import io.github.hansanto.vaulttools.commontest.listener.VaultListener
import io.github.hansanto.vaulttools.shared.util.randomString
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

class SearchServiceTest :
    ShouldSpec({

        extensions(VaultListener)

        lateinit var rootClient: VaultClient

        beforeSpec {
            rootClient = createVaultEnterpriseClient()
        }

        afterSpec {
            rootClient.close()
        }

        beforeTest {
            configureKV2(rootClient)
        }

        for (ignoreCase in listOf(true, false)) {
            should("return error when there is no secret [ignoreCase=$ignoreCase]") {
                val result = createService(rootClient).search(
                    rootPath = "",
                    searchString = randomString(),
                    ignoreCase = ignoreCase
                ).result

                assertResult(
                    searchResult = VaultSearchResult(result),
                    // Exception because there is no secret at the root path
                    expected = mapOf(
                        "" to VaultPathSecretError(VaultAPIException())
                    )
                )
            }
        }

        should("return permission error when there is no permission to access secrets") {
            val searchString = "value"
            val secret = buildJsonObject {
                put("key", searchString)
            }
            writeSecret(rootClient, "secret1", secret)
            writeSecret(rootClient, "folder/secret2", secret)
            writeSecret(rootClient, "folder/secret3", secret)

            rootClient.system.policy.createOrUpdate(
                "no-root-list-policy",
                """
                    path "secret/metadata" {        
                        capabilities = ["list"]   
                    }
                    
                    path "secret/data/secret1" {        
                        capabilities = ["read"]   
                    }
                    
                    path "secret/metadata/folder" {        
                        capabilities = ["list"]   
                    }
                    
                    path "secret/data/folder/secret2" {        
                        capabilities = ["read"]   
                    }
                """.trimIndent()
            )

            val noPermissionToken = rootClient.auth.token.createToken {
                noDefaultPolicy = true
                policies = listOf("no-root-list-policy")
            }

            createVaultEnterpriseClient {
                setTokenString(noPermissionToken.clientToken)
            }.apply {
                configureKV2(this)
            }.use { clientWithoutPermission ->
                val result = createService(clientWithoutPermission).search(
                    rootPath = "",
                    searchString = searchString,
                    ignoreCase = false
                ).result

                assertResult(
                    searchResult = VaultSearchResult(result),
                    expected = mapOf(
                        "secret1" to VaultPathSecretFound(
                            content = secret,
                            lines = listOf(1)
                        ),
                        "folder/secret2" to VaultPathSecretFound(
                            content = secret,
                            lines = listOf(1)
                        ),
                        "folder/secret3" to VaultPathSecretError(
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

                clientWithoutPermission.close()
            }
        }

        for (ignoreCase in listOf(true, false)) {
            should("return empty result when search string does not match any secret [ignoreCase=$ignoreCase]") {
                repeat(3) { i ->
                    writeSecret(rootClient, "$i", mapOf("key" to "value"))
                }

                val result = createService(rootClient).search(
                    rootPath = "",
                    searchString = "non-matching",
                    ignoreCase = ignoreCase
                )

                assertResult(
                    searchResult = result,
                    expected = mapOf(
                        "0" to VaultPathSecretNotFound,
                        "1" to VaultPathSecretNotFound,
                        "2" to VaultPathSecretNotFound
                    )
                )
            }
        }

        should("return all secrets when search string is empty") {
            val secretPath1 = buildJsonObject {
                put("key", "value")
            }
            writeSecret(rootClient, "path1", secretPath1)
            val secretPath2 = buildJsonObject {
                put("key2", "value2")
                put("key3", "value3")
            }
            writeSecret(rootClient, "folder/path2", secretPath2)

            val result = createService(rootClient).search(
                rootPath = "",
                searchString = "",
                ignoreCase = false
            )

            assertResult(
                searchResult = result,
                expected = mapOf(
                    "path1" to VaultPathSecretFound(
                        content = secretPath1,
                        lines = listOf(0, 1, 2)
                    ),
                    "folder/path2" to VaultPathSecretFound(
                        content = secretPath2,
                        lines = listOf(0, 1, 2, 3)
                    )
                )
            )
        }

        for (primitive in listOf(
            JsonPrimitive(42),
            JsonPrimitive(true),
            JsonPrimitive(null),
            JsonPrimitive(123.45),
            JsonPrimitive("string value")
        )) {
            should("find secret when value is a primitive type: ${primitive.content}") {
                assertPrimitiveType(rootClient, primitive)
            }
        }

        should("find secret in different json types") {
            val searchString = randomString()
            val secretPath = randomString()
            val secret = buildJsonObject {
                put("key1", searchString)
                putJsonArray("key2") {
                    add(searchString.toJsonPrimitive())
                    add("other value".toJsonPrimitive())
                }
                putJsonObject("key3") {
                    put("subKey", searchString)
                }
            }
            writeSecret(
                rootClient,
                secretPath,
                secret
            )

            val result = createService(rootClient).search(
                rootPath = "",
                searchString = searchString,
                ignoreCase = false
            )

            assertResult(
                searchResult = result,
                expected = mapOf(
                    secretPath to VaultPathSecretFound(
                        content = secret,
                        lines = listOf(1, 3, 7)
                    )
                )
            )
        }

        for (ignoreCase in listOf(true, false)) {
            should("find secret surrounded by characters [ignoreCase=$ignoreCase]") {
                val searchString = "value"

                val secretPath = randomString()
                val secret = buildJsonObject {
                    put("key1", "prefix${searchString}suffix")
                    put("key2", "$searchString suffix")
                    put("key3", "prefix $searchString")
                }

                writeSecret(rootClient, secretPath, secret)

                val result = createService(rootClient).search(
                    rootPath = "",
                    searchString = if (ignoreCase) searchString.uppercase() else searchString,
                    ignoreCase = ignoreCase
                )

                assertResult(
                    searchResult = result,
                    expected = mapOf(
                        secretPath to VaultPathSecretFound(
                            content = secret,
                            lines = listOf(1, 2, 3)
                        )
                    )
                )
            }
        }

        for (ignoreCase in listOf(true, false)) {
            should("find secret with only one property [ignoreCase=$ignoreCase]") {
                val searchString = "value1"

                val secretPath = randomString()
                val secret = buildJsonObject {
                    put("key1", searchString)
                }

                writeSecret(rootClient, secretPath, secret)

                val result = createService(rootClient).search(
                    rootPath = "",
                    searchString = if (ignoreCase) searchString.uppercase() else searchString,
                    ignoreCase = ignoreCase
                )

                assertResult(
                    searchResult = result,
                    expected = mapOf(
                        secretPath to VaultPathSecretFound(
                            content = secret,
                            lines = listOf(1)
                        )
                    )
                )
            }
        }

        should("find secret when stored inside sub json object") {
            val searchString = "value1"

            val secretPath = randomString()
            val secret = buildJsonObject {
                putJsonObject("key1") {
                    put("subKey1", searchString)
                }
            }

            writeSecret(rootClient, secretPath, secret)

            val result = createService(rootClient).search(
                rootPath = "",
                searchString = searchString,
                ignoreCase = false
            )

            assertResult(
                searchResult = result,
                expected = mapOf(
                    secretPath to VaultPathSecretFound(
                        content = secret,
                        lines = listOf(2)
                    )
                )
            )
        }

        for (ignoreCase in listOf(true, false)) {
            should("find secret with several properties at the root path [ignoreCase=$ignoreCase]") {
                val searchString = "value2"

                val secretPath = randomString()
                val secret = buildJsonObject {
                    put("key1", "value1")
                    put("key2", searchString)
                    put("key3", "value3")
                }

                writeSecret(rootClient, secretPath, secret)

                val result = createService(rootClient).search(
                    rootPath = "",
                    searchString = if (ignoreCase) searchString.uppercase() else searchString,
                    ignoreCase = ignoreCase
                )

                assertResult(
                    searchResult = result,
                    expected = mapOf(
                        secretPath to VaultPathSecretFound(
                            content = secret,
                            lines = listOf(2)
                        )
                    )
                )
            }
        }

        for (ignoreCase in listOf(true, false)) {
            should("find secret when key contains the search string [ignoreCase=$ignoreCase]") {
                val searchString = "key3"

                val secretPath = randomString()
                val secret = buildJsonObject {
                    put("key1", "value1")
                    put("key2", "value2")
                    put("key3", "value3")
                }

                writeSecret(rootClient, secretPath, secret)

                val result = createService(rootClient).search(
                    rootPath = "",
                    searchString = if (ignoreCase) searchString.uppercase() else searchString,
                    ignoreCase = ignoreCase
                )

                assertResult(
                    searchResult = result,
                    expected = mapOf(
                        secretPath to VaultPathSecretFound(
                            content = secret,
                            lines = listOf(3)
                        )
                    )
                )
            }
        }

        should("ignore path that are before the root path") {
            val searchString = "value"
            val secret = buildJsonObject {
                put("key", searchString)
            }
            writeSecret(rootClient, "secret", secret)
            writeSecret(rootClient, "folder1/secret", secret)
            writeSecret(rootClient, "folder2/secret", secret)

            for (rootPath in listOf("folder1", "folder1/", "folder2")) {
                val result = createService(rootClient).search(
                    rootPath = rootPath,
                    searchString = searchString,
                    ignoreCase = false
                )

                val expectedPath = if (rootPath.endsWith("/")) rootPath + "secret" else "$rootPath/secret"

                assertResult(
                    searchResult = result,
                    expected = mapOf(
                        expectedPath to VaultPathSecretFound(
                            content = secret,
                            lines = listOf(1)
                        )
                    )
                )
            }
        }

        for (ignoreCase in listOf(true, false)) {
            should("find secrets in multiple sub-folders path [ignoreCase=$ignoreCase]") {
                val searchString = randomString()
                val expectedPaths = listOf(
                    "secret",
                    "secret2",
                    "folder1/secret1",
                    "folder1/secret2",
                    "folder2/sub/secret3"
                )
                val secret = buildJsonObject {
                    put("key", searchString)
                }
                expectedPaths.forEach { path ->
                    writeSecret(rootClient, path, secret)
                }

                writeSecret(rootClient, "folder/ignored", mapOf("key" to "ignored value"))

                val result = createService(rootClient).search(
                    rootPath = "",
                    searchString = if (ignoreCase) searchString.uppercase() else searchString,
                    ignoreCase = ignoreCase
                )

                assertResult(
                    searchResult = result,
                    expected = expectedPaths.associateWith {
                        VaultPathSecretFound(
                            content = secret,
                            lines = listOf(1)
                        )
                    } + listOf(
                        "folder/ignored" to VaultPathSecretNotFound
                    )
                )
            }
        }

        should("find secrets inside a specific namespace") {
            val searchString = randomString()
            val path = randomString()

            val namespace1 = randomString()
            val namespace2 = randomString()

            rootClient.system.namespaces.apply {
                create(namespace1)
                create(namespace2)
            }

            writeSecret(rootClient, path, mapOf("key" to searchString))

            createVaultEnterpriseClient(namespace = namespace1).use { client1 ->
                configureKV2(client1)
                val secretNamespace1 = buildJsonObject {
                    put("key1", searchString)
                }
                writeSecret(client1, path, secretNamespace1)

                createVaultEnterpriseClient(namespace = namespace2).use { client2 ->
                    configureKV2(client2)
                    val secretNamespace2 = buildJsonObject {
                        put("key2", searchString)
                    }
                    writeSecret(client2, path, secretNamespace2)

                    val result = createService(client1).search(
                        rootPath = "",
                        searchString = searchString,
                        ignoreCase = false
                    )

                    assertResult(
                        searchResult = result,
                        expected = mapOf(
                            path to VaultPathSecretFound(
                                content = secretNamespace1,
                                lines = listOf(1)
                            )
                        )
                    )

                    val result2 = createService(client2).search(
                        rootPath = "",
                        searchString = searchString,
                        ignoreCase = false
                    )

                    assertResult(
                        searchResult = result2,
                        expected = mapOf(
                            path to VaultPathSecretFound(
                                content = secretNamespace2,
                                lines = listOf(1)
                            )
                        )
                    )
                }
            }
        }

        should("find secrets inline when json is minified") {
            val searchString = randomString()
            val secretPath = randomString()
            val secret = buildJsonObject {
                put("key1", searchString)
                put("key2", searchString)
                putJsonObject("key3") {
                    put("subKey", searchString)
                }
            }
            writeSecret(rootClient, secretPath, secret)

            val result = createService(rootClient, Json(from = VaultClient.json) { prettyPrint = false }).search(
                rootPath = "",
                searchString = searchString,
                ignoreCase = false
            )

            assertResult(
                searchResult = result,
                expected = mapOf(
                    secretPath to VaultPathSecretFound(
                        content = secret,
                        lines = listOf(0)
                    )
                )
            )
        }
    })

private suspend fun assertPrimitiveType(rootClient: VaultClient, value: JsonPrimitive) {
    val secretPath = randomString()
    val secret = buildJsonObject {
        put("key", value)
    }
    writeSecret(rootClient, secretPath, secret)

    val result = createService(rootClient).search(
        rootPath = "",
        searchString = value.toString(),
        ignoreCase = false
    )

    assertResult(
        searchResult = result,
        expected = mapOf(
            secretPath to VaultPathSecretFound(
                content = secret,
                lines = listOf(1)
            )
        )
    )
}

private suspend inline fun <reified T> writeSecret(client: VaultClient, path: String, data: T) {
    client.secret.kv2.createOrUpdateSecret(path) {
        data(data)
    }
}

private fun createService(client: VaultClient, json: Json = SearchService.pretty_json): SearchService = SearchService(
    client.secret.kv2,
    UnlimitedExecutor,
    json = json
)

fun assertResult(searchResult: VaultSearchResult, expected: Map<String, VaultPathSecret>) {
    val result = searchResult.result
    result.size shouldBe expected.size
    result.keys.map { it.value } shouldContainExactlyInAnyOrder expected.keys

    result.forEach { (path, secret) ->
        val secretPath = expected[path.value] ?: error("Unexpected path in result: ${path.value}")
        when (secretPath) {
            is VaultPathSecretError -> {
                secret.shouldBeInstanceOf<VaultPathSecretError>()
                val secretError = secret
                secretError.error.shouldBeInstanceOf<VaultAPIException>()
                secretError.error.message?.trim() shouldBe secretPath.error.message
            }

            is VaultPathSecretFound -> {
                secret.shouldBeInstanceOf<VaultPathSecretFound>()
                val secretFound = secret
                secretFound.content shouldBe secretPath.content
                secretFound.lines shouldContainExactlyInAnyOrder secretPath.lines
            }

            is VaultPathSecretNotFound -> {
                secret.shouldBeInstanceOf<VaultPathSecretNotFound>()
            }
        }
    }
}
