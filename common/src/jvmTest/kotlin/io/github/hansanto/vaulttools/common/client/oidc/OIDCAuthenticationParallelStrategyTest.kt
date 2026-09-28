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
import io.kotest.matchers.ranges.shouldBeInOpenEndRange
import io.kotest.matchers.shouldBe
import kotlin.time.Duration

class OIDCAuthenticationParallelStrategyTest :
    ShouldSpec({

        val playwrightListener = PlaywrightOIDCFlowListener()
        extensions(playwrightListener, VaultListener)

        lateinit var rootClient: VaultClient
        lateinit var strategy: OIDCAuthenticationParallelStrategy
        lateinit var oidcCallbackServer: OIDCCallbackServer

        beforeSpec {
            rootClient = createVaultEnterpriseClient()
            configureOIDC(rootClient)
            strategy = OIDCAuthenticationParallelStrategy
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
            strategy.authenticate(emptyList(), oidcCallbackServer) { error("Should not open browser") }
            oidcCallbackServer.handlers shouldBe emptyMap()
            playwrightListener.maxCounterSimultaneousAuthentication shouldBe 0
        }

        should("authenticate clients in parallel") {
            val numberOfClients = 5
            processClientsAuthentication(
                rootClient = rootClient,
                strategy = strategy,
                oidcCallbackServer = oidcCallbackServer,
                numberOfClients = numberOfClients
            )

            // At least 2 clients should be authenticated simultaneously
            // But that should never be equal to the total number of clients
            // because a first authentication needs to complete (to have cookie, session, etc.)
            // before starting the next ones
            playwrightListener.maxCounterSimultaneousAuthentication shouldBeInOpenEndRange 2..<numberOfClients
        }

        should("authenticate clients in sequential when only two clients") {
            processClientsAuthentication(
                rootClient = rootClient,
                strategy = strategy,
                oidcCallbackServer = oidcCallbackServer,
                numberOfClients = 2
            )

            // The first client should be authenticated before starting the others asynchronously
            // So the max simultaneous authentication should be 1
            playwrightListener.maxCounterSimultaneousAuthentication shouldBe 1
        }
    })

private suspend fun processClientsAuthentication(
    rootClient: VaultClient,
    strategy: OIDCAuthenticationParallelStrategy,
    oidcCallbackServer: OIDCCallbackServer,
    numberOfClients: Int
) {
    val namespaces = List(numberOfClients) { "namespace$it" }
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
    } finally {
        clients.closeAll()
    }
}
