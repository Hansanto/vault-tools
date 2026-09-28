package io.github.vaulttools.search.command

import com.github.ajalt.clikt.command.parse
import com.github.ajalt.clikt.command.test
import com.github.ajalt.clikt.core.BadParameterValue
import com.github.ajalt.clikt.core.MissingArgument
import com.github.ajalt.clikt.core.MissingOption
import io.github.hansanto.kault.VaultClient
import io.github.hansanto.kault.auth.token.createToken
import io.github.hansanto.kault.engine.kv.v2.createOrUpdateSecret
import io.github.hansanto.vaulttools.commontest.KeycloakUtil
import io.github.hansanto.vaulttools.commontest.ROOT_TOKEN
import io.github.hansanto.vaulttools.commontest.VAULT_ENTERPRISE_URL
import io.github.hansanto.vaulttools.commontest.configureKV2
import io.github.hansanto.vaulttools.commontest.configureOIDC
import io.github.hansanto.vaulttools.commontest.createVaultEnterpriseClient
import io.github.hansanto.vaulttools.commontest.deleteAllKV2Secrets
import io.github.hansanto.vaulttools.commontest.listener.PlaywrightOIDCFlowListener
import io.github.hansanto.vaulttools.commontest.listener.VaultListener
import io.github.hansanto.vaulttools.commontest.readJson
import io.github.hansanto.vaulttools.commontest.toMountsEnableSecretsEnginePayload
import io.github.hansanto.vaulttools.search.command.SearchCommand
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds

private const val SECRET_ENGINE = "secret"

data class TerminalConfig(val text: Boolean, val json: Boolean, val expected: String)

