package io.github.hansanto.vaulttools.common.parameter

import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import io.github.hansanto.vaulttools.common.Executor
import io.github.hansanto.vaulttools.common.LimitedExecutor
import io.github.hansanto.vaulttools.common.UnlimitedExecutor
import kotlinx.coroutines.sync.Semaphore

/**
 * Options related to performance.
 */
class PerformanceOptions :
    OptionGroup(
        name = "Performance Options"
    ) {

    val concurrency: Int? by option(
        "--concurrency",
        help = """
            |Number of simultaneous requests to perform.
            |(default: unlimited)
        """.trimMargin()
    ).int()

    /**
     * Get an [Executor] instance based on the specified concurrency level.
     * If concurrency is not specified, an [UnlimitedExecutor] is returned.
     * Otherwise, a [LimitedExecutor] with a [Semaphore] is returned to limit the number of concurrent requests.
     *
     * @return An [Executor] instance based on the specified concurrency level.
     */
    fun getExecutor(): Executor = concurrency?.let { LimitedExecutor(Semaphore(it)) } ?: UnlimitedExecutor
}
