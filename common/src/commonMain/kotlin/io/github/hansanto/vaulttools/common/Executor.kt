package io.github.hansanto.vaulttools.common

import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Concurrency executor interface to manage the execution of tasks with different concurrency strategies.
 */
interface Executor {
    /**
     * Executes a given task with the defined concurrency strategy.
     *
     * @param T The type of the result produced by the task.
     * @param task The suspend function representing the task to be executed.
     * @return The result of the executed task.
     */
    suspend fun <T> execute(task: suspend () -> T): T
}

/**
 * Unlimited concurrency executor that executes tasks without any concurrency limits.
 */
object UnlimitedExecutor : Executor {
    override suspend fun <T> execute(task: suspend () -> T): T = task()
}

/**
 * Limited concurrency executor using a semaphore to limit the number of concurrent tasks.
 *
 * @property semaphore Semaphore to control concurrency.
 */
class LimitedExecutor(private val semaphore: Semaphore) : Executor {
    override suspend fun <T> execute(task: suspend () -> T): T = semaphore.withPermit {
        task()
    }
}
