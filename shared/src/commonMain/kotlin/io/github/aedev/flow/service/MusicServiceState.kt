package io.github.aedev.flow.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Whether the music service is alive in this process, for instance after the system media controls
 * resumed it. Kept apart from the music service so reading it never loads the service class.
 * On iOS the music player (S12) sets [isRunning] while its session is up, and CarPlay (S21) counts its connections.
 */
internal object MusicServiceState {
    private val state = MutableStateFlow(false)

    /** For waiting until the service has stopped, as a profile switch does. */
    val running: StateFlow<Boolean> = state.asStateFlow()

    private val cars = MutableStateFlow(0)

    /** A car (Android Auto or Automotive; on iOS CarPlay) is connected to the music session. */
    val isCarConnected: Boolean get() = cars.value > 0

    fun carConnected() {
        cars.update { it + 1 }
    }

    fun carDisconnected() {
        cars.update { (it - 1).coerceAtLeast(0) }
    }

    var isRunning: Boolean
        get() = state.value
        set(value) {
            state.value = value
        }
}
