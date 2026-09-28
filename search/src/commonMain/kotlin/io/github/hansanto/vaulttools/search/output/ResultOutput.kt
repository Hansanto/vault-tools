package io.github.hansanto.vaulttools.search.output

import io.github.hansanto.vaulttools.search.service.VaultSearchResult

fun interface ResultOutput {
    /**
     * Write the search results to the output.
     * @param results A map of namespace to VaultSearchResult containing the search results.
     */
    suspend fun write(results: Map<String?, VaultSearchResult>)
}
