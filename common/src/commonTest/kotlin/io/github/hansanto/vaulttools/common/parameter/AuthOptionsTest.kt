package io.github.hansanto.vaulttools.common.parameter

import com.github.ajalt.clikt.core.BadParameterValue
import com.github.ajalt.clikt.core.NoOpCliktCommand
import com.github.ajalt.clikt.core.parse
import com.github.ajalt.clikt.core.terminal
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import io.github.hansanto.kault.VaultClient
import io.github.hansanto.vaulttools.common.client.oidc.OIDCAuthenticationParallelStrategy
import io.github.hansanto.vaulttools.common.client.oidc.OIDCAuthenticationSequentialStrategy
import io.github.hansanto.vaulttools.common.client.oidc.OIDCLoginTimeoutException
import io.github.hansanto.vaulttools.common.client.oidc.VaultClientOIDCStrategy
import io.github.hansanto.vaulttools.commontest.VAULT_ENTERPRISE_URL
import io.github.hansanto.vaulttools.commontest.configureKV2
import io.github.hansanto.vaulttools.commontest.configureOIDC
import io.github.hansanto.vaulttools.commontest.createVaultEnterpriseClient
import io.github.hansanto.vaulttools.commontest.listener.TerminalListener
import io.github.hansanto.vaulttools.commontest.listener.VaultListener
import io.github.hansanto.vaulttools.shared.DefaultBrowserLauncher
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class AuthOptionsTest :
    ShouldSpec({

        val terminalListener = TerminalListener()
        extensions(terminalListener, VaultListener)

        lateinit var rootClient: VaultClient

        beforeSpec {
            rootClient = createVaultEnterpriseClient()
        }

        afterSpec {
            rootClient.close()
        }

        beforeTest {
            configureKV2(rootClient)
            configureOIDC(rootClient)
        }

        class TestCommand : NoOpCliktCommand() {
            val authOptions by AuthOptions(DefaultBrowserLauncher)
        }

        should("have default values") {
            val cmd = TestCommand()
            cmd.parse(emptyList())
            val options = cmd.authOptions
            options.token shouldBe null
            options.oidc shouldBe false
            options.oidcPort shouldBe 8250
            options.oidcStrategy::class shouldBe OIDCAuthenticationSequentialStrategy::class
            options.oidcTimeout shouldBe 2.minutes
        }

        should("parse provided values") {
            val cmd = TestCommand()
            cmd.parse(
                listOf(
                    "--token", "my-token",
                    "--oidc", "true",
                    "--oidc-port", "1234",
                    "--oidc-parallel", "true",
                    "--oidc-timeout", "3600"
                )
            )
            val options = cmd.authOptions
            options.token shouldBe "my-token"
            options.oidc shouldBe true
            options.oidcPort shouldBe 1234
            options.oidcStrategy::class shouldBe OIDCAuthenticationParallelStrategy::class
            options.oidcTimeout shouldBe 1.hours
        }

        for ((timeout, expectedExceptionTime) in listOf(
            0.seconds to "0s",
            (-1).seconds to "-1s",
            (-2).seconds to "-2s",
            (-1).days to "-1d",
            (-200_000).seconds to "-(2d 7h 33m 20s)",
            (-365).days to "-365d"
        )) {
            should("throw error when oidc-timeout is not a positive duration: $expectedExceptionTime") {
                val cmd = TestCommand()
                val exception = shouldThrow<BadParameterValue> {
                    cmd.parse(
                        listOf(
                            "--oidc-timeout",
                            timeout.inWholeSeconds.toString()
                        )
                    )
                }
                exception.message shouldBe "OIDC timeout must be a positive duration, but was $expectedExceptionTime"
            }
        }

        should("getClientAuthenticationStrategy throw exception if token is not defined and oidc is false") {
            val cmd = TestCommand()
            cmd.parse(
                buildList {
                    add("--oidc")
                    add("false")
                }
            )
            val exception = shouldThrow<NoAuthStrategyProvidedException> {
                cmd.authOptions.getClientAuthenticationStrategy(cmd)
            }
            exception.message shouldBe expectedErrorMessage
        }

        for (token in listOf("", "   ")) {
            should("getClientAuthenticationStrategy throw exception if token is blank ('$token') and oidc is false") {
                val cmd = TestCommand()
                cmd.parse(
                    buildList {
                        add("--oidc")
                        add("false")
                        add("--token")
                        add(token)
                    }
                )
                val exception = shouldThrow<NoAuthStrategyProvidedException> {
                    cmd.authOptions.getClientAuthenticationStrategy(cmd)
                }
                exception.message shouldBe expectedErrorMessage
            }
        }

        should("getClientAuthenticationStrategy display error when browser cannot be opened for OIDC authentication") {
            runTest {
                lateinit var authUrl: String
                class TestCommand : NoOpCliktCommand() {
                    val authOptions by AuthOptions { url ->
                        authUrl = url
                        false
                    }
                }

                val cmd = TestCommand()
                cmd.configureContext {
                    terminal = terminalListener.terminal
                }
                cmd.parse(
                    buildList {
                        add("--oidc")
                        add("true")
                        add("--oidc-timeout")
                        add("1")
                    }
                )

                val strategy = cmd.authOptions.getClientAuthenticationStrategy(cmd) as VaultClientOIDCStrategy

                shouldThrow<OIDCLoginTimeoutException> {
                    strategy.createAuthenticatedClients(VAULT_ENTERPRISE_URL)
                }

                terminalListener.terminalOutput() shouldBe """
                    Failed to open the browser for OIDC authentication. Please open the following URL in your browser to complete authentication:
                    $authUrl
                """.trimIndent()
            }
        }
    })

val expectedErrorMessage = """
    |You must provide an authentication strategy.
    |To use token authentication, provide a Vault token via the "--token <token>" option or the VAULT_TOKEN environment variable.
    |To use OIDC authentication, enable it via the "--oidc true" option.
""".trimMargin()
