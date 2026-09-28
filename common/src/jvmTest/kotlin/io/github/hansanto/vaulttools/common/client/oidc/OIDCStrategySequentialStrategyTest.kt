package io.github.hansanto.vaulttools.common.client.oidc

import io.github.hansanto.kault.VaultClient
import io.github.hansanto.vaulttools.common.extension.closeAll
import io.github.hansanto.vaulttools.commontest.OIDC_PORT
import io.github.hansanto.vaulttools.commontest.ROOT_TOKEN
import io.github.hansanto.vaulttools.commontest.assertCanCreateSecret
import io.github.hansanto.vaulttools.commontest.assertDistinctTokens
import io.github.hansanto.vaulttools.commontest.configureOIDC
import io.github.hansanto.vaulttools.commontest.createVaultEnterpriseClient
import io.github.hansanto.vaulttools.commontest.listener.PlaywrightOIDCFlowListener
import io.github.hansanto.vaulttools.commontest.listener.VaultListener
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldNotBeIn
import io.kotest.matchers.shouldBe
import kotlin.time.Duration

class OIDCStrategySequentialStrategyTest :
    ShouldSpec({

        val playwrightListener = PlaywrightOIDCFlowListener()
        extensions(playwrightListener, VaultListener)

        lateinit var rootClient: VaultClient
        lateinit var strategy: OIDCAuthenticationSequentialStrategy
        lateinit var oidcCallbackServer: OIDCCallbackServer

        beforeSpec {
            rootClient = createVaultEnterpriseClient()
            configureOIDC(rootClient)
            strategy = OIDCAuthenticationSequentialStrategy
        }

        afterSpec {
            rootClient.close()
        }

        beforeTest {
            oidcCallbackServer = OIDCCallbackServer(
                port = OIDC_PORT,
                loginTimeout = Duration.INFINITE,
                browserLauncher = playwrightListener
            )
            oidcCallbackServer.start()
        }

        afterTest {
            oidcCallbackServer.stop()
        }

        should("do nothing when clients is empty") {
            strategy.authenticate(emptyList(), oidcCallbackServer) { error("Should never happen") }
            oidcCallbackServer.handlers shouldBe emptyMap()
            playwrightListener.maxCounterSimultaneousAuthentication shouldBe 0
        }

        should("authenticate clients sequentially") {
            val namespaces = List(5) { "namespace$it" }
            val clients = namespaces.map { namespace ->
                rootClient.system.namespaces.create(namespace)
                createVaultEnterpriseClient(namespace = namespace).also { client ->
                    configureOIDC(client)
                }
            }

            try {
                strategy.authenticate(
                    vaultClients = clients,
                    oidcCallbackServer = oidcCallbackServer
                ) { error("Should never happen") }

                clients.forEachIndexed { index, client ->
                    client.namespace shouldBe namespaces[index]
                    val token = client.auth.getTokenString()
                    token shouldNotBeIn listOf(null, ROOT_TOKEN)
                    client.auth.autoRenewToken shouldBe false
                    assertCanCreateSecret(client)
                }

                assertDistinctTokens(clients)

                playwrightListener.maxCounterSimultaneousAuthentication shouldBe 1
            } finally {
                clients.closeAll()
            }
        }
    })
