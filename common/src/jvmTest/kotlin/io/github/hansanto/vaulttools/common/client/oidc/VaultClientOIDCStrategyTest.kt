package io.github.hansanto.vaulttools.common.client.oidc

import io.github.hansanto.kault.VaultClient
import io.github.hansanto.vaulttools.common.extension.closeAll
import io.github.hansanto.vaulttools.commontest.OIDC_PORT
import io.github.hansanto.vaulttools.commontest.ROOT_TOKEN
import io.github.hansanto.vaulttools.commontest.VAULT_ENTERPRISE_URL
import io.github.hansanto.vaulttools.commontest.assertCanCreateSecret
import io.github.hansanto.vaulttools.commontest.assertDistinctTokens
import io.github.hansanto.vaulttools.commontest.configureOIDC
import io.github.hansanto.vaulttools.commontest.createVaultEnterpriseClient
import io.github.hansanto.vaulttools.commontest.isPortInUse
import io.github.hansanto.vaulttools.commontest.listener.PlaywrightOIDCFlowListener
import io.github.hansanto.vaulttools.commontest.listener.VaultListener
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotBeIn
import io.kotest.matchers.shouldBe
import kotlin.time.Duration

class VaultClientOIDCStrategyTest :
    ShouldSpec({

        val playwrightListener = PlaywrightOIDCFlowListener()
        extensions(playwrightListener, VaultListener)

        lateinit var rootClient: VaultClient
        lateinit var strategy: VaultClientOIDCStrategy

        beforeSpec {
            rootClient = createVaultEnterpriseClient()
            configureOIDC(rootClient)
            strategy = VaultClientOIDCStrategy(
                authenticationStrategy = OIDCAuthenticationParallelStrategy,
                port = OIDC_PORT,
                loginTimeout = Duration.INFINITE,
                browserLauncher = playwrightListener
            ) { error("Should never happen") }
        }

        afterSpec {
            rootClient.close()
        }

        should("createAuthenticatedClients create single client when namespaces is empty") {
            val clients = strategy.createAuthenticatedClients(VAULT_ENTERPRISE_URL, emptyList())

            try {
                clients shouldHaveSize 1

                isPortInUse(OIDC_PORT) shouldBe false

                val client = clients.first()
                client.namespace shouldBe null

                val token = client.auth.getTokenString()
                token shouldNotBeIn listOf(null, ROOT_TOKEN)
                client.auth.autoRenewToken shouldBe false
                assertCanCreateSecret(client)
            } finally {
                clients.closeAll()
            }
        }

        should("createAuthenticatedClients create one client per namespace") {
            val namespaces = listOf("namespace1", "namespace2")
            namespaces.forEach { namespace ->
                rootClient.system.namespaces.create(namespace)
                createVaultEnterpriseClient(namespace = namespace).use { client ->
                    configureOIDC(client)
                }
            }

            val clients = strategy.createAuthenticatedClients(VAULT_ENTERPRISE_URL, namespaces)

            try {
                clients shouldHaveSize namespaces.size

                isPortInUse(OIDC_PORT) shouldBe false

                val clientNamespace1 = clients.firstOrNull { it.namespace == "namespace1" }!!.also {
                    val clientToken1 = it.auth.getTokenString()
                    clientToken1 shouldNotBeIn listOf(null, ROOT_TOKEN)
                }
                assertCanCreateSecret(clientNamespace1)

                val clientNamespace2 = clients.firstOrNull { it.namespace == "namespace2" }!!.also {
                    val clientToken2 = it.auth.getTokenString()
                    clientToken2 shouldNotBeIn listOf(null, ROOT_TOKEN)
                }
                assertCanCreateSecret(clientNamespace2)

                assertDistinctTokens(clients)
            } finally {
                clients.closeAll()
            }
        }
    })