class SearchCommandTest :
    ShouldSpec({

        val playwrightListener = PlaywrightOIDCFlowListener()
        extensions(playwrightListener, VaultListener)

        lateinit var rootClient: VaultClient
        lateinit var command: SearchCommand

        beforeSpec {
            rootClient = createVaultEnterpriseClient()
        }

        afterSpec {
            rootClient.close()
        }

        beforeTest {
            configureKV2(rootClient)
            configureOIDC(rootClient)
            initializeVault(rootClient, "searchCommandInit.json")
            command = SearchCommand(playwrightListener)
        }

        should("throw exception if vault url is not provided") {
            shouldThrow<MissingArgument> {
                command.parse(
                    listOf(
                        "-s",
                        "search_string"
                    )
                )
            }
        }

        should("throw exception if search string is not provided") {
            shouldThrow<MissingOption> {
                command.parse(
                    listOf(
                        VAULT_ENTERPRISE_URL
                    )
                )
            }
        }

        for (invalidUrl in listOf(
            "invalid_url",
            "ftp://example.com",
            "file:///path/to/file"
        )) {
            should("throw exception if url is invalid ('$invalidUrl')") {
                shouldThrow<BadParameterValue> {
                    command.parse(
                        listOf(
                            invalidUrl,
                            "-s",
                            "search_string"
                        )
                    )
                }
            }
        }

        should("display error if no output is selected") {
            val result = runCommand(
                command = command,
                vaultUrl = VAULT_ENTERPRISE_URL,
                searchString = "search_string",
                outputTerminalText = false,
                outputTerminalJson = false
            )

            result.stderr shouldContain "Please select at least one output option to display the search results."
            result.statusCode shouldBe 1
        }

        should("display error when the Vault url is not reachable") {
            val result = runCommand(
                command = command,
                vaultUrl = "http://invalid-url",
                searchString = "search_string",
                token = ROOT_TOKEN,
                outputTerminalText = true
            )

            result.stderr.trim() shouldBe """
            Could not reach Vault server.
            Please check the URL or your network connection.
            """.trimIndent()
            result.stdout.trim() shouldBe ""
            result.statusCode shouldBe 1
        }

        for (token in listOf(null, "")) {
            should("display error if token is $token and other auth methods is disabled") {
                val result = runCommand(
                    command = command,
                    vaultUrl = VAULT_ENTERPRISE_URL,
                    searchString = "search_string",
                    token = token,
                    oidc = false,
                    outputTerminalText = true
                )

                result.stderr.trim() shouldBe """
            |You must provide an authentication strategy.
            |To use token authentication, provide a Vault token via the "--token <token>" option or the VAULT_TOKEN environment variable.
            |To use OIDC authentication, enable it via the "--oidc true" option.
                """.trimMargin()
                result.stdout.trim() shouldBe ""
                result.statusCode shouldBe 1
            }
        }

        for ((outputTerminalText, outputJsonText, expected) in listOf(
            TerminalConfig(
                text = true,
                json = false,
                expected = """
                |Namespace: 
                |Total paths analyzed: 1
                |Secret paths: 0
                |Error paths: 1
                |- '' (Unknown error)
                """.trimMargin()
            ),
            TerminalConfig(
                text = false,
                json = true,
                expected = """
                |{
                |    "namespace": "",
                |    "totalPathsAnalyzed": 1,
                |    "secretPaths": {},
                |    "errorPaths": {
                |        "": "Unknown error"
                |    }
                |}
                """.trimMargin()
            )
        )) {
            should(
                "display nothing when there is no secret in the vault (outputTerminalText=$outputTerminalText, outputTerminalJson=$outputJsonText)"
            ) {
                deleteAllKV2Secrets(rootClient)

                val result = runCommand(
                    command = command,
                    vaultUrl = VAULT_ENTERPRISE_URL,
                    token = ROOT_TOKEN,
                    searchString = "value",
                    outputTerminalText = outputTerminalText,
                    outputTerminalJson = outputJsonText
                )

                result.stderr.trim() shouldBe ""
                result.stdout.trim() shouldBe expected
                result.statusCode shouldBe 0
            }
        }

        for ((outputTerminalText, outputJsonText, expected) in listOf(
            TerminalConfig(
                text = true,
                json = false,
                expected = """
                |Namespace: 
                |Total paths analyzed: 6
                |Secret paths: 0
                |Error paths: 0
                """.trimMargin()
            ),
            TerminalConfig(
                text = false,
                json = true,
                expected = """
                |{
                |    "namespace": "",
                |    "totalPathsAnalyzed": 6,
                |    "secretPaths": {},
                |    "errorPaths": {}
                |}
                """.trimMargin()
            )
        )) {
            should(
                "display nothing when there is no match for the search string (outputTerminalText=$outputTerminalText, outputTerminalJson=$outputJsonText)"
            ) {
                val result = runCommand(
                    command = command,
                    vaultUrl = VAULT_ENTERPRISE_URL,
                    token = ROOT_TOKEN,
                    searchString = "non_existing_value",
                    outputTerminalText = outputTerminalText,
                    outputTerminalJson = outputJsonText
                )

                result.stderr.trim() shouldBe ""
                result.stdout.trim() shouldBe expected
                result.statusCode shouldBe 0
            }
        }

        for ((outputTerminalText, outputJsonText, expected) in listOf(
            TerminalConfig(
                text = true,
                json = false,
                expected = ROOT_VALUE_DEFAULT_TEXT
            ),
            TerminalConfig(
                text = false,
                json = true,
                expected = ROOT_VALUE_DEFAULT_JSON
            )
        )) {
            should(
                "display find matching value when match in default namespace (outputTerminalText=$outputTerminalText, outputTerminalJson=$outputJsonText)"
            ) {
                val result = runCommand(
                    command = command,
                    vaultUrl = VAULT_ENTERPRISE_URL,
                    token = ROOT_TOKEN,
                    searchString = "value",
                    outputTerminalText = outputTerminalText,
                    outputTerminalJson = outputJsonText
                )

                result.stderr.trim() shouldBe ""
                result.stdout.trim() shouldBe expected
                result.statusCode shouldBe 0
            }
        }

        should("display find matching keys") {
            val result = runCommand(
                command = command,
                vaultUrl = VAULT_ENTERPRISE_URL,
                token = ROOT_TOKEN,
                searchString = "encryption_key",
                outputTerminalText = true,
                outputTerminalJson = false
            )

            result.stderr.trim() shouldBe ""
            result.stdout.trim() shouldBe """
                |Namespace: 
                |Total paths analyzed: 6
                |Secret paths: 1
                |- 'deep/nested/path/multiple/levels' (L. 11)
                |Error paths: 0
            """.trimMargin()
            result.statusCode shouldBe 0
        }

        should("display find matching value when match in a specific namespace") {
            val result = runCommand(
                command = command,
                vaultUrl = VAULT_ENTERPRISE_URL,
                namespaces = listOf("namespace1"),
                token = ROOT_TOKEN,
                searchString = "value",
                outputTerminalText = true
            )

            result.statusCode shouldBe 0
            result.stdout.trim() shouldBe NAMESPACE1_VALUE_DEFAULT_TEXT
            result.stderr.trim() shouldBe ""
        }

        should("display find matching value when match in default and specific namespaces") {
            val result = runCommand(
                command = command,
                vaultUrl = VAULT_ENTERPRISE_URL,
                namespaces = listOf("", "namespace1", "namespace2"),
                token = ROOT_TOKEN,
                searchString = "value",
                outputTerminalText = true
            )

            result.stderr.trim() shouldBe ""
            result.stdout.trim() shouldBe ALL_VALUE_DEFAULT_TEXT
            result.statusCode shouldBe 0
        }

        should("use token authentication valid for namespace but rejected for default namespace") {
            val namespaceClient = createVaultEnterpriseClient("namespace1")
            val namespaceToken = namespaceClient.use { client ->
                client.system.policy.createOrUpdate(
                    "namespace1-search-policy",
                    """
                        |path "secret/metadata" {
                        |    capabilities = ["list"]
                        |}
                        |path "secret/metadata/*" {
                        |    capabilities = ["list"]
                        |}
                        |path "secret/data/*" {
                        |    capabilities = ["read"]
                        |}
                    """.trimMargin()
                )
                client.auth.token.createToken {
                    policies = listOf("namespace1-search-policy")
                }.clientToken
            }

            val result = runCommand(
                command = command,
                vaultUrl = VAULT_ENTERPRISE_URL,
                namespaces = listOf("", "namespace1"),
                token = namespaceToken,
                searchString = "value",
                outputTerminalText = true
            )

            result.stderr.trim() shouldBe ""
            result.stdout.trim() shouldBe """
            |Namespace: 
            |Total paths analyzed: 1
            |Secret paths: 0
            |Error paths: 1
            |- '' (1 error occurred: * permission denied)
            |
            |$NAMESPACE1_VALUE_DEFAULT_TEXT
            """.trimMargin()
            result.statusCode shouldBe 0
        }

        should("display error if OIDC authentication is not configured in Vault") {
            val result = runCommand(
                command = command,
                vaultUrl = VAULT_ENTERPRISE_URL,
                namespaces = listOf("namespace1"),
                token = null,
                oidc = true,
                searchString = "XXX",
                outputTerminalText = true
            )

            result.stderr.trim() shouldBe """
            |Failed to generate the OIDC authorization URL.
            |Please check your Vault server configuration and ensure that the OIDC authentication method is properly configured.
            """.trimMargin()
            result.stdout.trim() shouldBe ""
            result.statusCode shouldBe 1
        }

        for ((timeout, expectedExceptionTime) in listOf(
            1.seconds to "1s",
            2.seconds to "2s",
            1.days to "1d",
            200_000.seconds to "2d 7h 33m 20s",
            365.days to "365d"
        )) {
            should("display error if OIDC authentication fails due to timeout (timeout=$expectedExceptionTime)") {
                // Use runTest to use fake timer
                runTest {
                    command = SearchCommand(
                        browserLauncher = {
                            true
                        }
                    )
                    val result = runCommand(
                        command = command,
                        vaultUrl = VAULT_ENTERPRISE_URL,
                        token = null,
                        oidc = true,
                        oidcTimeout = timeout,
                        searchString = "XXX",
                        outputTerminalText = true
                    )

                    result.stderr.trim() shouldBe """
                    |OIDC login process timed out after $expectedExceptionTime.
                    |Please ensure that the OIDC provider is reachable.
                    """.trimMargin()
                    result.stdout.trim() shouldBe ""
                    result.statusCode shouldBe 1
                }
            }
        }

        should("display error if OIDC authentication fails due to url not open in browser") {
            runTest {
                command = SearchCommand(
                    browserLauncher = {
                        false
                    }
                )

                val result = runCommand(
                    command = command,
                    vaultUrl = VAULT_ENTERPRISE_URL,
                    token = null,
                    oidc = true,
                    oidcTimeout = 1.seconds,
                    searchString = "XXX",
                    outputTerminalText = true
                )

                result.stderr.trim() shouldContain Regex(
                    """
                    |Failed to open the browser for OIDC authentication. Please open the following URL in your browser to complete authentication:
                    |${KeycloakUtil.HOST}/realms/vault/protocol/openid-connect/auth\?(?=.*client_id=)(?=.*code_challenge=)(?=.*nonce=)(?=.*redirect_uri=)(?=.*state=)
                    """.trimMargin()
                )
                result.stdout.trim() shouldBe ""
                result.statusCode shouldBe 1
            }
        }

        should("use OIDC authentication when provided for default namespace") {
            val result = runCommand(
                command = command,
                vaultUrl = VAULT_ENTERPRISE_URL,
                token = null,
                oidc = true,
                searchString = "XXX",
                outputTerminalText = true
            )

            playwrightListener.maxCounterSimultaneousAuthentication shouldBe 1

            result.stderr.trim() shouldBe ""
            result.stdout.trim() shouldBe """
            |Namespace: 
            |Total paths analyzed: 6
            |Secret paths: 0
            |Error paths: 0
            """.trimMargin()
            result.statusCode shouldBe 0
        }

        should("use OIDC for default namespace and specific namespace") {
            createVaultEnterpriseClient("namespace1").use {
                configureOIDC(it)
            }
            val result = runCommand(
                command = command,
                vaultUrl = VAULT_ENTERPRISE_URL,
                namespaces = listOf("", "namespace1", "namespace2"),
                token = null,
                oidc = true,
                searchString = "XXX",
                outputTerminalText = true
            )

            playwrightListener.maxCounterSimultaneousAuthentication shouldBe 1

            // Fail due to missing OIDC configuration for namespace2
            // Even if the root namespace succeed the OIDC login
            result.stderr.trim() shouldBe """
                |Failed to generate the OIDC authorization URL.
                |Please check your Vault server configuration and ensure that the OIDC authentication method is properly configured.
            """.trimMargin()
            result.stdout.trim() shouldBe ""
            result.statusCode shouldBe 1
        }

        should("use OIDC for several namespace in sequential") {
            listOf("namespace1", "namespace2").forEach { namespace ->
                createVaultEnterpriseClient(namespace).use {
                    configureOIDC(it)
                }
            }

            val result = runCommand(
                command = command,
                vaultUrl = VAULT_ENTERPRISE_URL,
                namespaces = listOf("", "namespace1", "namespace2"),
                token = null,
                oidc = true,
                oidcParallel = false,
                searchString = "value",
                outputTerminalText = true
            )

            playwrightListener.maxCounterSimultaneousAuthentication shouldBe 1

            result.stderr.trim() shouldBe ""
            result.stdout.trim() shouldBe ALL_VALUE_DEFAULT_TEXT
            result.statusCode shouldBe 0
        }

        should("use OIDC for several namespace in parallel") {
            listOf("namespace1", "namespace2").forEach { namespace ->
                createVaultEnterpriseClient(namespace).use {
                    configureOIDC(it)
                }
            }

            val result = runCommand(
                command = command,
                vaultUrl = VAULT_ENTERPRISE_URL,
                namespaces = listOf("", "namespace1", "namespace2"),
                token = null,
                oidc = true,
                oidcParallel = true,
                searchString = "value",
                outputTerminalText = true
            )

            // The first authentication is performed alone to store the session (cookies, SSO, ...) in the browser,
            // then the two remaining ones are processed simultaneously.
            playwrightListener.maxCounterSimultaneousAuthentication shouldBe 2

            result.stderr.trim() shouldBe ""
            result.stdout.trim() shouldBe ALL_VALUE_DEFAULT_TEXT
            result.statusCode shouldBe 0
        }

        for (rootPath in listOf("secrets", "secrets/")) {
            should("search from a specific root path ('$rootPath')") {
                val result = runCommand(
                    command = command,
                    vaultUrl = VAULT_ENTERPRISE_URL,
                    namespaces = listOf("namespace2"),
                    token = ROOT_TOKEN,
                    rootPath = rootPath,
                    searchString = "value",
                    outputTerminalText = true
                )

                result.stderr.trim() shouldBe ""
                result.stdout.trim() shouldBe NAMESPACE2_VALUE_SECRETS_TEXT
                result.statusCode shouldBe 0
            }
        }

        for (rootPath in listOf(null, "")) {
            should("search from a root path when root path is $rootPath") {
                val result = runCommand(
                    command = command,
                    vaultUrl = VAULT_ENTERPRISE_URL,
                    token = ROOT_TOKEN,
                    rootPath = rootPath,
                    searchString = "value",
                    outputTerminalText = true
                )

                result.stderr.trim() shouldBe ""
                result.stdout.trim() shouldBe ROOT_VALUE_DEFAULT_TEXT
                result.statusCode shouldBe 0
            }
        }

        should("search by ignoring case when ignore-case option is set to true") {
            val result = runCommand(
                command = command,
                vaultUrl = VAULT_ENTERPRISE_URL,
                namespaces = listOf("namespace2"),
                token = ROOT_TOKEN,
                searchString = "VALUE",
                ignoreCase = true,
                outputTerminalText = true
            )

            result.stderr.trim() shouldBe ""
            result.stdout.trim() shouldBe """
            |Namespace: namespace2
            |Total paths analyzed: 6
            |Secret paths: 5
            |- 'complex_key_search' (L. 1, 2, 3, 4, 5)
            |- 'mixed_case_scenario' (L. 1, 2, 3, 4, 5)
            |- 'partial_content_test' (L. 1, 2, 3, 4)
            |- 'secrets/database' (L. 4, 5, 7, 13, 15, 17)
            |- 'test_identical_values' (L. 1, 2, 3, 4)
            |Error paths: 0
            """.trimMargin()
            result.statusCode shouldBe 0
        }

        for (ignoreCase in listOf(null, false)) {
            should("search by considering case when ignore-case option is set to $ignoreCase") {
                val result = runCommand(
                    command = command,
                    vaultUrl = VAULT_ENTERPRISE_URL,
                    namespaces = listOf("namespace2"),
                    token = ROOT_TOKEN,
                    searchString = "VALUE",
                    ignoreCase = ignoreCase,
                    outputTerminalText = true
                )

                result.stderr.trim() shouldBe ""
                result.stdout.trim() shouldBe """
                |Namespace: namespace2
                |Total paths analyzed: 6
                |Secret paths: 1
                |- 'mixed_case_scenario' (L. 1, 3)
                |Error paths: 0
                """.trimMargin()
                result.statusCode shouldBe 0
            }
        }

        should("display find matching value in text format when only output-terminal-text option is set to true") {
            val result = runCommand(
                command = command,
                vaultUrl = VAULT_ENTERPRISE_URL,
                namespaces = listOf("namespace2"),
                token = ROOT_TOKEN,
                rootPath = "secrets",
                searchString = "value",
                outputTerminalText = true,
                outputTerminalJson = false
            )

            result.stderr.trim() shouldBe ""
            result.stdout.trim() shouldBe NAMESPACE2_VALUE_SECRETS_TEXT
            result.statusCode shouldBe 0
        }

        should("display find matching value in JSON format when only output-terminal-json option is set to true") {
            val result = runCommand(
                command = command,
                vaultUrl = VAULT_ENTERPRISE_URL,
                namespaces = listOf("namespace2"),
                token = ROOT_TOKEN,
                rootPath = "secrets",
                searchString = "value",
                outputTerminalText = false,
                outputTerminalJson = true
            )

            result.stderr.trim() shouldBe ""
            result.stdout.trim() shouldBe NAMESPACE2_VALUE_SECRETS_JSON
            result.statusCode shouldBe 0
        }

        should(
            "display find matching value in both text and JSON format when both output-terminal-text and output-terminal-json options are set to true"
        ) {
            val result = runCommand(
                command = command,
                vaultUrl = VAULT_ENTERPRISE_URL,
                namespaces = listOf("namespace2"),
                token = ROOT_TOKEN,
                rootPath = "secrets",
                searchString = "value",
                outputTerminalText = true,
                outputTerminalJson = true
            )

            result.stderr.trim() shouldBe ""
            result.stdout.trim() shouldBe """
                |$NAMESPACE2_VALUE_SECRETS_TEXT
                |
                |$NAMESPACE2_VALUE_SECRETS_JSON
            """.trimMargin()
            result.statusCode shouldBe 0
        }
    })

