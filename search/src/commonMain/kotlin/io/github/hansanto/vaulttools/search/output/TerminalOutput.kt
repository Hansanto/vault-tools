package io.github.hansanto.vaulttools.search.output

import com.github.ajalt.mordant.terminal.Terminal
import io.github.hansanto.vaulttools.search.render.Render
import io.github.hansanto.vaulttools.search.service.VaultSearchResult

open class TerminalOutput(private val terminal: Terminal, val separator: String, val render: Render) : ResultOutput {
    override suspend fun write(results: Map<String?, VaultSearchResult>) {
        val resultTexts = results.map { (namespace, result) ->
            render.renderString(namespace, result)
        }
        terminal.println(resultTexts.joinToString(separator = separator))
    }
}
