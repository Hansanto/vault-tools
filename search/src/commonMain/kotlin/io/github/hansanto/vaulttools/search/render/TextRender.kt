package io.github.hansanto.vaulttools.search.render

import com.github.ajalt.mordant.rendering.TextColors.gray
import com.github.ajalt.mordant.rendering.TextColors.green
import com.github.ajalt.mordant.rendering.TextColors.red
import com.github.ajalt.mordant.rendering.TextColors.yellow
import io.github.hansanto.vaulttools.search.service.VaultPath
import io.github.hansanto.vaulttools.search.service.VaultPathSecretError
import io.github.hansanto.vaulttools.search.service.VaultPathSecretFound
import io.github.hansanto.vaulttools.search.service.VaultSearchResult

/**
 * Render with text format:
 * ```
 * Namespace: <namespace or 'null'>
 * Total paths analyzed: <total>
 * Secret paths: <secretCount>
 * - '<path>' (L. <line1>, <line2>, ...)
 * Error paths: <errorCount>
 * - '<path>' (<error message>)
 * ```
 */
object TextRender : Render {

    override suspend fun renderString(namespace: String?, searchResult: VaultSearchResult): String {
        val (pathsSecretFound, pathsSecretError) = searchResult.partition()
        val totalPathAnalyzed = searchResult.result.size

        return buildString {
            append("Namespace: ")
            if (namespace == null) {
                append(gray("null"))
            } else {
                append(namespace)
            }
            appendLine()
            append("Total paths analyzed: ").append(gray(totalPathAnalyzed.toString())).appendLine()
            append("Secret paths: ").append(green(pathsSecretFound.size.toString())).appendLine()
            renderPathsFound(pathsSecretFound)
            append("Error paths: ").append(red(pathsSecretError.size.toString())).appendLine()
            renderPathsError(pathsSecretError)

            // Remove the last line break
            setLength(length - 1)
        }
    }

    /**
     * Render the paths where secrets were found, along with their corresponding secret information.
     *
     * @param stringPathsFound Path with their corresponding secrets to render.
     */
    context(stringBuilder: StringBuilder)
    private fun renderPathsFound(stringPathsFound: Map<VaultPath, VaultPathSecretFound>) {
        if (stringPathsFound.isEmpty()) {
            return
        }

        stringPathsFound.forEach { (path, secret) ->
            val lines = secret.lines
            stringBuilder.append("- '").append(yellow(path.value)).append('\'')
                .append(lines.joinToString(prefix = " (L. ", postfix = ")", separator = ", "))
                .appendLine()
        }
    }

    /**
     * Render the paths that encountered errors during analysis, along with their corresponding error messages.
     * @param stringPathsError Path with their corresponding errors to render.
     */
    context(stringBuilder: StringBuilder)
    private fun renderPathsError(stringPathsError: Map<VaultPath, VaultPathSecretError>) {
        if (stringPathsError.isEmpty()) {
            return
        }

        stringPathsError.forEach { (path, secret) ->
            val exception = secret.error
            val errorMessage = RendererUtil.getErrorMessage(exception)
            stringBuilder.append("- '").append(red(path.value)).append("' (")
                .append(gray(errorMessage))
                .append(')')
                .appendLine()
        }
    }
}
