package io.github.aedev.flow.player.stream

import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.incrementAndFetch
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import io.github.aedev.flow.testing.assertEquals
import kotlin.test.Test

class InFlightRequestCoalescerTest {
    @Test
    fun concurrentRequestsForTheSameKeyShareOneOperation() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val coalescer = InFlightRequestCoalescer<String, String>(scope)
            val requestCount = AtomicInt(0)
            val release = CompletableDeferred<Unit>()

            val first = async(start = CoroutineStart.UNDISPATCHED) {
                coalescer.run("video") {
                    requestCount.incrementAndFetch()
                    release.await()
                    "streams"
                }
            }
            val second = async(start = CoroutineStart.UNDISPATCHED) {
                coalescer.run("video") {
                    requestCount.incrementAndFetch()
                    "duplicate"
                }
            }

            release.complete(Unit)

            assertEquals("streams", first.await())
            assertEquals("streams", second.await())
            assertEquals(1, requestCount.load())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun cancellingOneWaiterDoesNotCancelTheSharedOperation() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val coalescer = InFlightRequestCoalescer<String, String>(scope)
            val started = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()

            val cancelledWaiter = async(start = CoroutineStart.UNDISPATCHED) {
                coalescer.run("video") {
                    started.complete(Unit)
                    release.await()
                    "streams"
                }
            }
            started.await()
            val survivingWaiter = async(start = CoroutineStart.UNDISPATCHED) {
                coalescer.run("video") { "duplicate" }
            }

            cancelledWaiter.cancel()
            release.complete(Unit)

            assertEquals("streams", survivingWaiter.await())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun cancellingTheLastWaiterCancelsUnobservedWork() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val coalescer = InFlightRequestCoalescer<String, String>(scope)
            val started = CompletableDeferred<Unit>()
            val cancelled = CompletableDeferred<Unit>()

            val onlyWaiter = async(start = CoroutineStart.UNDISPATCHED) {
                coalescer.run("video") {
                    try {
                        started.complete(Unit)
                        CompletableDeferred<Unit>().await()
                        "streams"
                    } finally {
                        cancelled.complete(Unit)
                    }
                }
            }

            started.await()
            onlyWaiter.cancelAndJoin()

            cancelled.await()
        } finally {
            scope.cancel()
        }
    }
}
