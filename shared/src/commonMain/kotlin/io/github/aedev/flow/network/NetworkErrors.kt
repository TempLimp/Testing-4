package io.github.aedev.flow.network

/** java.net.UnknownHostException's case: the host could not be looked up (no connection, or no such name). */
expect fun isUnknownHostError(error: Throwable): Boolean

/** java.net.SocketTimeoutException's case: a connection or read that took too long. */
expect fun isSocketTimeoutError(error: Throwable): Boolean

/** java.io.IOException's case: any failure of the transport. */
expect fun isIoError(error: Throwable): Boolean
