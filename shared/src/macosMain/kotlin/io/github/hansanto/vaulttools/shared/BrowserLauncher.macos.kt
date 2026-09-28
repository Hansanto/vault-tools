package io.github.hansanto.vaulttools.shared

import platform.AppKit.NSWorkspace
import platform.Foundation.NSURL

actual suspend fun openBrowser(url: String): Boolean {
    val nsUrl = NSURL.URLWithString(url) ?: return false
    NSWorkspace.sharedWorkspace.openURL(nsUrl)
    return true
}
