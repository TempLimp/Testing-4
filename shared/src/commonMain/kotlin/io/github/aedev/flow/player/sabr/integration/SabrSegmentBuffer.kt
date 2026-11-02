package io.github.aedev.flow.player.sabr.integration

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.concurrent.atomics.AtomicBoolean

/** Upstream's blocking queue as a channel: [read] suspends where upstream's blocked its loader thread. */
@OptIn(ExperimentalCoroutinesApi::class)
class SabrSegmentBuffer {
    private val queue = Channel<ByteArray>(Channel.UNLIMITED)
    private var currentChunk: ByteArray? = null
    private var currentOffset = 0
    private val closed = AtomicBoolean(false)
    private val endOfStream = AtomicBoolean(false)

    fun appendSegment(data: ByteArray) {
        if (closed.load()) return
        if (data.isNotEmpty()) {
            queue.trySend(data)
        }
    }

    fun signalEndOfStream() {
        endOfStream.store(true)
        queue.trySend(ByteArray(0))
    }

    suspend fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (closed.load()) return -1
        if (length == 0) return 0

        var totalRead = 0
        while (totalRead < length) {
            if (currentChunk == null || currentOffset >= currentChunk!!.size) {
                val next = if (totalRead > 0) {
                    queue.tryReceive().getOrNull()
                } else {
                    var polled: ByteArray? = null
                    while (polled == null && !closed.load()) {
                        if (endOfStream.load() && queue.isEmpty) break
                        polled = withTimeoutOrNull(250) { queue.receive() }
                    }
                    polled
                }

                if (next == null) {
                    if (closed.load() || (endOfStream.load() && queue.isEmpty)) {
                        return if (totalRead > 0) totalRead else -1
                    }
                    continue
                }

                if (next.isEmpty()) {
                    return if (totalRead > 0) totalRead else -1
                }

                currentChunk = next
                currentOffset = 0
            }

            val chunk = currentChunk!!
            val available = chunk.size - currentOffset
            val toRead = minOf(available, length - totalRead)
            chunk.copyInto(buffer, offset + totalRead, currentOffset, currentOffset + toRead)
            currentOffset += toRead
            totalRead += toRead
        }
        return totalRead
    }

    fun close() {
        closed.store(true)
        clearQueue()
        currentChunk = null
        currentOffset = 0
    }

    fun reset() {
        clearQueue()
        currentChunk = null
        currentOffset = 0
        closed.store(false)
        endOfStream.store(false)
    }

    private fun clearQueue() {
        while (queue.tryReceive().isSuccess) {}
    }
}
