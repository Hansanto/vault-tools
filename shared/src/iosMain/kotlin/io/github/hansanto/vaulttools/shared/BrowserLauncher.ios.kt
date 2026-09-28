package io.github.hansanto.vaulttools.shared

import platform.Foundation.NSURL
import platform.UIKit.UIApplication

actual suspend fun openBrowser(url: String): Boolean {
    val nsUrl = NSURL.URLWithString(url) ?: return false

    val sharedApplication = UIApplication.sharedApplication
    return sharedApplication.canOpenURL(nsUrl) && sharedApplication.openURL(nsUrl)
}
