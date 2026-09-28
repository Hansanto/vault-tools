package io.github.hansanto.vaulttools.common

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore

class LimitedExecutorTest :
    ShouldSpec({

        should("execute should run the task and return its result") {
            val semaphore = Semaphore(1)
            val executor = LimitedExecutor(semaphore)
            val result = executor.execute {
                semaphore.availablePermits shouldBe 0
                "Hello, World!"
            }
            result shouldBe "Hello, World!"
            semaphore.availablePermits shouldBe 1
        }

        should("execute should handle exceptions thrown by the task") {
            class MyException(message: String) : Exception(message)

            val semaphore = Semaphore(1)
            val executor = LimitedExecutor(semaphore)

            val exception = MyException("Test exception")
            val catchException = shouldThrow<MyException> {
                executor.execute { throw exception }
            }
            catchException shouldBe exception
        }

        should("execute should run multiple tasks with concurrency limits") {
            val permits = 10
            val semaphore = Semaphore(permits)

            val executor = LimitedExecutor(semaphore)
            val deferredWait = CompletableDeferred<Unit>()
            val parallelTasksStarted = CompletableDeferred<Unit>()

            val waitingTasks = 10
            val listSize = permits + waitingTasks
            val listStatus = ArrayList<TaskStatus>(listSize)
            repeat(listSize) { listStatus.add(TaskStatus.NOT_STARTED) }

            val results = async {
                List(listSize) {
                    async {
                        executor.execute {
                            listStatus[it] = TaskStatus.RUNNING
                            if (semaphore.availablePermits == 0) {
                                parallelTasksStarted.complete(Unit)
                            }

                            deferredWait.await()
                            listStatus[it] = TaskStatus.COMPLETED
                        }
                    }
                }
            }

            parallelTasksStarted.await()
            semaphore.availablePermits shouldBe 0
            listStatus shouldContainExactlyInAnyOrder
                List(permits) { TaskStatus.RUNNING } + List(waitingTasks) { TaskStatus.NOT_STARTED }

            deferredWait.complete(Unit)
            results.await().awaitAll()
            semaphore.availablePermits shouldBe permits
            listStatus shouldContainExactlyInAnyOrder List(listSize) { TaskStatus.COMPLETED }
        }
    }) {

    enum class TaskStatus { NOT_STARTED, RUNNING, COMPLETED }
}
