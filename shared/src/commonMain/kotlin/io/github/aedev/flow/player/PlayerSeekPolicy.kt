package io.github.aedev.flow.player

import io.github.aedev.flow.player.engine.FlowPlayer

internal fun isEndBoundarySeek(requestedPositionMs: Long, durationMs: Long): Boolean =
    durationMs > 0L &&
        durationMs != FlowPlayer.TIME_UNSET &&
        requestedPositionMs >= durationMs
