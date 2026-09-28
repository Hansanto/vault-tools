package io.github.hansanto.vaulttools.common.client

import io.github.hansanto.kault.VaultClient

interface VaultClientStrategy {

    /**
     * Creates authenticated Vault clients for the given URL and namespaces.
     * If the namespaces list is empty, a single client without namespace will be created.
     *
     * @param url Vault server URL.
     * @param namespaces List of namespaces to create clients for.
     * @return A list of authenticated [VaultClient].
     */
    suspend fun createAuthenticatedClients(url: String, namespaces: List<String> = emptyList()): List<VaultClient>
}
