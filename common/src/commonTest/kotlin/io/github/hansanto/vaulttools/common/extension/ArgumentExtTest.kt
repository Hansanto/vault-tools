package io.github.hansanto.vaulttools.common.extension

import com.github.ajalt.clikt.core.BadParameterValue
import com.github.ajalt.clikt.core.NoOpCliktCommand
import com.github.ajalt.clikt.core.parse
import com.github.ajalt.clikt.parameters.arguments.argument
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.ktor.http.Url

class ArgumentExtTest :
    ShouldSpec({

        class TestCommand : NoOpCliktCommand() {
            val url: Url by argument(
                name = "url",
            ).url()
        }

        for (url in listOf(
            "http://example.com",
            "https://example.com",
            "https://example.com/path/to/resource",
            "https://example.com:8080",
            "https://example.com:8080/path/to/resource",
            "https://example.com?query=param",
            "https://example.com#fragment",
            "https://example.com?query=param#fragment",
            "https://example.com:8080?query=param#fragment",
            "https://example.com:8080/path/to/resource?query=param#fragment",
            "http:///path",
            "http://example.com/path with spaces",
            "htp://example.com"
        )) {
            should("url should convert '$url' to Url instance") {
                val cmd = TestCommand()
                val args = listOf(url)
                cmd.parse(args)
                cmd.url shouldBe Url(url)
            }
        }

        for (url in listOf(
            "invalid-url",
            "example.com",
            "http:/example.com",
            "",
            "/path/to/resource",
            "../path/to/resource",
            "./path/to/resource",
            "???",
            "http://"
        )) {
            should("url should fail when url $url doesn't contain a host") {
                val cmd = TestCommand()
                val args = listOf(url)

                shouldThrow<BadParameterValue> {
                    cmd.parse(args)
                } shouldBe
                    BadParameterValue("'$url' is not a valid URL: The URL must contain a host (e.g., 'example.com')")
            }
        }

        for (url in listOf(
            "://example.com",
            "http://example.com:abc",
            "http://example.com:99999",
            "http://exa mple.com",
        )) {
            should("url should fail for invalid URL '$url'") {
                val cmd = TestCommand()
                val args = listOf(url)

                shouldThrow<BadParameterValue> {
                    cmd.parse(args)
                } shouldBe BadParameterValue("'$url' is not a valid URL: Fail to parse url: $url")
            }
        }
    })