/**
 * Namespace: 'root'
 * Search: 'value'
 * Path: ''
 * Format: TEXT
 */
private val ROOT_VALUE_DEFAULT_TEXT = """
|Namespace: 
|Total paths analyzed: 6
|Secret paths: 6
|- 'deep/nested/path/multiple/levels' (L. 4, 6, 11, 14, 16)
|- 'edge_cases' (L. 3, 4, 5, 6, 8, 9, 10, 11, 12, 13)
|- 'identical_values' (L. 1, 2, 3, 4)
|- 'key_contains_value' (L. 1, 2, 3, 4, 5)
|- 'key_named_value' (L. 2, 4)
|- 'partial_matches' (L. 1, 2, 3, 4, 5)
|Error paths: 0
""".trimMargin()

/**
 * Namespace: 'namespace1'
 * Search: 'value'
 * Path: ''
 * Format: TEXT
 */
private val NAMESPACE1_VALUE_DEFAULT_TEXT = """
|Namespace: namespace1
|Total paths analyzed: 5
|Secret paths: 5
|- 'duplicate_secrets' (L. 1, 2, 3)
|- 'key_name_search' (L. 1, 2, 3, 4, 5)
|- 'payment/nested' (L. 3, 4)
|- 'services' (L. 3, 5, 8, 10)
|- 'similar_but_different' (L. 1, 2, 3, 4, 5)
|Error paths: 0
""".trimMargin()

