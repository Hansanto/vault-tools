package io.github.hansanto.vaulttools.common.extension

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Iterates over the elements and applies the given [block] to each element, expecting a [Deferred] result.
 * This function will wait for all deferred tasks to complete before closing the elements.
 * If any exception is thrown during execution or deferred completion, all elements will be closed and the exception will be thrown.
 * If no exception is thrown, all elements will be closed and the results of the completed deferreds will be returned as a list.
 *
 * @param T The type of the elements in the iterable, which must implement [AutoCloseable].
 * @param R The type of the result returned by the deferred tasks.
 * @param block Function to apply to each element that returns a [Deferred] task.
 * @receiver The iterable of [AutoCloseable] elements to which the [block] will be applied.
 * @return A list of results returned by the completed deferred tasks.
 */
suspend inline fun <T : AutoCloseable, R> Iterable<T>.useAsync(crossinline block: suspend (T) -> R): List<R> {
    val result = runCatching {
        coroutineScope {
            map {
                async {
                    block(it)
                }
            }.awaitAll()
        }
    }
    closeAll(result.exceptionOrNull())
    return result.getOrThrow()
}

/**
 * Closes all [AutoCloseable] elements in the iterable.
 * If the close is provoked by an exception, all exceptions thrown during the closing of the elements will be suppressed and added to the original exception, throwing the original exception at the end.
 * If no exception is thrown during the closing of the elements, any exception thrown during the closing will be thrown.
 */
fun Iterable<AutoCloseable>.closeAll(cause: Throwable? = null) {
    var exception: Throwable? = cause

    forEach {
        try {
            it.close()
        } catch (e: Throwable) {
            when (exception) {
                null -> exception = e
                else -> exception.addSuppressed(e)
            }
        }
    }

    if (exception != null) {
        throw exception
    }
}
