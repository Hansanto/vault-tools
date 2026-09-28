package io.github.hansanto.vaulttools.commontest

import io.github.hansanto.kault.VaultClient
import io.github.hansanto.kault.auth.oidc.configure
import io.github.hansanto.kault.auth.oidc.createOrUpdateRole
import io.github.hansanto.kault.engine.kv.v2.createOrUpdateSecret
import io.github.hansanto.kault.system.auth.enable
import io.github.hansanto.kault.system.mounts.enableSecretsEngine
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.plugins.logging.DEFAULT
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

const val ROOT_TOKEN = "root"
const val VAULT_ENTERPRISE_URL = "http://localhost:8200"
const val OIDC_PORT = 8250
const val OIDC_ROLE = "oidc-role"
const val SECRET_POLICY = "secret-policy"

/**
 * Maximum time to wait for a data has been processed by Vault in case of async operations, such as namespace deletion.
 */
val maxCheckVaultAsyncOpTimeout = 10.seconds

/**
 * Interval between checks for data processing completion in case of async operations, such as namespace deletion.
 */
val checkVaultAsyncOpInterval = 100.milliseconds

fun createVaultEnterpriseClient(
    namespace: String? = null,
    authBuilder: VaultClient.Builder.AuthBuilder.() -> Unit = {
        autoRenewToken = false
    }
): VaultClient = createVaultClient(VAULT_ENTERPRISE_URL, namespace, authBuilder)

private fun createVaultClient(
    url: String,
    namespace: String? = null,
    authBuilder: VaultClient.Builder.AuthBuilder.() -> Unit = {
        autoRenewToken = false
    }
): VaultClient = VaultClient {
    this.url = url
    this.namespace = namespace
    auth {
        setTokenString(ROOT_TOKEN)
        authBuilder()
    }
    httpClient { headerBuilder ->
        HttpClient {
            install(Logging) {
                logger = Logger.DEFAULT
                level = LogLevel.INFO
            }
            defaultHttpClientConfiguration(headerBuilder)
        }
    }
}

suspend fun enableAuthMethod(client: VaultClient, authMethod: String) {
    runCatching {
        client.system.auth.enable(authMethod) {
            type = authMethod
        }
    }
}

suspend fun configureOIDC(client: VaultClient) {
    enableAuthMethod(client, "oidc")

    val oidc = client.auth.oidc
    oidc.configure {
        oidcDiscoveryUrl = "${KeycloakUtil.HOST}/realms/${KeycloakUtil.REALM}"
        oidcClientId = KeycloakUtil.CLIENT_ID
        oidcClientSecret = KeycloakUtil.CLIENT_SECRET
        defaultRole = OIDC_ROLE
        boundIssuer = "${KeycloakUtil.HOST}/realms/${KeycloakUtil.REALM}"
    }

    oidc.createOrUpdateRole(OIDC_ROLE) {
        userClaim = "sub"
        allowedRedirectUris = listOf("http://localhost:$OIDC_PORT/oidc/callback")
        tokenPolicies = listOf("default", SECRET_POLICY)
    }

    configureSecretPolicy(client)
    configureKV2(client)
}

suspend fun configureSecretPolicy(client: VaultClient) {
    client.system.policy.createOrUpdate(
        SECRET_POLICY,
        """
            path "secret/*" {
                capabilities = ["create", "read", "update", "delete", "list"]
            }
        """.trimIndent()
    )
}

suspend fun configureKV2(client: VaultClient, mountPath: String = "secret") {
    runCatching {
        client.system.mounts.enableSecretsEngine(mountPath) {
            type = "kv"
            kvOptions(2)
        }
    }
}

suspend fun revokeAllUserpassData(client: VaultClient) {
    val userpassService = client.auth.userpass

    runCatching { userpassService.list() }
        .onSuccess { users ->
            users.forEach { user ->
                userpassService.delete(user)
            }
        }
}

suspend fun revokeAllKubernetesData(client: VaultClient) {
    val kubernetesService = client.auth.kubernetes

    runCatching { kubernetesService.list() }
        .onSuccess { roles ->
            roles.forEach { role ->
                kubernetesService.deleteRole(role)
            }
        }
}

suspend fun revokeAllOIDCData(client: VaultClient) {
    val oidcService = client.auth.oidc

    runCatching { oidcService.list() }
        .onSuccess { roles ->
            roles.forEach { role ->
                oidcService.deleteRole(role)
            }
        }
}

suspend fun revokeAllAppRoleData(client: VaultClient) {
    val appRoleService = client.auth.appRole

    runCatching { appRoleService.list() }
        .onSuccess { roles ->
            roles.forEach { role ->
                appRoleService.delete(role)
            }
        }
}

