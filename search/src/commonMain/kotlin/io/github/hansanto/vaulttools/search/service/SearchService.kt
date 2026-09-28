package io.github.hansanto.vaulttools.search.service

import io.github.hansanto.kault.VaultClient
import io.github.hansanto.kault.engine.kv.v2.VaultKV2Engine
import io.github.hansanto.vaulttools.common.Executor
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.jvm.JvmInline

/**
 * Represents the result of searching for a string in all secrets in Vault.
 */
@JvmInline
value class VaultSearchResult(val result: Map<VaultPath, VaultPathSecret>) {

    /**
     * Represents the partitioned result of a Vault search, separating found secrets, errors, and not found paths.
     */
    data class PartitionedResult(
        /**
         * A map of paths where the secret was found, along with the corresponding secret information.
         */
        val found: Map<VaultPath, VaultPathSecretFound>,
        /**
         * A map of paths to their corresponding errors encountered during the search.
         */
        val error: Map<VaultPath, VaultPathSecretError>,
        /**
         * A list of paths where the secret was not found.
         */
        val notFound: List<VaultPath>
    )

    /**
     * Partition the search result into two separate maps: one for found secrets and another for errors.
     * @return A pair of maps, where the first map contains paths with found secrets and the second map contains paths with errors.
     */
    fun partition(): PartitionedResult {
        val found = mutableMapOf<VaultPath, VaultPathSecretFound>()
        val error = mutableMapOf<VaultPath, VaultPathSecretError>()
        val notFound = mutableListOf<VaultPath>()

        result.forEach { (path, secret) ->
            when (secret) {
                is VaultPathSecretFound -> found[path] = secret
                is VaultPathSecretNotFound -> notFound.add(path)
                is VaultPathSecretError -> error[path] = secret
            }
        }

        return PartitionedResult(found, error, notFound)
    }
}

/**
 * Represents a path in Vault.
 */
@JvmInline
value class VaultPath(val value: String) {

    /**
     * Check if the path represents a folder in Vault.
     *
     * @return `true` if the path represents a folder (i.e., ends with a '/'), `false` if it represents a secret.
     */
    fun isFolder(): Boolean = value.endsWith('/')
}

/**
 * Represents the result of searching for a secret at a given path in Vault.
 * It can either be an [error][VaultPathSecretError] or the [content][VaultPathSecretFound].
 */
sealed interface VaultPathSecret

/**
 * Represents an error that occurred while trying to access a secret at a given path in Vault.
 *
 * @property error The exception that was thrown while trying to access the secret.
 */
data class VaultPathSecretError(val error: Throwable) : VaultPathSecret

/**
 * Represents the content of a secret at a given path, along with the line numbers where a search string was found.
 */
data class VaultPathSecretFound(
    /**
     * The content of the secret path as a JSON object.
     */
    val content: JsonObject,
    /**
     * The line numbers where the search string was found in the secret content.
     */
    val lines: Collection<Int>
) : VaultPathSecret

/**
 * Singleton object representing a secret path that was not found.
 */
object VaultPathSecretNotFound : VaultPathSecret

class SearchService(val kV2Engine: VaultKV2Engine, val executor: Executor, val json: Json = pretty_json) {

    companion object {
        /**
         * JSON serializer configured to pretty print the output, using the same configuration as the [VaultClient].
         */
        val pretty_json = Json(from = VaultClient.json) {
            prettyPrint = true
        }
    }

    /**
     * Search for a string in all secrets in Vault.
     *
     * @param rootPath Starting path in Vault to search from. If blank, the search will start from the root.
     * @param searchString The string to search for in the secrets.
     * @param ignoreCase `true` if the search should ignore case, `false` otherwise.
     * @return Result with the traversed paths.
     */
    suspend fun search(rootPath: String, searchString: String, ignoreCase: Boolean): VaultSearchResult =
        coroutineScope {
            val result = mutableMapOf<VaultPath, VaultPathSecret>()
            val mutex = Mutex()

            suspend fun searchInternal(path: String) {
                val listPaths = try {
                    getListSecrets(path)
                } catch (e: Exception) {
                    mutex.withLock {
                        result[VaultPath(path)] = VaultPathSecretError(e)
                    }
                    return
                }

                listPaths.map {
                    this@coroutineScope.async {
                        val secretPath = path + it
                        val vaultPath = VaultPath(secretPath)

                        when {
                            vaultPath.isFolder() -> searchInternal(secretPath)

                            else -> {
                                val secrets = try {
                                    getSecrets(secretPath)
                                } catch (e: Exception) {
                                    mutex.withLock {
                                        result[vaultPath] = VaultPathSecretError(e)
                                    }
                                    null
                                } ?: return@async

                                val secretsRaw = json.encodeToString(secrets)
                                val lineIndexes = getContainLineIndexes(secretsRaw, searchString, ignoreCase)
                                mutex.withLock {
                                    result[vaultPath] = if (lineIndexes.isEmpty()) {
                                        VaultPathSecretNotFound
                                    } else {
                                        VaultPathSecretFound(secrets, lineIndexes)
                                    }
                                }
                            }
                        }
                    }
                }.awaitAll()
            }

            searchInternal(
                when {
                    rootPath.isBlank() -> ""

                    rootPath.endsWith('/') -> rootPath

                    // Folder path must end with a '/'
                    else -> "$rootPath/"
                }
            )

            VaultSearchResult(result)
        }

    /**
     * Get the list of secrets at a given path in Vault.
     *
     * @param path The path to list secrets from.
     * @return A collection of secret names at the given path.
     */
    private suspend fun getListSecrets(path: String): Collection<String> = executor.execute {
        kV2Engine.listSecrets(path)
    }

    /**
     * Get the raw content of a secret path in Vault.
     *
     * @param path The path of the secret to retrieve.
     * @return The raw content of the secret as a string, or `null` if the secret could not be retrieved.
     */
    private suspend fun getSecrets(path: String): JsonObject? = executor.execute {
        kV2Engine.readSecret(path).data
    }?.jsonObject

    /**
     * Get the indexes of the lines that contain the search string in the given secret content.
     *
     * @param secretsRaw The full content of the secret as a string.
     * @param searchString The string to search for in the secret content.
     * @param ignoreCase Whether to ignore case when searching for the string.
     * @return A collection of line numbers (0-based) where the search string was found in the secret content.
     */
    private fun getContainLineIndexes(secretsRaw: String, searchString: String, ignoreCase: Boolean): Collection<Int> =
        buildList {
            secretsRaw.lineSequence().forEachIndexed { index, line ->
                if (line.contains(searchString, ignoreCase)) {
                    add(index)
                }
            }
        }
}
