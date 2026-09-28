package io.github.hansanto.vaulttools.shared.util

/**
 * Generates a random string of specified size using the allowed characters.
 *
 * @param size Size of the generated string.
 * @param allowedChar List of characters to use for generating the string.
 * @return A random string.
 */
fun randomString(size: Int = 50, allowedChar: List<Char> = ('a'..'z') + ('A'..'Z') + ('0'..'9')): String {
    if (size == 0) return ""
    require(size > 0) { "Size must be greater than 0" }

    return buildString {
        repeat(size) {
            append(allowedChar.random())
        }
    }
}
