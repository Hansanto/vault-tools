package io.github.hansanto.vaulttools.common.parameter

import com.github.ajalt.clikt.core.BaseCliktCommand
import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.validate
import com.github.ajalt.clikt.parameters.types.boolean
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.long
import io.github.hansanto.vaulttools.common.client.VaultClientStrategy
import io.github.hansanto.vaulttools.common.client.oidc.OIDCAuthenticationParallelStrategy
import io.github.hansanto.vaulttools.common.client.oidc.OIDCAuthenticationSequentialStrategy
import io.github.hansanto.vaulttools.common.client.oidc.OIDCAuthenticationStrategy
import io.github.hansanto.vaulttools.common.client.oidc.VaultClientOIDCStrategy
import io.github.hansanto.vaulttools.common.client.token.VaultClientTokenStrategy
import io.github.hansanto.vaulttools.shared.BrowserLauncher
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private const val VAULT_TOKEN_ENV = "VAULT_TOKEN"
private const val DEFAULT_OIDC = false
private const val DEFAULT_OIDC_PORT = 8250
private const val DEFAULT_OIDC_PARALLEL = false
private val defaultOidcTimeout = 2.minutes

class NoAuthStrategyProvidedException :
    CliktError(
        """
    |You must provide an authentication strategy.
    |To use token authentication, provide a Vault token via the "--token <token>" option or the $VAULT_TOKEN_ENV environment variable.
    |To use OIDC authentication, enable it via the "--oidc true" option.
        """.trimMargin()

    )

class AuthOptions(
    /**
     * Launcher to open the OIDC authentication URL in the user's browser.
     */
    val browserLauncher: BrowserLauncher
) : OptionGroup(name = "Authentication options") {

    /**
     * Vault token to authenticate with Vault.
     * If token is not defined and [oidc] is set to `true`, OIDC authentication will be used instead.
     * If token is not defined and [oidc] is set to `false`, the command will fail with an error.
     */
    val token: String? by option(
        "-t",
        "--token",
        help = "Vault token (can also be set via $VAULT_TOKEN_ENV environment variable)",
        envvar = VAULT_TOKEN_ENV
    )

    /**
     * `true` if OIDC authentication should be used.
     * If `true`, OIDC authentication will be used instead of token authentication.
     * If `false`, token authentication will be used. If token is not defined, the command will fail with an error.
     */
    val oidc: Boolean by option(
        "-o",
        "--oidc",
        help = "Use OIDC authentication instead of token authentication (default: $DEFAULT_OIDC)"
    ).boolean().default(DEFAULT_OIDC)

    /**
     * Port for the OIDC callback server.
     * The default value is 8250, which is the default port used by Vault for its development server.
     */
    val oidcPort: Int by option(
        "--oidc-port",
        help = "OIDC authentication callback server port (default: $DEFAULT_OIDC_PORT)"
    ).int().default(DEFAULT_OIDC_PORT)

    /**
     * `true` if OIDC authentication should be processed in parallel for all clients,
     * `false` if it should be processed sequentially.
     */
    @Suppress("ktlint:standard:max-line-length")
    val oidcStrategy: OIDCAuthenticationStrategy by option(
        "--oidc-parallel",
        help = "OIDC authentication is processed in parallel for all clients instead of sequentially (default: $DEFAULT_OIDC_PARALLEL)"
    ).boolean().default(DEFAULT_OIDC_PARALLEL).convert {
        if (it) OIDCAuthenticationParallelStrategy else OIDCAuthenticationSequentialStrategy
    }

    /**
     * Timeout for the OIDC authentication process.
     * If the OIDC authentication process takes longer than this duration, it will be canceled and an error will be thrown.
     */
    val oidcTimeout: Duration by option(
        "--oidc-timeout",
        help = "Timeout for the OIDC authentication process (default: ${defaultOidcTimeout.inWholeSeconds} seconds)"
    ).long().convert { it.seconds }.default(defaultOidcTimeout).validate {
        require(it > Duration.ZERO) { "OIDC timeout must be a positive duration, but was $it" }
    }

    /**
     * Returns the appropriate [VaultClientStrategy] based on the provided authentication options.
     *
     * @return a [VaultClientStrategy] instance that can be used to create and authenticate Vault clients.
     */
    fun getClientAuthenticationStrategy(command: BaseCliktCommand<*>): VaultClientStrategy = when {
        this.oidc -> VaultClientOIDCStrategy(
            authenticationStrategy = this.oidcStrategy,
            port = this.oidcPort,
            loginTimeout = this.oidcTimeout,
            browserLauncher = this.browserLauncher,
            onBrowserOpenFailed = { authUrl ->
                command.echo(
                    """
                        Failed to open the browser for OIDC authentication. Please open the following URL in your browser to complete authentication:
                        $authUrl
                    """.trimIndent(),
                    err = true
                )
            }
        )

        !this.token.isNullOrBlank() -> VaultClientTokenStrategy(this.token!!)

        else -> throw NoAuthStrategyProvidedException()
    }
}
