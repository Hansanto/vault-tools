package io.github.hansanto.vaulttools.common.client.oidc

import io.github.hansanto.kault.VaultClient
import io.github.hansanto.kault.auth.common.response.LoginResponse
import io.github.hansanto.vaulttools.commontest.OIDC_PORT
import io.github.hansanto.vaulttools.commontest.assertCanCreateSecret
import io.github.hansanto.vaulttools.commontest.configureOIDC
import io.github.hansanto.vaulttools.commontest.createVaultEnterpriseClient
import io.github.hansanto.vaulttools.commontest.getAvailablePort
import io.github.hansanto.vaulttools.commontest.isPortInUse
import io.github.hansanto.vaulttools.commontest.listener.PlaywrightOIDCFlowListener
import io.github.hansanto.vaulttools.commontest.listener.VaultListener
import io.github.hansanto.vaulttools.commontest.randomLoginResponse
import io.github.hansanto.vaulttools.shared.BrowserLauncher
import io.github.hansanto.vaulttools.shared.util.randomString
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode.Companion.OK
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

private const val MISSING_CODE_ERROR_RESPONSE = "Missing 'code' parameter"
private const val MISSING_STATE_ERROR_RESPONSE = "Missing 'state' parameter"
private const val EXPECTED_STATE_ERROR_RESPONSE = "Invalid state parameter"
private const val EXPECTED_SUCCESS_RESPONSE = "Authentication successful! You can close this window."
private const val EXPECTED_ERROR_RESPONSE = "Authentication failed: %s"