/**
 * Namespace: 'namespace2'
 * Search: 'value'
 * Path: ''
 * Format: TEXT
 */
private val NAMESPACE2_VALUE_DEFAULT_TEXT = """
|Namespace: namespace2
|Total paths analyzed: 6
|Secret paths: 5
|- 'complex_key_search' (L. 1, 2, 3, 4, 5)
|- 'mixed_case_scenario' (L. 4)
|- 'partial_content_test' (L. 1, 2, 3, 4)
|- 'secrets/database' (L. 4, 5, 7, 13, 15, 17)
|- 'test_identical_values' (L. 1, 2, 3, 4)
|Error paths: 0
""".trimMargin()

/**
 * Namespace: 'root'
 * Search: 'value'
 * Path: ''
 * Format: JSON
 */
private val ROOT_VALUE_DEFAULT_JSON = """
|{
|    "namespace": "",
|    "totalPathsAnalyzed": 6,
|    "secretPaths": {
|        "deep/nested/path/multiple/levels": [
|            4,
|            6,
|            11,
|            14,
|            16
|        ],
|        "edge_cases": [
|            3,
|            4,
|            5,
|            6,
|            8,
|            9,
|            10,
|            11,
|            12,
|            13
|        ],
|        "identical_values": [
|            1,
|            2,
|            3,
|            4
|        ],
|        "key_contains_value": [
|            1,
|            2,
|            3,
|            4,
|            5
|        ],
|        "key_named_value": [
|            2,
|            4
|        ],
|        "partial_matches": [
|            1,
|            2,
|            3,
|            4,
|            5
|        ]
|    },
|    "errorPaths": {}
|}
""".trimMargin()

