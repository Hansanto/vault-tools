package io.github.hansanto.vaulttools.app

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.promise

/**
 * Reference to the global Node.js `process` object.
 */
private external val process: dynamic

actual fun main(args: Array<String>) {
    // For OIDC authentication, if the browser cannot be opened automatically,
    // the user needs to click / copy paste the URL in the browser.
    // However, if the user presses Ctrl+C, the program will not be killed.
    // To fix this, we listen to the SIGINT and SIGTERM signals and force the process to exit with the appropriate exit code.
    // Values 0–127 are reserved for a process's own explicit/normal exit codes
    // Values 128–255 are reserved to indicate the process was terminated by a signal, encoded as 128 + signal_number
    // List of Signal Numbers: https://man7.org/linux/man-pages/man7/signal.7.html
    process.on("SIGINT") { process.exit(130) } // 130 = 128 + 2 (SIGINT)
    process.on("SIGTERM") { process.exit(143) } // 143 = 128 + 15 (SIGTERM)

    GlobalScope.promise {
        executeProgram(args)
    }
}
