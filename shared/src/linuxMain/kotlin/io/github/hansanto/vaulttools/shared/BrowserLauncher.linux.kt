@file:OptIn(ExperimentalForeignApi::class)

package io.github.hansanto.vaulttools.shared

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.cstr
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.set
import kotlinx.cinterop.toKString
import platform.linux.posix_spawn
import platform.posix.pid_tVar
import platform.posix.strerror

actual suspend fun openBrowser(url: String): Boolean {
    memScoped {
        val pid = alloc<pid_tVar>()

        val result = posix_spawn(
            pid.ptr,
            "/usr/bin/xdg-open",
            null,
            null,
            allocArray<CPointerVar<ByteVar>>(3).apply {
                set(0, "xdg-open".cstr.ptr)
                set(1, url.cstr.ptr)
                set(2, null)
            },
            platform.posix.__environ
        )

        if (result != 0) {
            throw RuntimeException("Failed to spawn xdg-open: ${strerror(result)?.toKString()}")
        }
    }
    return true
}
