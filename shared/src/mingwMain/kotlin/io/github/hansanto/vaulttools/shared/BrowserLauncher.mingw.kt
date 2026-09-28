package io.github.hansanto.vaulttools.shared

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toLong
import platform.windows.SW_SHOWNORMAL
import platform.windows.ShellExecuteA

@OptIn(ExperimentalForeignApi::class)
actual suspend fun openBrowser(url: String): Boolean {
    val result = ShellExecuteA(
        null,
        "open",
        url,
        null,
        null,
        SW_SHOWNORMAL
    )
    return (result?.toLong() ?: 0) > 32
}
