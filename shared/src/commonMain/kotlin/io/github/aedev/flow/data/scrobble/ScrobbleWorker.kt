package io.github.aedev.flow.data.scrobble

import io.github.aedev.flow.network.networkChanges
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Sends the queued listens once there is a connection, retrying with backoff while a service is down. Upstream
 * queues this as unique WorkManager work (APPEND_OR_REPLACE) with a network constraint and exponential backoff
 * from five minutes; here the same rules run in the app's process.
 */
// shortcut: a flush waiting for a connection or a retry runs only while iOS keeps the app in memory; the listens stay
// queued on disk and the next listen or launch flushes them. S18 adds a BGProcessingTask so iOS can run it later too.
object ScrobbleWorker {
    private const val BACKOFF_MINUTES = 5L

    // WorkManager's WorkRequest.MAX_BACKOFF_MILLIS.
    private const val MAX_BACKOFF_MS = 5 * 60 * 60 * 1000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = SynchronizedObject()
    private var running: Job? = null
    private var again = false

    fun enqueue(scrobbler: Scrobbler) {
        synchronized(lock) {
            // A run under way picks up what was queued since with one more pass, as appended work would.
            if (running?.isActive == true) {
                again = true
                return
            }
            again = false
            running = scope.launch { flushUntilDone(scrobbler) }
        }
    }

    private suspend fun flushUntilDone(scrobbler: Scrobbler) {
        var attempt = 0
        while (true) {
            networkChanges().first { it.online }
            if (scrobbler.flush()) {
                val more =
                    synchronized(lock) {
                        again.also { again = false }
                    }
                if (!more) return
                attempt = 0
            } else {
                delay(backoffMs(attempt++))
            }
        }
    }

    internal fun backoffMs(attempt: Int): Long = (BACKOFF_MINUTES * 60_000L shl attempt.coerceAtMost(20)).coerceAtMost(MAX_BACKOFF_MS)
}
