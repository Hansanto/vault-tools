package io.github.hansanto.vaulttools.commontest

import io.github.hansanto.kault.auth.common.common.TokenType
import io.github.hansanto.kault.auth.common.response.LoginResponse
import io.github.hansanto.vaulttools.shared.util.randomString
import kotlin.time.Duration.Companion.milliseconds

private const val DEFAULT_MIN_RANDOM_LONG = 0L
private const val DEFAULT_MAX_RANDOM_LONG = 100L
private const val DEFAULT_MIN_RANDOM_DURATION = 0L
private const val DEFAULT_MAX_RANDOM_DURATION = 10000L

fun randomBoolean(): Boolean = listOf(true, false).random()

fun randomLong(range: LongRange = DEFAULT_MIN_RANDOM_LONG..DEFAULT_MAX_RANDOM_LONG): Long = range.random()

fun randomDuration() = randomLong(DEFAULT_MIN_RANDOM_DURATION..DEFAULT_MAX_RANDOM_DURATION).milliseconds

fun randomLoginResponse(): LoginResponse = LoginResponse(
    clientToken = randomString(),
    accessor = randomString(),
    tokenPolicies = listOf(randomString(), randomString()),
    metadata = mapOf(
        "user" to randomString(),
        "role" to randomString()
    ),
    leaseDuration = randomDuration(),
    renewable = randomBoolean(),
    entityId = randomString(),
    tokenType = TokenType.entries.random(),
    orphan = randomBoolean(),
    numUses = randomLong(),
)
