package io.github.hansanto.vaulttools.search.command

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.validate
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import io.github.hansanto.kault.VaultClient
import io.github.hansanto.vaulttools.common.Executor
import io.github.hansanto.vaulttools.common.extension.checkReachability
import io.github.hansanto.vaulttools.common.extension.url
import io.github.hansanto.vaulttools.common.extension.useAsync
import io.github.hansanto.vaulttools.common.parameter.AuthOptions
import io.github.hansanto.vaulttools.common.parameter.PerformanceOptions
import io.github.hansanto.vaulttools.search.parameter.NoOutputSelectedException
import io.github.hansanto.vaulttools.search.parameter.OutputOptions
import io.github.hansanto.vaulttools.search.parameter.SearchOptions
import io.github.hansanto.vaulttools.search.service.SearchService
import io.github.hansanto.vaulttools.search.service.VaultSearchResult
import io.github.hansanto.vaulttools.shared.BrowserLauncher
import io.github.hansanto.vaulttools.shared.DefaultBrowserLauncher
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import kotlinx.coroutines.supervisorScope

/**
 * Even if the [VaultClient] supports `null` as a namespace to target the root namespace.
 * Using an empty string works too.
 * When rendering the results, it more clearly to display "" instead of "null" for the root namespace.
 */
private const val DEFAULT_ROOT_NAMESPACE = ""

class SearchCommand(
    /**
     * Launcher to open the OIDC authentication URL in the user's browser.
     * We give the opportunity to override this for testing purposes.
     */
    browserLauncher: BrowserLauncher = DefaultBrowserLauncher
) : SuspendingCliktCommand(name = "search") {

    val url: Url by argument(
        name = "url",
        help = "Vault server URL"
    ).url()
        .validate {
            require(it.protocol == URLProtocol.HTTP || it.protocol == URLProtocol.HTTPS) {
                "URL must use HTTP(S) scheme"
            }
        }

    val namespaces: List<String> by option(
        "-n",
        "--namespaces",
        help = "Vault namespaces"
    ).convert {
        // If the user put ",my_namespace" that means he wants to search in the root namespace and in my_namespace
        it.split(",")
            .map(String::trim)
    }.default(listOf(DEFAULT_ROOT_NAMESPACE)) // Empty will use the root namespace

    val performanceOptions by PerformanceOptions()
    val authOptions by AuthOptions(browserLauncher)
    val searchOptions by SearchOptions()
    val outputOptions by OutputOptions()

    override fun help(context: Context): String = """
        |Walks through all secrets in Vault and searches for a string in their keys and values.
    """.trimMargin()

    override suspend fun run() {
        val outputs = outputOptions.getOutputs(this)
        if (outputs.isEmpty()) {
            throw NoOutputSelectedException()
        }

        val authStrategy = authOptions.getClientAuthenticationStrategy(this)
        val clients = authStrategy.createAuthenticatedClients(url.toString(), namespaces)
        checkReachability(clients.first())

        val executor = performanceOptions.getExecutor()
        val results = search(clients, executor)
        outputs.forEach { output ->
            output.write(results)
        }
    }

    private suspend fun search(clients: Collection<VaultClient>, executor: Executor): Map<String?, VaultSearchResult> =
        supervisorScope {
            val rootPath = searchOptions.rootPath
            val searchString = searchOptions.searchString
            val ignoreCase = searchOptions.ignoreCase

            clients.useAsync { client ->
                val service = SearchService(client.secret.kv2, executor)
                val searchResult = service.search(
                    rootPath = rootPath,
                    searchString = searchString,
                    ignoreCase = ignoreCase
                )
                val sortedResult = searchResult.result.toList().sortedBy { it.first.value }.toMap()
                client.namespace to VaultSearchResult(sortedResult)
            }.toMap()
        }
}
