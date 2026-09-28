package io.github.hansanto.vaulttools.common.client.oidc

import com.github.ajalt.clikt.core.CliktError
import io.github.hansanto.kault.VaultClient
import io.github.hansanto.kault.auth.common.response.LoginResponse
import io.github.hansanto.kault.auth.oidc.oidcAuthorizationUrl
import io.github.hansanto.kault.auth.oidc.oidcCallback
import io.github.hansanto.vaulttools.shared.BrowserLauncher
import io.github.hansanto.vaulttools.shared.DefaultBrowserLauncher
import io.github.hansanto.vaulttools.shared.util.randomString
import io.ktor.http.Url
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EngineConnectorBuilder
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration

/**
 * Callback to execute when the user is redirected back to the OIDC callback URL after authenticating with the OIDC provider.
 */
typealias OIDCCallback = suspend (code: String, state: String, nonce: String?) -> LoginResponse

/**
 * Callback to execute when the user is redirected back to the OIDC callback URL after authenticating with the OIDC provider and the OIDC callback is successfully validated with Vault.
 */
typealias OIDCOnLoginSucceed = suspend (LoginResponse) -> Unit

/**
 * Callback to execute when the user is redirected back to the OIDC callback URL after authenticating with the OIDC provider but the OIDC callback validation with Vault fails, or when any error occurs during the OIDC callback handling.
 */
typealias OIDCOnLoginError = suspend (Throwable) -> Unit

/**
 * Callback when the browser fails to open the OIDC authentication URL.
 */
typealias OIDCOnBrowserOpenFailed = suspend (String) -> Unit

open class OIDCException(message: String, cause: Exception? = null) : CliktError(message, cause)

class OIDCAuthorizationUrlException(cause: Exception? = null) :
    OIDCException(
        """
    |Failed to generate the OIDC authorization URL.
    |Please check your Vault server configuration and ensure that the OIDC authentication method is properly configured.
        """.trimMargin(),
        cause
    )

class OIDCMissingStateParameterException :
    OIDCException(
        """
    |Missing 'state' parameter in the OIDC callback URL.
    |Please ensure that the OIDC provider is correctly configured and that the redirect URI is properly set.
        """.trimMargin()
    )

class OIDCLoginTimeoutException(timeout: Duration) :
    OIDCException(
        """
    |OIDC login process timed out after $timeout.
    |Please ensure that the OIDC provider is reachable.
        """.trimMargin()
    )

/**
 * Data class to hold the OIDC callback and its associated success and error handlers for a specific state parameter.
 *
 * @property oidcCallback The callback to validate the OIDC callback with Vault and obtain the login response.
 * @property onLoginSucceed The callback to execute when the OIDC callback is successfully validated with Vault.
 * @property onLoginError The callback to execute when the OIDC callback validation with Vault fails, or when any error occurs during the OIDC callback handling.
 */
data class CallbackHandler(
    val oidcCallback: OIDCCallback,
    val onLoginSucceed: OIDCOnLoginSucceed,
    val onLoginError: OIDCOnLoginError
)

/**
 * OIDC Callback Server to handle the redirect from the OIDC provider after user authentication.
 */