/**
 * Namespace: root / namespace1 / namespace2
 * Search: 'value'
 * Path: ''
 * Format: TEXT
 */
private val ALL_VALUE_DEFAULT_TEXT = """
|$ROOT_VALUE_DEFAULT_TEXT
|
|$NAMESPACE1_VALUE_DEFAULT_TEXT
|
|$NAMESPACE2_VALUE_DEFAULT_TEXT
""".trimMargin()

/**
 * Namespace: namespace2
 * Search: 'value'
 * Path: 'secrets'
 * Format: TEXT
 */
private val NAMESPACE2_VALUE_SECRETS_TEXT = """
|Namespace: namespace2
|Total paths analyzed: 1
|Secret paths: 1
|- 'secrets/database' (L. 4, 5, 7, 13, 15, 17)
|Error paths: 0
""".trimMargin()

/**
 * Namespace: namespace2
 * Search: 'value'
 * Path: 'secrets'
 * Format: JSON
 */
private val NAMESPACE2_VALUE_SECRETS_JSON = """
|{
|    "namespace": "namespace2",
|    "totalPathsAnalyzed": 1,
|    "secretPaths": {
|        "secrets/database": [
|            4,
|            5,
|            7,
|            13,
|            15,
|            17
|        ]
|    },
|    "errorPaths": {}
|}
""".trimMargin()

