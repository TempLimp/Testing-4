package io.github.aedev.flow.player.engine

import androidx.compose.ui.text.AnnotatedString
import io.github.aedev.flow.player.config.PlayerConfig
import io.github.aedev.flow.player.handlePlayButtonAction
import io.github.aedev.flow.player.shouldShowPlayButton
import io.github.aedev.flow.player.factory.LoadControlFactory
import io.github.aedev.flow.player.renderer.subtitle.Cue
import io.github.aedev.flow.player.renderer.subtitle.CuesWithTiming
import io.github.aedev.flow.player.renderer.subtitle.CuesWithTimingSubtitle
import io.github.aedev.flow.player.resolver.MediaSource
import io.github.aedev.flow.player.resolver.PlaybackEngineKind
import io.github.aedev.flow.testing.assertEquals
import io.github.aedev.flow.testing.assertThat
import io.github.aedev.flow.testing.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/** EnginePlayer gives the manager Media3's player behaviour over engines that only report what they are doing. */
@OptIn(ExperimentalCoroutinesApi::class)
class EnginePlayerTest {
    private class FakeEngine(
        val kind: PlaybackEngineKind,
    ) : EngineControl {
        var source: MediaSource? = null
        var state = FlowPlayer.STATE_IDLE
        var playing = false
        var position = 0L
        var durationMs = 120_000L
        var failure: String? = null
        var failureCode = PlaybackException.ERROR_CODE_UNSPECIFIED
        var appliedRate = 1f
        var appliedVolume = 1f
        var exact = false
        var firstFrame = false
        var width = 0
        var height = 0
        var closed = false
        val seeks = mutableListOf<Long>()

        override fun prepare(source: MediaSource) {
            this.source = source
            state = FlowPlayer.STATE_BUFFERING
        }

        override fun play() {
            playing = state == FlowPlayer.STATE_READY
            wantsToPlay = true
        }

        var wantsToPlay = false

        override fun pause() {
            playing = false
            wantsToPlay = false
        }

        /** An engine that takes a moment to reach a seek, as the bundled one waits out its feeds. */
        var landsLater = false

        override fun seekTo(positionMs: Long) {
            seeks += positionMs
            if (!landsLater) position = positionMs
            if (state == FlowPlayer.STATE_ENDED) state = FlowPlayer.STATE_READY
        }

        override fun currentPositionMs() = position

        override fun durationMs() = durationMs

        override fun bufferedPositionMs() = position + 10_000

        override fun isPlaying() = playing

        override fun playRequested() = wantsToPlay

        override fun playbackState() = state

        override fun error() = failure

        override fun errorCode() = failureCode

        override fun setPlaybackRate(rate: Float) {
            appliedRate = rate
        }

        override fun setVolume(volume: Float) {
            appliedVolume = volume
        }

        override fun setSeekExact(exact: Boolean) {
            this.exact = exact
        }

        var videoOn = true

        override fun setVideoEnabled(enabled: Boolean) {
            videoOn = enabled
        }

        var limits = Triple(0, 0, 0L)

        override fun setVideoLimits(
            maxWidth: Int,
            maxHeight: Int,
            maxBitrate: Long,
        ) {
            limits = Triple(maxWidth, maxHeight, maxBitrate)
        }

        override fun videoDecoderName() = "fake"

        override fun droppedFrameCount() = 0

        override fun hasRenderedFirstFrame() = firstFrame

        override fun videoWidth() = width

        override fun videoHeight() = height

        override fun close() {
            closed = true
        }

        fun becomeReady() {
            state = FlowPlayer.STATE_READY
            playing = wantsToPlay
            firstFrame = true
            width = 1920
            height = 1080
        }
    }

    private val engines = mutableListOf<FakeEngine>()
    private val events = mutableListOf<String>()

    private fun TestScope.player(): EnginePlayer =
        EnginePlayer(backgroundScope) { kind -> FakeEngine(kind).also { engines += it } }.also { player ->
            player.addListener(
                object : FlowPlayer.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        events += "state $playbackState"
                    }

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        events += "playing $isPlaying"
                    }

