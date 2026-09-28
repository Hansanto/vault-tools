package io.github.hansanto.vaulttools.search.parameter

import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.options.check
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.boolean

private const val VAULT_SEARCH_ENV = "VAULT_SEARCH"
private const val DEFAULT_ROOT_PATH = ""
private const val DEFAULT_IGNORE_CASE = false

class SearchOptions : OptionGroup(name = "Search options") {

    /**
     * Location in Vault to start the search from. If not specified, the search will start from the root of the Vault.
     */
    val rootPath: String by option(
        "-r",
        "--root",
        help = "Path to start the search from, subpaths will be searched recursively (default: <empty>)"
    ).default(DEFAULT_ROOT_PATH)

    /**
     * String to search for in Vault secrets.
     */
    val searchString: String by option(
        "-s",
        "--search",
        help = "String to search for in Vault secrets (can also be set via $VAULT_SEARCH_ENV environment variable)",
        envvar = VAULT_SEARCH_ENV
    ).required()
        .check("Search string cannot be empty") {
            it.isNotBlank()
        }

    /**
     * `true` if the search should ignore case when searching for the string, `false` otherwise.
     */
    val ignoreCase: Boolean by option(
        "-i",
        "--ignore-case",
        help = "Ignore case when searching for the string (default: $DEFAULT_IGNORE_CASE)"
    ).boolean().default(DEFAULT_IGNORE_CASE)
}