suspend fun initializeVault(rootClient: VaultClient, filePath: String) {
    val structure = readJson<JsonObject>(filePath)
    val secretEngine = "secret"
    val defaultKV2Configuration = rootClient.system.mounts.getConfigurationOfSecretEngine(SECRET_ENGINE)
    val enableSecretsEnginePayload = defaultKV2Configuration.toMountsEnableSecretsEnginePayload()

    structure.forEach { (namespace, secrets) ->
        val client = if (namespace.isEmpty()) {
            rootClient
        } else {
            rootClient.system.namespaces.create(namespace)
            createVaultEnterpriseClient(namespace).also {
                it.system.mounts.enableSecretsEngine(secretEngine, enableSecretsEnginePayload)
            }
        }

        val kv2 = client.secret.kv2
        secrets.jsonObject.forEach { (path, secretData) ->
            kv2.createOrUpdateSecret(path) {
                data(secretData)
            }
        }

        if (rootClient != client) {
            client.close()
        }
    }
}

private suspend fun runCommand(
    command: SearchCommand,
    vaultUrl: String,
    namespaces: List<String> = emptyList(),
    token: String? = null,
    oidc: Boolean? = null,
    oidcParallel: Boolean? = null,
    oidcTimeout: Duration? = null,
    rootPath: String? = null,
    searchString: String? = null,
    ignoreCase: Boolean? = null,
    outputTerminalText: Boolean? = null,
    outputTerminalJson: Boolean? = null,
) = command.test(
    buildList {
        add(vaultUrl)
        if (namespaces.isNotEmpty()) {
            add("-n")
            add(namespaces.joinToString(separator = ","))
        }
        if (token != null) {
            add("-t")
            add(token)
        }
        if (oidc != null) {
            add("--oidc")
            add(oidc.toString())
        }
        if (oidcParallel != null) {
            add("--oidc-parallel")
            add(oidcParallel.toString())
        }
        if (oidcTimeout != null) {
            add("--oidc-timeout")
            add(oidcTimeout.inWholeSeconds.toString())
        }
        if (rootPath != null) {
            add("-r")
            add(rootPath)
        }
        if (searchString != null) {
            add("-s")
            add(searchString)
        }
        if (ignoreCase != null) {
            add("-i")
            add(ignoreCase.toString())
        }
        if (outputTerminalText != null) {
            add("-ott")
            add(outputTerminalText.toString())
        }
        if (outputTerminalJson != null) {
            add("-otj")
            add(outputTerminalJson.toString())
        }
    }
)