                    override fun onPlayWhenReadyChanged(
                        playWhenReady: Boolean,
                        reason: Int,
                    ) {
                        events += "playWhenReady $playWhenReady $reason"
                    }

                    override fun onMediaItemTransition(
                        mediaId: String?,
                        reason: Int,
                    ) {
                        events += "transition $mediaId $reason"
                    }

                    override fun onRenderedFirstFrame() {
                        events += "first frame"
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        events += "error ${error.errorCode}"
                    }

                    override fun onVideoSizeChanged(videoSize: VideoSize) {
                        events += "size ${videoSize.width}x${videoSize.height}"
                    }

                    override fun onCues(cues: List<Cue>) {
                        events += "cues ${cues.joinToString { it.text.toString() }}"
                    }
                },
            )
        }

    private fun source(
        id: String,
        kind: PlaybackEngineKind = PlaybackEngineKind.AVPLAYER,
        resolve: suspend () -> MediaSource = { MediaSource("http://127.0.0.1/p/$id/master.m3u8", "application/x-mpegURL", id, engine = kind) },
    ) = LazyMediaSource(id, resolve = resolve)

    @Test
    fun preparingResolvesTheSourceAndOpensTheEngineItsStreamsNeed() =
        runTest {
            val player = player()
            player.setMediaSource(source("a", PlaybackEngineKind.BUNDLED))
            player.seekTo(30_000)
            player.playWhenReady = true
            player.prepare()
            runCurrent()

            val engine = engines.single()
            assertEquals(PlaybackEngineKind.BUNDLED, engine.kind)
            assertThat(engine.seeks).containsExactly(30_000L)
            assertEquals(FlowPlayer.STATE_BUFFERING, player.playbackState)
            assertTrue(engine.wantsToPlay)

            engine.becomeReady()
            advanceTimeBy(300)
            assertEquals(FlowPlayer.STATE_READY, player.playbackState)
            assertTrue(player.isPlaying)
            assertThat(events).containsAtLeastElementsIn(listOf("state 2", "state 3", "playing true", "first frame", "size 1920x1080"))
            assertEquals(VideoSize(1920, 1080), player.videoSize)
        }

    @Test
    fun theLoadControlsMaxBufferReachesTheEngineWithTheSource() =
        runTest {
            val player = EnginePlayer(backgroundScope, LoadControlFactory.forMusic()) { kind -> FakeEngine(kind).also { engines += it } }
            player.setMediaSource(source("a"))
            player.prepare()
            runCurrent()

            assertEquals(PlayerConfig.MUSIC_MAX_BUFFER_MS, engines.single().source!!.forwardBufferMs)
        }

    @Test
    fun theSeekTargetIsThePositionUntilTheEngineGetsThere() =
        runTest {
            val player = player()
            player.setMediaSource(source("a"))
            player.prepare()
            runCurrent()
            val engine =
                engines.single().apply {
                    becomeReady()
                    position = 40_000
                    landsLater = true
                }

            player.seekTo(15_000)
            assertEquals(15_000L, player.currentPosition)

            engine.position = 14_980
            assertEquals(14_980L, player.currentPosition)
            engine.position = 40_500
            assertEquals(40_500L, player.currentPosition)
        }

    @Test
    fun aSourceThatCannotBeResolvedIsAPlayerErrorAndPreparingAgainRetriesIt() =
        runTest {
            var attempts = 0
            val player = player()
            player.setMediaSource(
                source("a") {
                    attempts++
                    if (attempts == 1) throw PlaybackException("sidx", null, PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS)
                    MediaSource("http://x", null, "a")
                },
            )
            player.prepare()
            runCurrent()

            assertEquals(FlowPlayer.STATE_IDLE, player.playbackState)
            assertThat(events).contains("error ${PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS}")
            assertThat(engines).isEmpty()

            player.prepare()
            runCurrent()
            assertEquals(1, engines.size)
            assertEquals(2, attempts)
        }

    @Test
    fun anEngineFailureKeepsThePositionForTheNextPrepare() =
        runTest {
            val player = player()
            player.setMediaSource(source("a"))
            player.prepare()
            runCurrent()
            val first = engines.single()
            first.becomeReady()
            first.position = 42_000
            first.failure = "decode vp9"
            first.failureCode = PlaybackException.ERROR_CODE_DECODING_FAILED
            advanceTimeBy(1_100)

            assertTrue(first.closed)
            assertEquals(FlowPlayer.STATE_IDLE, player.playbackState)
            assertEquals(42_000L, player.currentPosition)
            assertThat(events).contains("error ${PlaybackException.ERROR_CODE_DECODING_FAILED}")

            player.prepare()
            runCurrent()
            assertThat(engines.last().seeks).containsExactly(42_000L)
        }

    @Test
    fun theEndWithNothingAfterItIsMedia3sEndedState() =
        runTest {
            val player = player()
            player.setMediaSource(source("a"))
            player.playWhenReady = true
            player.prepare()
            runCurrent()
            val engine = engines.single()
            engine.becomeReady()
            advanceTimeBy(300)
            engine.state = FlowPlayer.STATE_ENDED
            engine.playing = false
            advanceTimeBy(300)

            assertEquals(FlowPlayer.STATE_ENDED, player.playbackState)
            assertTrue(player.playWhenReady)
            assertThat(events).contains("playing false")
        }

    @Test
    fun aPreparedNextItemTakesOverAtTheEndAsAnAutoTransition() =
        runTest {
            val player = player()
            player.setMediaSource(source("a"))
            player.playWhenReady = true
            player.prepare()
            runCurrent()
            engines.single().becomeReady()
            advanceTimeBy(300)

            player.addMediaSource(source("b", PlaybackEngineKind.BUNDLED))
            runCurrent()
            assertEquals(2, engines.size)
            val next = engines[1]
            assertTrue(!next.wantsToPlay)
            next.becomeReady()

            engines[0].state = FlowPlayer.STATE_ENDED
            advanceTimeBy(300)
            assertTrue(engines[0].closed)
            assertTrue(next.wantsToPlay)
            assertEquals("b", player.currentMediaId)
            // As Media3: the played window stays until removed, and the next one is current.
            assertEquals(2, player.mediaItemCount)
            assertEquals(1, player.currentMediaItemIndex)
            assertThat(events).contains("transition b ${FlowPlayer.MEDIA_ITEM_TRANSITION_REASON_AUTO}")
            assertThat(events).doesNotContain("state ${FlowPlayer.STATE_ENDED}")
            assertEquals(next, player.engine.value)

            player.removeMediaItem(0)
            assertEquals(1, player.mediaItemCount)
            assertEquals(0, player.currentMediaItemIndex)
            assertEquals("b", player.currentMediaId)
            assertEquals(next, player.engine.value)
        }

    @Test
    fun seekingToTheNextItemStartsItAsASeekTransition() =
        runTest {
            val player = player()
            player.setMediaSource(source("a"))
            player.playWhenReady = true
            player.prepare()
            runCurrent()
            engines.single().becomeReady()
            player.addMediaSource(source("b"))
            runCurrent()
            engines[1].becomeReady()

            player.seekToNextMediaItem()
            assertTrue(engines[0].closed)
            assertEquals(1, player.currentMediaItemIndex)
            assertTrue(engines[1].wantsToPlay)
            assertThat(events).contains("transition b ${FlowPlayer.MEDIA_ITEM_TRANSITION_REASON_SEEK}")
        }

    @Test
    fun aRefusalTheProxySawMakesAnIoFailureABadHttpStatus() =
        runTest {
            val player = player()
            val refusal = InvalidResponseCodeException(403, "https://rr1.googlevideo.com/videoplayback?itag=137")
            player.setMediaSource(
                LazyMediaSource("a", failureCause = { refusal }) { MediaSource("http://x", null, "a") },
            )
            var reported: PlaybackException? = null
            player.addListener(
                object : FlowPlayer.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        reported = error
                    }
                },
            )
            player.prepare()
            runCurrent()
            engines.single().failure = "read video (403)"
            engines.single().failureCode = PlaybackException.ERROR_CODE_IO_UNSPECIFIED
            advanceTimeBy(1_100)

            assertEquals(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, reported?.errorCode)
            assertEquals(refusal, reported?.cause)
        }

    @Test
    fun repeatOneStartsAgainWithoutEnding() =
        runTest {
            val player = player()
            player.repeatMode = FlowPlayer.REPEAT_MODE_ONE
            player.setMediaSource(source("a"))
            player.playWhenReady = true
            player.prepare()
            runCurrent()
            val engine = engines.single()
            engine.becomeReady()
            advanceTimeBy(300)
            engine.state = FlowPlayer.STATE_ENDED
            advanceTimeBy(300)

            assertThat(engine.seeks).contains(0L)
            assertThat(events).contains("transition a ${FlowPlayer.MEDIA_ITEM_TRANSITION_REASON_REPEAT}")
            assertThat(events).doesNotContain("state ${FlowPlayer.STATE_ENDED}")
        }

    @Test
    fun speedVolumeMuteAndSeekParametersReachEveryEngine() =
        runTest {
            val player = player()
            player.playbackSpeed = 1.5f
            player.volume = 0.8f
            player.setSeekParameters(exact = true)
            player.setMediaSource(source("a"))
            player.prepare()
            runCurrent()
            val engine = engines.single()
            assertEquals(1.5f, engine.appliedRate)
            assertEquals(0.8f, engine.appliedVolume)
            assertTrue(engine.exact)

            player.mute()
            assertEquals(0f, engine.appliedVolume)
            player.unmute()
            assertEquals(0.8f, engine.appliedVolume)
            player.playbackSpeed = 0.25f
            assertEquals(0.25f, engine.appliedRate)
        }

    @Test
    fun captionsFollowThePosition() =
        runTest {
            val player = player()
            player.setMediaSource(source("a"))
            player.playWhenReady = true
            player.prepare()
            runCurrent()
            val engine = engines.single()
            engine.becomeReady()
            player.setSubtitle(
                CuesWithTimingSubtitle(
                    listOf(
                        CuesWithTiming(listOf(Cue(AnnotatedString("one"))), 1_000_000, 1_000_000),
                        CuesWithTiming(listOf(Cue(AnnotatedString("two"))), 3_000_000, 1_000_000),
                    ),
                ),
            )
            engine.position = 1_500
            advanceTimeBy(300)
            assertThat(player.currentCues.map { it.text.toString() }).containsExactly("one")
            engine.position = 2_500
            advanceTimeBy(300)
            assertThat(player.currentCues).isEmpty()
            engine.position = 3_100
            advanceTimeBy(300)
            assertThat(events.filter { it.startsWith("cues") }).containsExactly("cues one", "cues ", "cues two")
        }

    @Test
    fun stopKeepsThePlaylistAndPositionAndClearingEmptiesIt() =
        runTest {
            val player = player()
            player.setMediaSource(source("a"))
            player.prepare()
            runCurrent()
            val engine = engines.single()
            engine.becomeReady()
            engine.position = 9_000
            advanceTimeBy(300)

            player.stop()
            assertTrue(engine.closed)
            assertEquals(FlowPlayer.STATE_IDLE, player.playbackState)
            assertEquals(1, player.mediaItemCount)
            assertEquals(9_000L, player.currentPosition)

            player.clearMediaItems()
            assertEquals(0, player.mediaItemCount)
            assertEquals(FlowPlayer.STATE_IDLE, player.playbackState)
            assertEquals(null, player.engine.value)
        }

    @Test
    fun aNewSourceWhileResolvingDropsTheOldResolve() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val player = player()
            player.setMediaSource(source("a") { gate.await(); MediaSource("http://a", null, "a") })
            player.prepare()
            runCurrent()
            player.setMediaSource(source("b"))
            runCurrent()
            gate.complete(Unit)
            runCurrent()

            assertEquals(1, engines.size)
            assertEquals("b", engines.single().source?.mediaId)
        }

    private fun TestScope.readyPlayer(): Pair<EnginePlayer, FakeEngine> {
        val player = player()
        player.setMediaSource(source("a"))
        player.playWhenReady = true
        player.prepare()
        runCurrent()
        val engine = engines.single().apply { becomeReady() }
        advanceTimeBy(300)
        return player to engine
    }

    @Test
    fun aSuppressionStopsTheEngineAndKeepsTheWishToPlay() =
        runTest {
            val (player, engine) = readyPlayer()

            player.setPlaybackSuppressed(true)
            advanceTimeBy(300)
            assertThat(engine.wantsToPlay).isFalse()
            assertThat(player.playWhenReady).isTrue()
            assertThat(player.isPlaying).isFalse()

            player.setPlaybackSuppressed(false)
            assertThat(engine.wantsToPlay).isTrue()
        }

    @Test
    fun playingWhileSuppressedWaitsForTheSuppressionToEnd() =
        runTest {
            val (player, engine) = readyPlayer()
            player.pause()
            player.setPlaybackSuppressed(true)

            player.play()
            assertThat(engine.wantsToPlay).isFalse()
            player.setPlaybackSuppressed(false)
            assertThat(engine.wantsToPlay).isTrue()
        }

    @Test
    fun aPauseForAnAudioFocusLossCarriesItsReason() =
        runTest {
            val (player, engine) = readyPlayer()

            player.setPlayWhenReady(false, FlowPlayer.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY)

            assertThat(engine.wantsToPlay).isFalse()
            assertThat(events).contains("playWhenReady false ${FlowPlayer.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY}")
        }

    @Test
    fun aPauseMadeOnTheEngineBecomesThePlayersOnlyWhileFollowed() =
        runTest {
            val (player, engine) = readyPlayer()

            engine.pause()
            advanceTimeBy(300)
            assertThat(player.playWhenReady).isTrue()

            player.followsEngineControls = true
            advanceTimeBy(1_100)
            assertThat(player.playWhenReady).isFalse()
            assertThat(events).contains("playWhenReady false ${FlowPlayer.PLAY_WHEN_READY_CHANGE_REASON_REMOTE}")

            engine.play()
            advanceTimeBy(1_100)
            assertThat(player.playWhenReady).isTrue()
            assertThat(events).contains("playWhenReady true ${FlowPlayer.PLAY_WHEN_READY_CHANGE_REASON_REMOTE}")
        }

    @Test
    fun aSuppressedEngineIsNotTakenForAPause() =
        runTest {
            val (player, _) = readyPlayer()
            player.followsEngineControls = true

            player.setPlaybackSuppressed(true)
            advanceTimeBy(300)

            assertThat(player.playWhenReady).isTrue()
        }

    @Test
    fun theSessionsPlayButtonStartsAnEndedVideoOverAndPreparesAnIdleOne() =
        runTest {
            val (player, engine) = readyPlayer()
            player.pause()
            engine.state = FlowPlayer.STATE_ENDED
            advanceTimeBy(1_100)
            assertThat(player.shouldShowPlayButton(isSuppressed = false)).isTrue()

            player.handlePlayButtonAction()
            assertThat(engine.seeks).contains(0L)
            assertTrue(engine.wantsToPlay)

            player.stop()
            player.handlePlayButtonAction()
            runCurrent()
            assertEquals(2, engines.size)
            assertTrue(engines.last().wantsToPlay)
        }

    @Test
    fun aPlayAskedForWhileSuppressedAsksForTheAudioBack() =
        runTest {
            val (player, engine) = readyPlayer()
            player.onPlayWhileSuppressed = { player.setPlaybackSuppressed(false) }
            player.setPlaybackSuppressed(true)

            player.play()

            assertThat(player.isPlaybackSuppressed).isFalse()
            assertTrue(engine.wantsToPlay)
        }

    @Test
    fun aPlayAskedForAgainRestartsAnEngineTheSystemPaused() =
        runTest {
            val (player, engine) = readyPlayer()
            val changes = events.count { it.startsWith("playWhenReady") }
            engine.pause()

            player.play()

            assertTrue(engine.wantsToPlay)
            // The player's own wish did not change, so its listeners hear nothing.
            assertThat(events.count { it.startsWith("playWhenReady") }).isEqualTo(changes)
        }
}
