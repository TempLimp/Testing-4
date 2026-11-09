package io.github.aedev.flow.player

/** What the video notification's repeat and shuffle buttons show and do. */
internal object VideoSessionControls {
    enum class Repeat { OFF, QUEUE, ONE }

    data class State(
        val videoLooping: Boolean,
        val queueLooping: Boolean,
        val queueShuffled: Boolean,
        val queueSize: Int,
    ) {
        val hasQueue: Boolean get() = queueSize > 1

        val repeat: Repeat
            get() =
                when {
                    videoLooping -> Repeat.ONE
                    queueLooping && hasQueue -> Repeat.QUEUE
                    else -> Repeat.OFF
                }
    }

    data class LoopTarget(
        val videoLoop: Boolean,
        val queueLoop: Boolean,
    )

    /** Off, then the whole queue, then this video; a single video goes straight to looping itself. */
    fun nextLoop(state: State): LoopTarget =
        when (state.repeat) {
            Repeat.OFF -> {
                if (state.hasQueue) {
                    LoopTarget(
                        videoLoop = false,
                        queueLoop = true,
                    )
                } else {
                    LoopTarget(videoLoop = true, queueLoop = false)
                }
            }

            Repeat.QUEUE -> {
                LoopTarget(videoLoop = true, queueLoop = false)
            }

            Repeat.ONE -> {
                LoopTarget(videoLoop = false, queueLoop = false)
            }
        }

    /**
     * The loop a control asking for [requested] gets: iOS's repeat command names the mode it wants where upstream's
     * button steps through them, and a single video loops itself for the whole queue, as [nextLoop] has it.
     */
    fun loopFor(
        requested: Repeat,
        state: State,
    ): LoopTarget =
        when (requested) {
            Repeat.OFF -> LoopTarget(videoLoop = false, queueLoop = false)
            Repeat.ONE -> LoopTarget(videoLoop = true, queueLoop = false)
            Repeat.QUEUE -> if (state.hasQueue) LoopTarget(videoLoop = false, queueLoop = true) else LoopTarget(videoLoop = true, queueLoop = false)
        }
}

/**
 * Repeat and shuffle on the video session. Upstream adds them as Media3 session commands, for the media card and older
 * notifications, because the queue lives in the app: the player only ever holds the current video. On iOS they are the
 * remote command center's repeat and shuffle commands, which iOS hands to Siri, CarPlay and accessories (the lock screen
 * shows neither); each names the mode it wants.
 */
internal class VideoSessionButtons(
    private val currentState: () -> VideoSessionControls.State,
    private val onLoop: (VideoSessionControls.LoopTarget) -> Unit,
    private val onShuffle: (Boolean) -> Unit,
) {
    enum class Button { REPEAT, SHUFFLE }

    /** Upstream's onCustomCommand for REPEAT: null steps on as its button does. */
    fun repeat(requested: VideoSessionControls.Repeat?) {
        val state = currentState()
        onLoop(requested?.let { VideoSessionControls.loopFor(it, state) } ?: VideoSessionControls.nextLoop(state))
    }

    /** Upstream's onCustomCommand for SHUFFLE: only a queue shuffles. Null toggles, as its button does. */
    fun shuffle(enabled: Boolean?) {
        val state = currentState()
        if (state.hasQueue) onShuffle(enabled ?: !state.queueShuffled)
    }

    /** Upstream's media button preferences: repeat always, shuffle only with a queue. */
    fun buttons(state: VideoSessionControls.State): List<Button> = if (state.hasQueue) listOf(Button.REPEAT, Button.SHUFFLE) else listOf(Button.REPEAT)
}
