@file:JvmName("Main")

package io.github.hansanto.vaulttools.app

import kotlinx.coroutines.runBlocking

actual fun main(args: Array<String>) {
    runBlocking {
        executeProgram(args)
    }
}
