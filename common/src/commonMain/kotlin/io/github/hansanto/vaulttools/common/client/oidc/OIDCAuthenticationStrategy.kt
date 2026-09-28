package io.github.hansanto.vaulttools.common.client.oidc

import io.github.hansanto.kault.VaultClient
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Defines the strategy to authenticate multiple Vault clients with OIDC.
 */
interface OIDCAuthenticationStrategy {

    /**
     * Authenticate the given list of Vault clients with the OIDC callback server.
     *
     * @param vaultClients List of Vault clients to authenticate.
     * @param oidcCallbackServer The OIDC callback server to use for authentication.
     * @param onBrowserOpenFailed Callback to execute when the browser fails to open the OIDC authentication URL.
     */
    suspend fun authenticate(
        vaultClients: List<VaultClient>,
        oidcCallbackServer: OIDCCallbackServer,
        onBrowserOpenFailed: OIDCOnBrowserOpenFailed
    )
}

/**
 * An OIDC authentication strategy that authenticates the Vault clients sequentially.
 * Compared to [OIDCAuthenticationParallelStrategy], this strategy is slower.
 * More adapted for environment with poor performance.
 */
object OIDCAuthenticationSequentialStrategy : OIDCAuthenticationStrategy {

    override suspend fun authenticate(
        vaultClients: List<VaultClient>,
        oidcCallbackServer: OIDCCallbackServer,
        onBrowserOpenFailed: OIDCOnBrowserOpenFailed
    ) {
        for (vaultClient in vaultClients) {
            oidcCallbackServer.authenticate(
                vaultClient = vaultClient,
                onBrowserOpenFailed = onBrowserOpenFailed
            )
        }
    }
}

/**
 * An OIDC authentication strategy that authenticates the Vault clients in parallel.
 * This strategy is faster when the browser used keeps the authentication information (cookies, session, SSO, etc.) after the first authentication.
 * When the first authentication is successful, the user is logged in the browser and all subsequent authentication can be done in parallel.
 * More adapted for environment with good performance,
 */
object OIDCAuthenticationParallelStrategy : OIDCAuthenticationStrategy {

    override suspend fun authenticate(
        vaultClients: List<VaultClient>,
        oidcCallbackServer: OIDCCallbackServer,
        onBrowserOpenFailed: OIDCOnBrowserOpenFailed
    ) {
        if (vaultClients.isEmpty()) return

        coroutineScope {
            // The first authentication allows to store the authentication in the browser,
            // can be cookies, session, SSO, etc. depending on the OIDC provider configuration.
            oidcCallbackServer.authenticate(
                vaultClient = vaultClients.first(),
                onBrowserOpenFailed = onBrowserOpenFailed
            )

            // Thanks to the first authentication, the user is now logged in the browser.
            // We can start all other authentication in parallel to speed up the process.
            vaultClients.drop(1).map {
                async {
                    oidcCallbackServer.authenticate(
                        vaultClient = it,
                        onBrowserOpenFailed = onBrowserOpenFailed
                    )
                }
            }.awaitAll()
        }
    }
}
