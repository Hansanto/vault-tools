package io.github.hansanto.vaulttools.shared

import kotlinx.coroutines.await
import kotlin.js.Promise

/**
 * External declaration of the npm "open" package.
 * The package is ESM-only and exposes its main function as a default export,
 * so it is required as a module namespace object with a `default` member.
 * @see <a href="https://www.npmjs.com/package/open">open npm package</a>
 */
@JsModule("open")
@JsNonModule
external object OpenModule {
    val default: (target: String) -> Promise<dynamic>
}

actual suspend fun openBrowser(url: String): Boolean {
    OpenModule.default(url).await()
    return true
}
