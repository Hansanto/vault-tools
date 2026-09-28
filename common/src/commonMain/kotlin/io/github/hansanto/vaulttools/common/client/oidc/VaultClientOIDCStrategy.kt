package io.github.hansanto.vaulttools.common.client.oidc

import io.github.hansanto.kault.VaultClient
import io.github.hansanto.vaulttools.common.client.VaultClientStrategy
import io.github.hansanto.vaulttools.shared.BrowserLauncher
import kotlin.time.Duration

class VaultClientOIDCStrategy(
    /**
     * Strategy to use for OIDC authentication (parallel or sequential).
     */
    val authenticationStrategy: OIDCAuthenticationStrategy,
    /**
     * Port for the OIDC callback server.
     */
    val port: Int,
    /**
     * Timeout for the OIDC login process.
     * If the user does not complete the login process within this time, the login will fail.
     */
    val loginTimeout: Duration,
    /**
     * Launcher to open the OIDC authentication URL in the user's browser.
     */
    val browserLauncher: BrowserLauncher,
    /**
     * Callback to execute when the browser fails to open the OIDC authentication URL.
     */
    val onBrowserOpenFailed: OIDCOnBrowserOpenFailed,
) : VaultClientStrategy {

    override suspend fun createAuthenticatedClients(url: String, namespaces: List<String>): List<VaultClient> {
        val clients = namespaces
            .map { createClient(url, it) }
            .ifEmpty { listOf(createClient(url)) }

        val oidcCallbackServer = OIDCCallbackServer(
            port = port,
            loginTimeout = loginTimeout,
            browserLauncher = browserLauncher,
        )

        try {
            oidcCallbackServer.start()
            authenticationStrategy.authenticate(
                vaultClients = clients,
                oidcCallbackServer = oidcCallbackServer,
                onBrowserOpenFailed = onBrowserOpenFailed
            )
        } finally {
            oidcCallbackServer.stop()
        }

        return clients
    }

    /**
     * Create a new Vault client for the given URL and namespace, not authenticated yet.
     *
     * @param url Vault server URL.
     * @param namespace Optional namespace for the Vault client.
     * @return A new instance of [VaultClient].
     */
    fun createClient(url: String, namespace: String? = null): VaultClient = VaultClient {
        this.url = url
        this.namespace = namespace
        auth {
            autoRenewToken = false
        }
    }
}
