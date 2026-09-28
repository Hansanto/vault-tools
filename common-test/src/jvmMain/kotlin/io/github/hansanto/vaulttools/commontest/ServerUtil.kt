package io.github.hansanto.vaulttools.commontest

import java.net.ServerSocket

/**
 * Check if a given port is already in use by trying to bind a server socket to it.
 *
 * @param port The port number to check.
 * @return `true` if the port is in use, `false` otherwise.
 */
fun isPortInUse(port: Int): Boolean = try {
    ServerSocket(port).close()
    false
} catch (e: Exception) {
    true
}

/**
 * Find an available port by creating a server socket with port 0.
 * The OS will assign an available port, which we can then retrieve and return.
 *
 * @return An available port number that can be used for testing purposes.
 */
fun getAvailablePort(): Int = ServerSocket(0).use { it.localPort }