class OIDCCallbackServerTest :
    ShouldSpec({

        val playwrightListener = PlaywrightOIDCFlowListener()
        extensions(playwrightListener, VaultListener)

        lateinit var rootClient: VaultClient
        lateinit var httpClient: HttpClient

        beforeSpec {
            rootClient = createVaultEnterpriseClient()
            configureOIDC(rootClient)

            httpClient = HttpClient()
        }

        afterSpec {
            rootClient.close()
            httpClient.close()
        }

        should("have correct properties based on constructor parameters") {
            repeat(3) {
                val availablePort = getAvailablePort()
                withCallbackServer(
                    browserLauncher = playwrightListener,
                    port = availablePort,
                    start = false
                ) {
                    this.url shouldBe "http://localhost:$availablePort/oidc/callback"
                    this.handlers shouldBe emptyMap()
                    isPortInUse(availablePort) shouldBe false
                }
            }
        }

        should("register handler throw exception if state already exists") {
            withCallbackServer(browserLauncher = playwrightListener, start = false) {
                var callbackCalled = false
                var loginSucceed = false
                var loginError = false

                val oidcCallback: OIDCCallback = { _, _, _ ->
                    callbackCalled = true
                    error("Not implemented")
                }
                val oidcOnLoginSucceed: OIDCOnLoginSucceed = { loginSucceed = true }
                val oidcOnLoginError: OIDCOnLoginError = { loginError = true }

                val state = randomString()

                val expectedHandlers = mapOf(
                    state to CallbackHandler(
                        oidcCallback = oidcCallback,
                        onLoginSucceed = oidcOnLoginSucceed,
                        onLoginError = oidcOnLoginError
                    )
                )

                registerHandler(
                    state = state,
                    callback = oidcCallback,
                    onLoginSucceed = oidcOnLoginSucceed,
                    onLoginError = oidcOnLoginError
                )

                handlers shouldBe expectedHandlers

                shouldThrow<IllegalStateException> {
                    registerHandler(
                        state = state,
                        callback = oidcCallback,
                        onLoginSucceed = oidcOnLoginSucceed,
                        onLoginError = oidcOnLoginError
                    )
                }

                handlers shouldBe expectedHandlers

                callbackCalled shouldBe false
                loginSucceed shouldBe false
                loginError shouldBe false
            }
        }

        should("register, unregister and get handler are thread safe") {
            withCallbackServer(browserLauncher = playwrightListener, start = false) {
                var callbackCalled = false
                var loginSucceed = false
                var loginError = false

                val oidcCallback: OIDCCallback = { _, _, _ ->
                    callbackCalled = true
                    error("Not implemented")
                }
                val oidcOnLoginSucceed: OIDCOnLoginSucceed = { loginSucceed = true }
                val oidcOnLoginError: OIDCOnLoginError = { loginError = true }

                val states = List(30) { "state-$it" }

                states.map { state ->
                    async {
                        registerHandler(
                            state = state,
                            callback = oidcCallback,
                            onLoginSucceed = oidcOnLoginSucceed,
                            onLoginError = oidcOnLoginError
                        )
                    }
                }.awaitAll()

                handlers shouldBe states.associateWith { _ ->
                    CallbackHandler(oidcCallback, oidcOnLoginSucceed, oidcOnLoginError)
                }

                states.map { state ->
                    async {
                        getHandler(state) shouldBe CallbackHandler(oidcCallback, oidcOnLoginSucceed, oidcOnLoginError)
                        unregisterHandler(state)
                    }
                }.awaitAll()

                handlers shouldBe emptyMap()
                callbackCalled shouldBe false
                loginSucceed shouldBe false
                loginError shouldBe false
            }
        }

        should("server response error when code is missing") {
            withCallbackServer(browserLauncher = playwrightListener) {
                val response = httpClient.get(this.url)
                response.status shouldBe OK
                response.bodyAsText() shouldBe MISSING_CODE_ERROR_RESPONSE
            }
        }

        should("server response error when state is missing") {
            withCallbackServer(browserLauncher = playwrightListener) {
                val response = httpClient.get(this.url) {
                    url {
                        parameters.append("code", "test-code")
                    }
                }
                response.status shouldBe OK
                response.bodyAsText() shouldBe MISSING_STATE_ERROR_RESPONSE
            }
        }

        should("server response error when state is not registered") {
            withCallbackServer(browserLauncher = playwrightListener) {
                val response = httpClient.get(this.url) {
                    url {
                        parameters.append("code", "test-code")
                        parameters.append("state", "invalid-state")
                    }
                }
                response.status shouldBe OK
                response.bodyAsText() shouldBe EXPECTED_STATE_ERROR_RESPONSE
            }
        }

        should("server response error when handler throws exception") {
            withCallbackServer(browserLauncher = playwrightListener) {
                val expectedCode = randomString()
                val expectedState = randomString()
                val exceptionMessage = randomString()
                val expectedException = RuntimeException(exceptionMessage)

                var errorCatcher = CompletableDeferred<Throwable>()

                val oidcCallback: OIDCCallback = { code, state, nonce ->
                    code shouldBe expectedCode
                    state shouldBe expectedState
                    nonce shouldBe null

                    throw expectedException
                }
                val oidcOnLoginSucceed: OIDCOnLoginSucceed = {
                    errorCatcher.completeExceptionally(
                        IllegalStateException("Login should not succeed when callback throws exception")
                    )
                }
                val oidcOnLoginError: OIDCOnLoginError = {
                    errorCatcher.complete(it)
                }

                registerHandler(
                    state = expectedState,
                    callback = oidcCallback,
                    onLoginSucceed = oidcOnLoginSucceed,
                    onLoginError = oidcOnLoginError
                )

                val response = httpClient.get(this.url) {
                    url {
                        parameters.append("code", expectedCode)
                        parameters.append("state", expectedState)
                    }
                }
                response.status shouldBe OK
                response.bodyAsText() shouldBe EXPECTED_ERROR_RESPONSE.format(exceptionMessage)

                val caughtException = errorCatcher.await()
                caughtException shouldBe expectedException

                handlers shouldBe emptyMap()
            }
        }

        should("server response success without nonce") {
            withCallbackServer(browserLauncher = playwrightListener) {
                val expectedCode = randomString()
                val expectedState = randomString()

                val loginResponse = randomLoginResponse()
                val loginCatcher = CompletableDeferred<LoginResponse>()

                val oidcCallback: OIDCCallback = { code, state, nonce ->
                    code shouldBe expectedCode
                    state shouldBe expectedState
                    nonce shouldBe null

                    loginResponse
                }
                val oidcOnLoginSucceed: OIDCOnLoginSucceed = {
                    loginCatcher.complete(it)
                }
                val oidcOnLoginError: OIDCOnLoginError = {
                    loginCatcher.completeExceptionally(it)
                }

                registerHandler(
                    state = expectedState,
                    callback = oidcCallback,
                    onLoginSucceed = oidcOnLoginSucceed,
                    onLoginError = oidcOnLoginError
                )

                val response = httpClient.get(this.url) {
                    url {
                        parameters.append("code", expectedCode)
                        parameters.append("state", expectedState)
                    }
                }
                response.status shouldBe OK
                response.bodyAsText() shouldBe EXPECTED_SUCCESS_RESPONSE

                loginCatcher.await() shouldBe loginResponse
                handlers shouldBe emptyMap()
            }
        }

        should("server response success with nonce") {
            withCallbackServer(browserLauncher = playwrightListener) {
                val expectedCode = randomString()
                val expectedState = randomString()
                val expectedNonce = randomString()

                val loginResponse = randomLoginResponse()
                val loginCatcher = CompletableDeferred<LoginResponse>()

                val oidcCallback: OIDCCallback = { code, state, nonce ->
                    code shouldBe expectedCode
                    state shouldBe expectedState
                    nonce shouldBe expectedNonce

                    loginResponse
                }
                val oidcOnLoginSucceed: OIDCOnLoginSucceed = {
                    loginCatcher.complete(it)
                }
                val oidcOnLoginError: OIDCOnLoginError = {
                    loginCatcher.completeExceptionally(it)
                }

                registerHandler(
                    state = expectedState,
                    callback = oidcCallback,
                    onLoginSucceed = oidcOnLoginSucceed,
                    onLoginError = oidcOnLoginError
                )

                val response = httpClient.get(this.url) {
                    url {
                        parameters.append("code", expectedCode)
                        parameters.append("state", expectedState)
                        parameters.append("nonce", expectedNonce)
                    }
                }
                response.status shouldBe OK
                response.bodyAsText() shouldBe EXPECTED_SUCCESS_RESPONSE

                loginCatcher.await() shouldBe loginResponse
                handlers shouldBe emptyMap()
            }
        }

        should("stop do nothing when server is not started") {
            val availablePort = getAvailablePort()
            withCallbackServer(browserLauncher = playwrightListener, port = availablePort, start = false) {
                isPortInUse(availablePort) shouldBe false
                stop()
                isPortInUse(availablePort) shouldBe false
            }
        }

        should("stop server will free the port") {
            val availablePort = getAvailablePort()
            withCallbackServer(browserLauncher = playwrightListener, port = availablePort) {
                isPortInUse(availablePort) shouldBe true
                stop()
                isPortInUse(availablePort) shouldBe false
            }
        }

        should("authenticate throw exception after timeout to login is exceeded when browser is opened") {
            val availablePort = getAvailablePort()
            val ms = 30
            val loginTimeout = ms.milliseconds
            val expectedException = OIDCLoginTimeoutException(loginTimeout)

            withCallbackServer(
                // Returns `true` to say "The browser has been launched and the login is in progress"
                // Useful to simulate a long login process to test the timeout without having to interact with the browser
                browserLauncher = { true },
                port = availablePort,
                start = true,
                loginTimeout = loginTimeout
            ) {
                createVaultEnterpriseClient().use { vaultClient ->
                    configureOIDC(vaultClient)

                    shouldThrow<OIDCLoginTimeoutException> {
                        authenticate(vaultClient) { error("Should never happen") }
                    } shouldBe expectedException
                }

                handlers shouldBe emptyMap()
            }
        }

        should("authenticate throw exception after timeout to login is exceeded when browser is waiting to be opened") {
            val availablePort = getAvailablePort()
            val ms = 50
            val loginTimeout = ms.milliseconds
            val expectedException = OIDCLoginTimeoutException(loginTimeout)

            withCallbackServer(
                browserLauncher = {
                    delay(loginTimeout * 2)
                    error("Should never happen")
                },
                port = availablePort,
                start = true,
                loginTimeout = loginTimeout
            ) {
                createVaultEnterpriseClient().use { vaultClient ->
                    configureOIDC(vaultClient)

                    shouldThrow<OIDCLoginTimeoutException> {
                        authenticate(vaultClient) { error("Should never happen") }
                    } shouldBe expectedException
                }

                handlers shouldBe emptyMap()
            }
        }

        should("authenticate execute lambda passed to authenticate when browser open failed by returning false") {
            val availablePort = getAvailablePort()
            val exception = Exception(randomString())

            withCallbackServer(
                browserLauncher = { false },
                port = availablePort,
                start = true,
            ) {
                createVaultEnterpriseClient().use { vaultClient ->
                    configureOIDC(vaultClient)

                    shouldThrowAny {
                        authenticate(vaultClient) {
                            // Stop the authentication process
                            throw exception
                        }
                    } shouldBe exception
                }

                handlers shouldBe emptyMap()
            }
        }

        should("authenticate execute lambda passed to authenticate when browser open failed by throwing exception") {
            val availablePort = getAvailablePort()
            val exception = Exception(randomString())
            val secondException = Exception(randomString())

            withCallbackServer(
                browserLauncher = { throw exception },
                port = availablePort,
                start = true,
            ) {
                createVaultEnterpriseClient().use { vaultClient ->
                    configureOIDC(vaultClient)

                    shouldThrowAny {
                        authenticate(vaultClient) {
                            // Stop the authentication process
                            // Also means that the exception thrown by the browser launcher should be ignored
                            // to call the lambda
                            throw secondException
                        }
                    } shouldBe secondException
                }

                handlers shouldBe emptyMap()
            }
        }

        should("authenticate store the login response inside client after successful login") {
            lateinit var oidcCallbackServer: OIDCCallbackServer
            var state: String? = null
            withCallbackServer(
                port = OIDC_PORT,
                start = true,
                browserLauncher = {
                    state = oidcCallbackServer.handlers.keys.first()
                    playwrightListener.open(it)
                }
            ) {
                oidcCallbackServer = this
                createVaultEnterpriseClient().use { vaultClient ->
                    val initialToken = vaultClient.auth.getTokenString()

                    configureOIDC(vaultClient)
                    authenticate(vaultClient) { error("Should never happen") }

                    initialToken shouldNotBe vaultClient.auth.getTokenString()
                    assertCanCreateSecret(vaultClient)
                }

                handlers shouldBe emptyMap()

                val urls = playwrightListener.captureUrls
                urls.size shouldBe 1

                val url = urls.first()
                val parameters = url.parameters
                parameters["state"] shouldBe state
            }
        }
    })

private suspend inline fun withCallbackServer(
    browserLauncher: BrowserLauncher,
    port: Int = OIDC_PORT,
    loginTimeout: Duration = Duration.INFINITE,
    start: Boolean = true,
    test: suspend OIDCCallbackServer.() -> Unit
) {
    val callbackServer = OIDCCallbackServer(
        port = port,
        loginTimeout = loginTimeout,
        browserLauncher = browserLauncher
    )
    try {
        if (start) {
            callbackServer.start()
        }
        callbackServer.test()
    } finally {
        callbackServer.stop()
    }
}