suspend fun revokeAllTokenData(client: VaultClient) {
    val tokenService = client.auth.token
    val rootAccessor = tokenService.lookupSelfToken().accessor

    // Revoke all tokens from accessor except root accessor
    tokenService.listAccessors()
        .asSequence()
        .filter {
            it != rootAccessor
        }
        .forEach {
            tokenService.revokeAccessorToken(it)
        }

    // Revoke all token roles
    runCatching { tokenService.listTokenRoles() }
        .onSuccess { roles ->
            roles.forEach { role ->
                tokenService.deleteTokenRole(role)
            }
        }
}

suspend fun revokeEntity(client: VaultClient) {
    val identityEntity = client.identity.entity

    runCatching { identityEntity.listEntitiesByID() }
        .onSuccess { response ->
            identityEntity.batchDeleteEntities(response.keys)
        }
}

suspend fun disableAllAudit(client: VaultClient) {
    val auditService = client.system.audit

    runCatching { auditService.list() }
        .onSuccess { audits ->
            audits.forEach { (key) ->
                auditService.disable(key)
            }
        }
}

suspend fun disableAllAuth(client: VaultClient) {
    val authService = client.system.auth

    runCatching { authService.list() }
        .onSuccess { auths ->
            auths
                .filterKeys { !it.contains("token") } // Cannot disable token auth
                .keys
                .forEach {
                    authService.disable(it)
                }
        }
}

suspend fun deleteAllNamespaces(client: VaultClient) {
    val namespacesService = client.system.namespaces

    runCatching { namespacesService.list() }
        .onSuccess { namespaces ->
            namespaces.keys.forEach {
                namespacesService.delete(it)
            }

            waitUntilVaultAsyncOpCompleted {
                val namespaces = runCatching { namespacesService.list() }
                    .getOrNull()
                    ?.keyInfo
                    // If the list() throws an exception, it means there are no namespaces, so we can consider the operation completed
                    ?: return@waitUntilVaultAsyncOpCompleted true

                // A tainted namespace means that the namespace is in the process of being deleted
                // If there are no tainted namespaces, it means that all namespaces have been deleted
                namespaces.asSequence().find { it.value.tainted } == null
            }
        }
}

suspend fun deleteAllPolicies(client: VaultClient) {
    val policyService = client.system.policy

    runCatching { policyService.list() }
        .onSuccess { policies ->
            policies
                .keys
                .filter { it != "default" && it != "root" }
                .forEach {
                    policyService.delete(it)
                }
        }
}

suspend fun deleteAllKV2Secrets(client: VaultClient) {
    val kv2 = client.secret.kv2

    val secrets = getAllSecrets(client)
    secrets.forEach { secret ->
        kv2.deleteMetadataAndAllVersions(secret)
    }
}

suspend fun getAllSecrets(client: VaultClient): Collection<String> = coroutineScope {
    val result = mutableSetOf<String>()
    val mutex = Mutex()
    val kv2 = client.secret.kv2

    suspend fun getSecretsRecursively(path: String) {
        val secrets = runCatching { kv2.listSecrets(path) }.getOrNull() ?: return

        secrets.map { secret ->
            this@coroutineScope.async {
                if (secret.endsWith('/')) {
                    getSecretsRecursively(path + secret)
                } else {
                    val secretCompletePath = path + secret
                    mutex.withLock {
                        result.add(secretCompletePath)
                    }
                }
            }
        }.awaitAll()
    }

    getSecretsRecursively("")
    return@coroutineScope result
}

/**
 * Some API calls in Vault are asynchronous, such as namespace deletion.
 * This function allows to wait until the operation launched asynchronously is completed by periodically checking the condition provided in [isCompleted].
 *
 * @param isCompleted `true` if the operation is completed, `false` otherwise.
 */
suspend inline fun waitUntilVaultAsyncOpCompleted(crossinline isCompleted: suspend () -> Boolean) {
    withTimeout(maxCheckVaultAsyncOpTimeout) {
        while (isActive) {
            if (isCompleted()) {
                break
            }
            delay(checkVaultAsyncOpInterval)
        }
    }
}

/**
 * Asserts that the provided [client] can create a secret in Vault.
 * That allows to verify that the client is authenticated.
 *
 * @param client Client to use for creating the secret.
 */
suspend fun assertCanCreateSecret(client: VaultClient) {
    shouldNotThrowAny {
        client.secret.kv2.createOrUpdateSecret("test") {
            data(mapOf("key" to "value"))
        }
    }
}

/**
 * Asserts that the provided [clients] have distinct tokens.
 *
 * @param clients List of clients to check that they have distinct tokens.
 */
fun assertDistinctTokens(clients: List<VaultClient>) {
    val tokens = clients.map { it.auth.getTokenString() }
    tokens.size shouldBe tokens.toSet().size
}
