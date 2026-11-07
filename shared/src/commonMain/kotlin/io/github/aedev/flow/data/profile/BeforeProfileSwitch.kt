package io.github.aedev.flow.data.profile

import kotlin.concurrent.atomics.AtomicReference

/**
 * Work an open screen must finish before a switch restarts the process, such as grading the watch session of the
 * video on screen. The process dies with the screen still open, so nothing else would run it.
 */
object BeforeProfileSwitch {
    // java.util.concurrent's CopyOnWriteArraySet: each change swaps in a new set, a run iterates the one it read.
    private val actions = AtomicReference<Set<() -> Unit>>(emptySet())

    /** Registers [action] and returns what unregisters it, for the owner's teardown. */
    fun register(action: () -> Unit): () -> Unit {
        update { it + action }
        return { update { it - action } }
    }

    internal fun run() = actions.load().forEach { it() }

    private fun update(change: (Set<() -> Unit>) -> Set<() -> Unit>) {
        while (true) {
            val current = actions.load()
            if (actions.compareAndSet(current, change(current))) return
        }
    }
}
