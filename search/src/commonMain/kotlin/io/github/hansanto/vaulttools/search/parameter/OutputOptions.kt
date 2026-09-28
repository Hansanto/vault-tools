package io.github.hansanto.vaulttools.search.parameter

import com.github.ajalt.clikt.core.BaseCliktCommand
import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.terminal
import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.boolean
import io.github.hansanto.vaulttools.search.output.ResultOutput
import io.github.hansanto.vaulttools.search.output.TerminalOutput
import io.github.hansanto.vaulttools.search.render.CompoundRender
import io.github.hansanto.vaulttools.search.render.JsonRender
import io.github.hansanto.vaulttools.search.render.Render
import io.github.hansanto.vaulttools.search.render.TextRender

private const val DEFAULT_TERMINAL_TEXT = true
private const val DEFAULT_TERMINAL_JSON = false

/**
 * Separator to correctly separate the output of multiple renders when using [CompoundRender].
 */
private const val TERMINAL_RENDER_SEPARATOR = "\n\n"

class OutputOptions : OptionGroup(name = "Output options") {

    val terminalText: Boolean by option(
        "-ott",
        "--output-terminal-text",
        help = "Use terminal text render for displaying search results (default: $DEFAULT_TERMINAL_TEXT)"
    ).boolean()
        .default(DEFAULT_TERMINAL_TEXT)

    val terminalJson: Boolean by option(
        "-otj",
        "--output-terminal-json",
        help = "Use terminal JSON render for displaying search results (default: $DEFAULT_TERMINAL_JSON)"
    ).boolean()
        .default(DEFAULT_TERMINAL_JSON)

    /**
     * Returns a list of [ResultOutput]s based on the selected render options.
     *
     * @param command Command to retrieve context information (e.g., terminal) for output.
     * @return List of [ResultOutput]s corresponding to the selected render options.
     */
    fun getOutputs(command: BaseCliktCommand<*>): List<ResultOutput> = buildList {
        val terminalRenders = mutableListOf<Render>()
        if (terminalText) {
            terminalRenders += TextRender
        }
        if (terminalJson) {
            terminalRenders += JsonRender
        }

        if (terminalRenders.isNotEmpty()) {
            val render = CompoundRender(terminalRenders, TERMINAL_RENDER_SEPARATOR)
            add(TerminalOutput(command.terminal, TERMINAL_RENDER_SEPARATOR, render))
        }
    }
}

class NoOutputSelectedException :
    CliktError(
        """
        |Please select at least one output option to display the search results.
        """.trimMargin()
    )
