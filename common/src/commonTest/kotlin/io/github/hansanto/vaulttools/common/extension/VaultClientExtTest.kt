package io.github.hansanto.vaulttools.common.extension

import io.github.hansanto.kault.VaultClient
import io.github.hansanto.vaulttools.common.exception.VaultUnreachableException
import io.github.hansanto.vaulttools.commontest.createVaultEnterpriseClient
import io.github.hansanto.vaulttools.commontest.listener.VaultListener
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe

class VaultClientExtTest :
    ShouldSpec({

        extensions(VaultListener)

        lateinit var rootClient: VaultClient

        beforeSpec {
            rootClient = createVaultEnterpriseClient()
        }

        afterSpec {
            rootClient.close()
        }

        should("checkReachability Vault server should succeed") {
            shouldNotThrowAny {
                checkReachability(rootClient)
            }
        }

        should("checkReachability Vault server should throw VaultUnreachableException for invalid URL") {
            VaultClient {
                url = "http://invalid-url"
            }.use { invalidClient ->
                val exception = shouldThrow<VaultUnreachableException> {
                    checkReachability(invalidClient)
                }

                exception.message shouldBe """
                    |Could not reach Vault server.
                    |Please check the URL or your network connection.
                """.trimMargin()
            }
        }
    })
