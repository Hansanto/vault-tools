package io.github.hansanto.vaulttools.common

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.incrementAndFetch

class UnlimitedExecutorTest :
    ShouldSpec({

        should("execute should run the task and return its result") {
            val executor = UnlimitedExecutor
            val result = executor.execute { "Hello, World!" }
            result shouldBe "Hello, World!"
        }

        should("execute should handle exceptions thrown by the task") {
            class MyException(message: String) : Exception(message)

            val executor = UnlimitedExecutor
            val exception = MyException("Test exception")
            val catchException = shouldThrow<MyException> {
                executor.execute { throw exception }
            }
            catchException shouldBe exception
        }

        should("execute should run multiple tasks without concurrency limits") {
            val executor = UnlimitedExecutor
            val deferredWait = CompletableDeferred<Unit>()
            val allStarted = CompletableDeferred<Unit>()

            val listSize = 100
            val listStatus = ArrayList<TaskStatus>(listSize)
            repeat(listSize) { listStatus.add(TaskStatus.NOT_STARTED) }
            val countRunning = AtomicInt(0)

            val results = async {
                List(listSize) {
                    async {
                        executor.execute {
                            listStatus[it] = TaskStatus.RUNNING
                            if (countRunning.incrementAndFetch() == listSize) {
                                allStarted.complete(Unit)
                            }

                            deferredWait.await()
                            listStatus[it] = TaskStatus.COMPLETED
                        }
                    }
                }
            }

            allStarted.await()
            listStatus shouldContainExactly List(listSize) { TaskStatus.RUNNING }

            deferredWait.complete(Unit)
            results.await().awaitAll()
            listStatus shouldContainExactly List(listSize) { TaskStatus.COMPLETED }
        }
    }) {
    enum class TaskStatus { NOT_STARTED, RUNNING, COMPLETED }
}
