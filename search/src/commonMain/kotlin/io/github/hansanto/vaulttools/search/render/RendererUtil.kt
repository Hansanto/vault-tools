package io.github.hansanto.vaulttools.search.render

object RendererUtil {

    /**
     * Regex to remove useless whitespace from error messages.
     */
    private val REGEX_SPACE = Regex("\\s+")

    /**
     * Message to indicate an unknown error occurred.
     * Sometimes, the Vault API can return errors without any message, in that case we use this default message.
     */
    const val MESSAGE_UNKNOWN_ERROR = "Unknown error"

    /**
     * Extract a user-friendly error message from a Throwable.
     *
     * @param throwable The Throwable to extract the message from.
     * @return Extracted error message or a default unknown error message.
     */
    fun getErrorMessage(throwable: Throwable): String {
        val message = throwable.message
        return if (message.isNullOrBlank()) MESSAGE_UNKNOWN_ERROR else message.trim().replace(REGEX_SPACE, " ")
    }
}
