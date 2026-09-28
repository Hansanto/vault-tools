package io.github.hansanto.vaulttools.common.client.token

import io.github.hansanto.kault.VaultClient
import io.github.hansanto.vaulttools.common.client.VaultClientStrategy

class VaultClientTokenStrategy(private val token: String) : VaultClientStrategy {

    override suspend fun createAuthenticatedClients(url: String, namespaces: List<String>): List<VaultClient> =
        namespaces
            .map { createClient(url, it) }
            .ifEmpty { listOf(createClient(url)) }

    /**
     * Create a new Vault client for the given URL and namespace, authenticated with the provided token.
     *
     * @param url Vault server URL.
     * @param namespace Optional namespace for the Vault client.
     * @return A new instance of [VaultClient] authenticated.
     */
    fun createClient(url: String, namespace: String? = null): VaultClient = VaultClient {
        this.url = url
        this.namespace = namespace
        auth {
            setTokenString(token)
            autoRenewToken = false
        }
    }
}
