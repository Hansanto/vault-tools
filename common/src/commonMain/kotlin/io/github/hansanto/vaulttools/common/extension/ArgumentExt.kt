package io.github.hansanto.vaulttools.common.extension

import com.github.ajalt.clikt.parameters.arguments.ProcessedArgument
import com.github.ajalt.clikt.parameters.arguments.RawArgument
import com.github.ajalt.clikt.parameters.arguments.convert
import io.ktor.http.URLBuilder
import io.ktor.http.Url

/**
 * Converts the argument to a Url.
 *
 * @return A processed argument of type Url.
 */
fun RawArgument.url(): ProcessedArgument<Url, Url> = convert(conversion = {
    try {
        URLBuilder(it).apply {
            require(host.isNotBlank()) {
                "The URL must contain a host (e.g., 'example.com')"
            }
        }.build()
    } catch (e: Exception) {
        fail("'$it' is not a valid URL: ${e.message}")
    }
})
