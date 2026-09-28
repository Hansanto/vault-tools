package io.github.hansanto.vaulttools.commontest.listener

import com.microsoft.playwright.Browser
import com.microsoft.playwright.BrowserType
import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright
import com.microsoft.playwright.options.AriaRole
import io.github.hansanto.vaulttools.commontest.KeycloakUtil
import io.github.hansanto.vaulttools.shared.BrowserLauncher
import io.kotest.core.listeners.AfterSpecListener
import io.kotest.core.listeners.BeforeSpecListener
import io.kotest.core.listeners.BeforeTestListener
import io.kotest.core.spec.Spec
import io.kotest.core.test.TestCase
import io.ktor.http.Url
import kotlinx.coroutines.yield
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.decrementAndFetch
import kotlin.concurrent.atomics.incrementAndFetch

class PlaywrightOIDCFlowListener :
    BeforeSpecListener,
    AfterSpecListener,
    BeforeTestListener,
    BrowserLauncher {

    private lateinit var playwright: Playwright
    private lateinit var browser: Browser

    // Because the authentication to OIDC provider is the same for all clients,
    // we can store the storage state after the first authentication and reuse it for the next ones
    // to speed up the tests and avoid unnecessary logins to the OIDC provider.
    private var state: String? = null

    private var counterSimultaneousAuthentication = AtomicInt(0)
    var maxCounterSimultaneousAuthentication = 0

    private var _captureUrls = mutableListOf<Url>()
    val captureUrls: List<Url> get() = _captureUrls

    override suspend fun beforeSpec(spec: Spec) {
        state = null
        playwright = Playwright.create()
        browser = playwright.chromium().launch(
            BrowserType.LaunchOptions().apply {
                headless = true
            }
        )
    }

    override suspend fun beforeTest(testCase: TestCase) {
        counterSimultaneousAuthentication.exchange(0)
        maxCounterSimultaneousAuthentication = 0
        _captureUrls.clear()
    }

    override suspend fun afterSpec(spec: Spec) {
        playwright.close()
    }

    override suspend fun open(url: String): Boolean {
        val numberAuth = counterSimultaneousAuthentication.incrementAndFetch()
        if (numberAuth > maxCounterSimultaneousAuthentication) {
            maxCounterSimultaneousAuthentication = numberAuth
        }

        _captureUrls.add(Url(url))

        // To verify if (in case of parallel authentication) the next authentication is really done in parallel with other ones,
        // we need to yield the thread to let the next authentication start before doing the authentication in the browser.
        // This is not possible to interact with Playwright in a new thread.
        yield()

        return browser.newContext(
            Browser.NewContextOptions().apply {
                setStorageState(state)
            }
        ).use {
            val page = it.newPage()
            page.navigate(url)

            if (state == null) {
                page.locator("#username").fill(KeycloakUtil.USERNAME)
                page.locator("#password").fill(KeycloakUtil.PASSWORD)
                page.getByRole(AriaRole.BUTTON, Page.GetByRoleOptions().setName("Sign In")).click()
                state = page.context().storageState()
            }

            counterSimultaneousAuthentication.decrementAndFetch()
            true
        }
    }
}
