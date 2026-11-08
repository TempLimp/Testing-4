package io.github.aedev.flow.network

import io.ktor.client.engine.darwin.DarwinHttpRequestException
import platform.Foundation.NSURLErrorCannotFindHost
import platform.Foundation.NSURLErrorDNSLookupFailed
import platform.Foundation.NSURLErrorDomain
import platform.Foundation.NSURLErrorNotConnectedToInternet
import platform.Foundation.NSURLErrorTimedOut

// NSURLSession reports through Ktor's Darwin engine what java.net's exceptions report on Android.

actual fun isUnknownHostError(error: Throwable): Boolean =
    (error as? DarwinHttpRequestException)?.origin?.takeIf { it.domain == NSURLErrorDomain }?.code in
        setOf(NSURLErrorCannotFindHost, NSURLErrorDNSLookupFailed, NSURLErrorNotConnectedToInternet)

actual fun isSocketTimeoutError(error: Throwable): Boolean =
    error is io.ktor.client.network.sockets.SocketTimeoutException ||
        (error as? DarwinHttpRequestException)?.origin?.let { it.domain == NSURLErrorDomain && it.code == NSURLErrorTimedOut } == true

actual fun isIoError(error: Throwable): Boolean = error is kotlinx.io.IOException
