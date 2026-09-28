package io.github.hansanto.vaulttools.common.extension

import io.github.hansanto.kault.VaultClient
import io.github.hansanto.vaulttools.common.exception.VaultUnreachableException
import io.ktor.client.request.get
import io.ktor.http.isSuccess

/**
 * Pings the Vault server to check if it is reachable.
 *
 * @param client The Vault client to use for the ping request.
 */
suspend fun checkReachability(client: VaultClient) {
    try {
        val result = client.client.get("/")
        if (!result.status.isSuccess()) {
            throw VaultUnreachableException()
        }
    } catch (e: Exception) {
        throw VaultUnreachableException(e)
    }
}
