package io.github.hansanto.vaulttools.search.render

import io.github.hansanto.vaulttools.search.service.VaultSearchResult

typealias RenderResult = String

fun interface Render {

    /**
     * Render the search results for a given namespace and return it as a string.
     *
     * @param namespace The namespace for which the search results were obtained.
     * @param searchResult The search result to render.
     * @return A string representation of the rendered search results.
     */
    suspend fun renderString(namespace: String?, searchResult: VaultSearchResult): RenderResult
}
