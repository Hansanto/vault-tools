package io.github.hansanto.vaulttools.search.parameter

import com.github.ajalt.clikt.core.BadParameterValue
import com.github.ajalt.clikt.core.MissingOption
import com.github.ajalt.clikt.core.NoOpCliktCommand
import com.github.ajalt.clikt.core.parse
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe

class SearchOptionsTest :
    ShouldSpec({

        class TestCommand : NoOpCliktCommand() {
            val searchOptions by SearchOptions()
        }

        should("have default values") {
            val cmd = TestCommand()
            cmd.parse(listOf("-s", "test"))
            cmd.searchOptions.rootPath shouldBe ""
            cmd.searchOptions.searchString shouldBe "test"
            cmd.searchOptions.ignoreCase shouldBe false
        }

        should("fail when searchString is not provided") {
            val cmd = TestCommand()
            shouldThrow<MissingOption> {
                cmd.parse(emptyList())
            }
        }

        should("fail when searchString is empty") {
            val cmd = TestCommand()
            shouldThrow<BadParameterValue> {
                cmd.parse(listOf("-s", ""))
            }
        }

        should("fail when searchString is blank") {
            val cmd = TestCommand()
            shouldThrow<BadParameterValue> {
                cmd.parse(listOf("-s", "   "))
            }
        }

        for (name in listOf(
            "-s",
            "--search"
        )) {
            should("searchString accept value with option $name") {
                val cmd = TestCommand()
                val searchValue = "test-search-string"
                val args = listOf(name, searchValue)
                cmd.parse(args)
                cmd.searchOptions.searchString shouldBe searchValue
            }
        }

        for (name in listOf(
            "-r",
            "--root"
        )) {
            should("rootPath accept value with option $name") {
                val cmd = TestCommand()
                val rootValue = "path/to/root"
                val args = listOf(name, rootValue, "-s", "test")
                cmd.parse(args)
                cmd.searchOptions.rootPath shouldBe rootValue
            }
        }

        for (name in listOf(
            "-i",
            "--ignore-case"
        )) {
            for (ignoreCase in listOf(
                false,
                true
            )) {
                should("ignoreCase convert '$ignoreCase' to Boolean instance with option $name") {
                    val cmd = TestCommand()
                    val args = listOf(name, ignoreCase.toString(), "-s", "test")
                    cmd.parse(args)
                    cmd.searchOptions.ignoreCase shouldBe ignoreCase
                }
            }
        }
    })
