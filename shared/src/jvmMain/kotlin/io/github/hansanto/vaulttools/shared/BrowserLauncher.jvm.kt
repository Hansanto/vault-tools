package io.github.hansanto.vaulttools.shared

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.net.URI

actual suspend fun openBrowser(url: String): Boolean {
    if (!Desktop.isDesktopSupported()) {
        return false
    }

    withContext(Dispatchers.IO) {
        Desktop.getDesktop().browse(URI(url))
    }
    return true
}
