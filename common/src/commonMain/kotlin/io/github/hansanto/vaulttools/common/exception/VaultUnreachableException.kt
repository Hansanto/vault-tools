package io.github.hansanto.vaulttools.common.exception

import com.github.ajalt.clikt.core.CliktError

/**
 * Exception thrown when the Vault server is unreachable.
 */
class VaultUnreachableException(cause: Exception? = null) :
    CliktError(
        """
    |Could not reach Vault server.
    |Please check the URL or your network connection.
        """.trimMargin(),
        cause = cause
    )
