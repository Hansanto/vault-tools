package io.github.hansanto.vaulttools.search.render

import io.github.hansanto.vaulttools.search.service.VaultSearchResult

/**
 * A render that combines multiple renders into one, separating their outputs with a specified separator.
 *
 * @property renders The collection of renders to be combined.
 * @property separator The string used to separate the outputs of the individual renders.
 */
class CompoundRender(val renders: Collection<Render>, val separator: String) : Render {
    override suspend fun renderString(namespace: String?, searchResult: VaultSearchResult): RenderResult =
        renders.map { it.renderString(namespace, searchResult) }.joinToString(separator = separator)
}
