package io.github.hansanto.vaulttools.app

import com.github.ajalt.clikt.command.SuspendingNoOpCliktCommand
import com.github.ajalt.clikt.command.main
import com.github.ajalt.clikt.core.subcommands
import io.github.hansanto.vaulttools.search.command.SearchCommand

expect fun main(args: Array<String>)

suspend fun executeProgram(args: Array<String>) = object : SuspendingNoOpCliktCommand() {}
    .subcommands(SearchCommand())
    .main(args)
