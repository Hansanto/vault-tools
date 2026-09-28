package io.github.hansanto.vaulttools.shared.util

import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.collections.shouldNotBeIn
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldHaveLength

class GeneratorUtilTest :
    ShouldSpec({

        should("randomString throws exception when size is negative") {
            val exception = shouldThrowAny {
                randomString(size = -1)
            }
            exception shouldBe IllegalArgumentException("Size must be greater than 0")
        }

        should("randomString generates empty string when size is 0") {
            val result = randomString(size = 0)
            result shouldHaveLength 0
        }

        should("randomString generates string of specified size") {
            (1..100).forEach { size ->
                val result = randomString(size = size)
                result shouldHaveLength size
            }
        }

        should("randomString generates string with default allowed characters") {
            val result = randomString(size = 1000)
            val allowedChars = ('a'..'z') + ('A'..'Z') + ('0'..'9')
            result.forEach { it shouldBeIn allowedChars }
        }

        should("randomString generates random string on multiple calls") {
            val stringSaved = mutableSetOf<String>()
            repeat(10) {
                val result = randomString(size = 1000)
                result shouldNotBeIn stringSaved
                stringSaved.add(result)
            }
        }

        should("randomString generates string with allowed characters only") {
            val allowedChars = listOf(
                listOf('a'),
                listOf('a', 'b', 'c'),
                listOf('-', '_', '@'),
                ('a'..'z').toList(),
                ('A'..'Z').toList(),
                ('0'..'9').toList()
            )
            allowedChars.forEach { chars ->
                val result = randomString(size = 1000, allowedChar = chars)
                result.forEach { it shouldBeIn chars }
            }
        }
    })
