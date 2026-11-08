package io.github.aedev.flow.network

actual fun isUnknownHostError(error: Throwable): Boolean = error is java.net.UnknownHostException

actual fun isSocketTimeoutError(error: Throwable): Boolean =
    error is java.net.SocketTimeoutException || error is io.ktor.client.network.sockets.SocketTimeoutException

actual fun isIoError(error: Throwable): Boolean = error is java.io.IOException
