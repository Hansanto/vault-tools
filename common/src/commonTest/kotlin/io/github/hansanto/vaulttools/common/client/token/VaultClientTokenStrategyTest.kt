package io.github.hansanto.vaulttools.common.client.token

import io.github.hansanto.kault.VaultClient
import io.github.hansanto.kault.auth.token.createToken
import io.github.hansanto.vaulttools.common.extension.closeAll
import io.github.hansanto.vaulttools.commontest.ROOT_TOKEN
import io.github.hansanto.vaulttools.commontest.SECRET_POLICY
import io.github.hansanto.vaulttools.commontest.VAULT_ENTERPRISE_URL
import io.github.hansanto.vaulttools.commontest.assertCanCreateSecret
import io.github.hansanto.vaulttools.commontest.configureKV2
import io.github.hansanto.vaulttools.commontest.configureSecretPolicy
import io.github.hansanto.vaulttools.commontest.createVaultEnterpriseClient
import io.github.hansanto.vaulttools.commontest.listener.VaultListener
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

class VaultClientTokenStrategyTest :
    ShouldSpec({

        extensions(VaultListener)

        lateinit var rootClient: VaultClient

        beforeSpec {
            rootClient = createVaultEnterpriseClient()
            configureSecretPolicy(rootClient)
        }

        afterSpec {
            rootClient.close()
        }

        should("createAuthenticatedClients create single client when namespaces is empty") {
            val token = createToken(rootClient)
            val strategy = VaultClientTokenStrategy(token)

            val clients = strategy.createAuthenticatedClients(VAULT_ENTERPRISE_URL, emptyList())

            try {
                clients shouldHaveSize 1

                val client = clients.first()
                client.namespace shouldBe null
                client.auth.getTokenString() shouldBe token
                client.auth.autoRenewToken shouldBe false
                assertCanCreateSecret(client)
            } finally {
                clients.closeAll()
            }
        }

        should("createAuthenticatedClients create one client per namespace") {
            // One token should work for all namespaces
            val token = ROOT_TOKEN
            val strategy = VaultClientTokenStrategy(token)

            val namespaces = List(5) { "namespace$it" }
            namespaces.forEach { namespace ->
                rootClient.system.namespaces.create(namespace)
                createVaultEnterpriseClient(namespace = namespace).use { client ->
                    configureSecretPolicy(client)
                    configureKV2(client)
                }
            }

            val clients = strategy.createAuthenticatedClients(VAULT_ENTERPRISE_URL, namespaces)

            try {
                clients.forEachIndexed { index, client ->
                    client.namespace shouldBe namespaces[index]
                    client.auth.getTokenString() shouldBe token
                    client.auth.autoRenewToken shouldBe false
                    assertCanCreateSecret(client)
                }
            } finally {
                clients.closeAll()
            }
        }
    })

private suspend fun createToken(rootClient: VaultClient): String = rootClient.auth.token.createToken {
    policies = listOf(SECRET_POLICY)
    noParent = true
}.clientToken
