package io.github.hansanto.vaulttools.common.extension

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.fetchAndIncrement
import kotlin.time.Duration.Companion.milliseconds

class AutoClosableExtTest :
    ShouldSpec({

        should("useAsync do nothing if the iterable is empty") {
            val list = emptyList<AutoCloseable>()

            shouldNotThrowAny {
                list.useAsync {
                    error("Should not be called")
                }
            }
        }

        should("useAsync wait to close element after deferred is completed") {
            var closed1 = false
            val list = listOf(
                AutoCloseable { closed1 = true },
            )

            val result = list.useAsync {
                delay(50.milliseconds)
                closed1 shouldBe false
                1
            }

            result.size shouldBe 1
            closed1 shouldBe true
        }

        should("useAsync close all elements in case of an exception in block execution") {
            class TestException : Exception()

            var closed1 = false
            var closed2 = false
            val list = listOf(
                AutoCloseable { closed1 = true },
                AutoCloseable { closed2 = true }
            )

            val exception = TestException()
            shouldThrow<TestException> {
                list.useAsync {
                    throw exception
                }
            } shouldBe exception

            closed1 shouldBe true
            closed2 shouldBe true
        }

        should("useAsync close all elements in case of an exception in deferred execution") {
            class TestException : Exception()

            var closed1 = false
            var closed2 = false
            val list = listOf(
                AutoCloseable { closed1 = true },
                AutoCloseable { closed2 = true }
            )

            val exception = TestException()
            shouldThrow<TestException> {
                list.useAsync {
                    delay(10.milliseconds)
                    throw exception
                }
            } shouldBe exception

            closed1 shouldBe true
            closed2 shouldBe true
        }

        should("useAsync return results from multiple deferred tasks") {
            var closed1 = false
            var closed2 = false
            val list = listOf(
                AutoCloseable { closed1 = true },
                AutoCloseable { closed2 = true }
            )

            var count = 0
            val result = list.useAsync {
                ++count
            }

            result shouldBe listOf(1, 2)
            closed1 shouldBe true
            closed2 shouldBe true
            count shouldBe 2
        }

        should("useAsync execute block in async context - verify concurrent execution") {
            val executionOrder = mutableListOf<AutoCloseable>()
            var closed1 = false
            var closed2 = false
            val list = listOf(
                AutoCloseable { closed1 = true },
                AutoCloseable { closed2 = true }
            )

            val mutex = Mutex()
            val count = AtomicInt(0)

            val result = list.useAsync {
                executionOrder.add(it)
                if (mutex.tryLock()) {
                    // The first task will acquire the lock and hold it
                    delay(100.milliseconds)
                    // the second task should have executed concurrently and added its index to the list
                    executionOrder shouldContainExactlyInAnyOrder list
                    mutex.unlock()
                }
                count.fetchAndIncrement()
            }

            result shouldContainExactlyInAnyOrder listOf(0, 1)
            closed1 shouldBe true
            closed2 shouldBe true
        }

        should("useAsync return results in the order of elements") {
            var closed1 = false
            var closed2 = false
            var closed3 = false
            val list = listOf(
                AutoCloseable { closed1 = true },
                AutoCloseable { closed2 = true },
                AutoCloseable { closed3 = true }
            )

            val count = AtomicInt(0)
            val result = list.useAsync {
                count.fetchAndIncrement() * 10
            }

            result shouldBe listOf(0, 10, 20)
            closed1 shouldBe true
            closed2 shouldBe true
            closed3 shouldBe true
        }

        should("useAsync throw cause element if only one exception is thrown during closing") {
            class TestException(message: String) : Exception(message)

            val exception1 = TestException("error 1")
            var closed1 = false
            var closed2 = false
            val list = listOf(
                AutoCloseable {
                    closed1 = true
                    throw exception1
                },
                AutoCloseable { closed2 = true }
            )

            val exception = shouldThrow<TestException> {
                list.useAsync {
                    1
                }
            }

            exception shouldBe exception1
            closed1 shouldBe true
            closed2 shouldBe true
        }

        should("useAsync close elements even if the block throws an exception") {
            class TestException(message: String) : Exception(message)

            var closed1 = false
            var closed2 = false
            val exception1 = TestException("error 1")
            val exception2 = TestException("error 2")
            val list = listOf(
                AutoCloseable {
                    closed1 = true
                    throw exception1
                },
                AutoCloseable {
                    closed2 = true
                    throw exception2
                }
            )

            val exception = shouldThrow<TestException> {
                list.useAsync {
                    1
                }
            }

            exception.message shouldBe "error 1"
            exception.suppressedExceptions shouldBe listOf(exception2)
            closed1 shouldBe true
            closed2 shouldBe true
        }

        should("useAsync close elements even if the block throws an exception and deferred throws an exception") {
            class TestException(message: String) : Exception(message)

            var closed1 = false
            var closed2 = false
            var closed3 = false
            val exception1 = TestException("error 1")
            val exception2 = TestException("error 2")
            val exception3 = TestException("error 3")
            val list = listOf(
                AutoCloseable {
                    closed1 = true
                    throw exception1
                },
                AutoCloseable {
                    closed2 = true
                    throw exception2
                },
                AutoCloseable {
                    closed3 = true
                    throw exception3
                }
            )

            val exceptionThrow = TestException("deferred error")
            val exception = shouldThrow<TestException> {
                list.useAsync {
                    throw exceptionThrow
                }
            }

            exception.message shouldBe "deferred error"
            exception.suppressedExceptions shouldBe listOf(exception1, exception2, exception3)
            exception1.suppressedExceptions shouldBe emptyList()
            exception2.suppressedExceptions shouldBe emptyList()
            exception3.suppressedExceptions shouldBe emptyList()
            closed1 shouldBe true
            closed2 shouldBe true
            closed3 shouldBe true
        }
        should("closeAll handle empty iterable") {
            val list = emptyList<AutoCloseable>()

            shouldNotThrowAny {
                list.closeAll()
            }
        }

        should("closeAll close all elements successfully without exceptions") {
            var closed1 = false
            var closed2 = false
            var closed3 = false
            val list = listOf(
                AutoCloseable { closed1 = true },
                AutoCloseable { closed2 = true },
                AutoCloseable { closed3 = true }
            )

            shouldNotThrowAny {
                list.closeAll()
            }

            closed1 shouldBe true
            closed2 shouldBe true
            closed3 shouldBe true
        }

        should("closeAll throw exception when a single element throws during close") {
            class TestException(message: String) : Exception(message)

            val exception1 = TestException("error 1")
            var closed1 = false
            var closed2 = false
            val list = listOf(
                AutoCloseable {
                    closed1 = true
                    throw exception1
                },
                AutoCloseable { closed2 = true }
            )

            val exception = shouldThrow<TestException> {
                list.closeAll()
            }

            exception shouldBe exception1
            exception.suppressedExceptions shouldBe emptyList()
            closed1 shouldBe true
            closed2 shouldBe true
        }

        should("closeAll suppress multiple exceptions during close") {
            class TestException(message: String) : Exception(message)

            val exception1 = TestException("error 1")
            val exception2 = TestException("error 2")
            val exception3 = TestException("error 3")
            var closed1 = false
            var closed2 = false
            var closed3 = false
            val list = listOf(
                AutoCloseable {
                    closed1 = true
                    throw exception1
                },
                AutoCloseable {
                    closed2 = true
                    throw exception2
                },
                AutoCloseable {
                    closed3 = true
                    throw exception3
                }
            )

            val exception = shouldThrow<TestException> {
                list.closeAll()
            }

            exception.message shouldBe "error 1"
            exception.suppressedExceptions shouldBe listOf(exception2, exception3)
            closed1 shouldBe true
            closed2 shouldBe true
            closed3 shouldBe true
        }

        should("closeAll throw cause exception when no close exceptions occur") {
            class TestException(message: String) : Exception(message)

            val causeException = TestException("cause")
            var closed1 = false
            var closed2 = false
            val list = listOf(
                AutoCloseable { closed1 = true },
                AutoCloseable { closed2 = true }
            )

            val exception = shouldThrow<TestException> {
                list.closeAll(causeException)
            }

            exception shouldBe causeException
            exception.suppressedExceptions shouldBe emptyList()
            closed1 shouldBe true
            closed2 shouldBe true
        }

        should("closeAll not throw if cause is null and no exceptions during close") {
            var closed1 = false
            var closed2 = false
            val list = listOf(
                AutoCloseable { closed1 = true },
                AutoCloseable { closed2 = true }
            )

            shouldNotThrowAny {
                list.closeAll(null)
            }

            closed1 shouldBe true
            closed2 shouldBe true
        }
    })