class OIDCCallbackServer(
    val port: Int,
    val loginTimeout: Duration,
    val browserLauncher: BrowserLauncher = DefaultBrowserLauncher
) {

    /**
     * The OIDC callback URL that Vault will redirect to after authentication.
     */
    val url = "http://localhost:$port/oidc/callback"

    private var _handlers: MutableMap<String, CallbackHandler> = mutableMapOf()

    /**
     * Map of state parameters to their corresponding callback handlers.
     */
    val handlers: Map<String, CallbackHandler> get() = _handlers

    /**
     * Mutex to ensure thread-safe operations on the handlers map.
     */
    private val mutexHandlers = Mutex()

    private val server = embeddedServer(CIO, configure = {
        connectors.add(
            EngineConnectorBuilder().apply {
                this.port = this@OIDCCallbackServer.port
            }
        )
        reuseAddress = true
    }) {
        routing {
            get("/oidc/callback") {
                val code = call.parameters["code"]
                val state = call.parameters["state"]
                val nonce = call.parameters["nonce"]

                if (code == null) {
                    call.respondText("Missing 'code' parameter")
                    return@get
                }

                if (state == null) {
                    call.respondText("Missing 'state' parameter")
                    return@get
                }

                val handler = getHandler(state)
                if (handler == null) {
                    call.respondText("Invalid state parameter")
                    return@get
                }

                val loginResult: LoginResponse
                try {
                    loginResult = handler.oidcCallback(code, state, nonce)
                    call.respondText("Authentication successful! You can close this window.")
                } catch (e: Exception) {
                    call.respondText("Authentication failed: ${e.message}")
                    handler.onLoginError(e)
                    return@get
                } finally {
                    unregisterHandler(state)
                }

                handler.onLoginSucceed(loginResult)
            }
        }
    }

    /**
     * Register a callback handler for a specific state parameter.
     * @param state The state parameter obtained from the OIDC authorization URL.
     * @param callback Action to validate the OIDC callback with Vault.
     * @param onLoginSucceed Success handler.
     * @param onLoginError Error handler.
     */
    suspend fun registerHandler(
        state: String,
        callback: OIDCCallback,
        onLoginSucceed: OIDCOnLoginSucceed,
        onLoginError: OIDCOnLoginError
    ) {
        mutexHandlers.withLock {
            if (_handlers.containsKey(state)) {
                throw IllegalStateException("Handler already registered for state: $state")
            }

            _handlers[state] = CallbackHandler(callback, onLoginSucceed, onLoginError)
        }
    }

    /**
     * Unregister a callback handler for a specific state.
     * @param state The state parameter obtained from the OIDC authorization URL.
     */
    suspend fun unregisterHandler(state: String) {
        mutexHandlers.withLock {
            _handlers.remove(state)
        }
    }

    /**
     * Get the callback handler for a specific state.
     * @param state The state received from the OIDC provider.
     * @return The corresponding [CallbackHandler], or null if not found.
     */
    suspend fun getHandler(state: String): CallbackHandler? = mutexHandlers.withLock {
        _handlers[state]
    }

    /**
     * Start the server.
     */
    suspend fun start() {
        server.startSuspend(wait = false)
    }

    /**
     * Stop the server.
     */
    suspend fun stop() {
        server.stopSuspend()
    }

    /**
     * Authenticate a Vault client using OIDC authentication flow.
     * This method will open the browser for user authentication and handle the OIDC callback to complete the login process with Vault.
     *
     * @param vaultClient The Vault client to authenticate.
     */
    suspend inline fun authenticate(
        vaultClient: VaultClient,
        crossinline onBrowserOpenFailed: OIDCOnBrowserOpenFailed
    ) = coroutineScope {
        val auth = vaultClient.auth
        val oidc = auth.oidc
        val loginDeferred = CompletableDeferred<LoginResponse>()

        val clientNonce = randomString(size = 20)
        val authUrl = try {
            oidc.oidcAuthorizationUrl {
                this.redirectUri = url
                this.clientNonce = clientNonce
            }
        } catch (e: Exception) {
            throw OIDCAuthorizationUrlException(e)
        }

        val url = Url(authUrl)
        val state = url.parameters["state"] ?: throw OIDCMissingStateParameterException()

        registerHandler(
            state = state,
            onLoginError = { loginDeferred.completeExceptionally(it) },
            onLoginSucceed = { loginDeferred.complete(it) },
            callback = { code, state, nonce ->
                oidc.oidcCallback {
                    this.code = code
                    this.state = state
                    if (nonce != null) {
                        this.nonce = nonce
                    }
                    this.clientNonce = clientNonce
                }
            }
        )

        try {
            launch {
                val openResult = try {
                    browserLauncher.open(authUrl)
                } catch (_: Throwable) {
                    // Catching `Throwable` (not just `Exception`) is required here:
                    // An error thrown/rejected by external interop code cannot be caught by `Exception`.
                    false
                }
                if (!openResult) {
                    onBrowserOpenFailed(authUrl)
                }
            }

            val loginResponse = withTimeoutOrNull(loginTimeout) { loginDeferred.await() }
                ?: throw OIDCLoginTimeoutException(loginTimeout)

            auth.login { loginResponse }
        } finally {
            unregisterHandler(state)
        }
    }
}
