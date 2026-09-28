package io.github.hansanto.vaulttools.commontest.listener

import io.github.hansanto.vaulttools.commontest.createVaultEnterpriseClient
import io.github.hansanto.vaulttools.commontest.deleteAllKV2Secrets
import io.github.hansanto.vaulttools.commontest.deleteAllNamespaces
import io.github.hansanto.vaulttools.commontest.deleteAllPolicies
import io.github.hansanto.vaulttools.commontest.disableAllAudit
import io.github.hansanto.vaulttools.commontest.disableAllAuth
import io.github.hansanto.vaulttools.commontest.revokeAllAppRoleData
import io.github.hansanto.vaulttools.commontest.revokeAllKubernetesData
import io.github.hansanto.vaulttools.commontest.revokeAllOIDCData
import io.github.hansanto.vaulttools.commontest.revokeAllTokenData
import io.github.hansanto.vaulttools.commontest.revokeAllUserpassData
import io.github.hansanto.vaulttools.commontest.revokeEntity
import io.kotest.core.listeners.AfterEachListener
import io.kotest.core.test.TestCase
import io.kotest.engine.test.TestResult

object VaultListener : AfterEachListener {

    override suspend fun afterEach(testCase: TestCase, result: TestResult) {
        createVaultEnterpriseClient().use { client ->
            revokeAllUserpassData(client)
            revokeAllKubernetesData(client)
            revokeAllOIDCData(client)
            revokeAllAppRoleData(client)
            revokeAllTokenData(client)
            revokeEntity(client)
            disableAllAudit(client)
            disableAllAuth(client)
            deleteAllNamespaces(client)
            deleteAllPolicies(client)
            deleteAllKV2Secrets(client)
        }
    }
}
