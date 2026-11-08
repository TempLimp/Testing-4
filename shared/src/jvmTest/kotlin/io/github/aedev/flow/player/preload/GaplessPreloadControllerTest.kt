package io.github.aedev.flow.player.preload

import io.github.aedev.flow.player.engine.FlowPlayer
import io.github.aedev.flow.player.engine.LazyMediaSource
import io.github.aedev.flow.testing.assertThat
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.player.stream.ResolvedStreamData
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import org.schabi.newpipe.extractor.stream.StreamType

/**
 * Preloading is what makes advancing to the next video gapless, and every one of its guards exists
 * to stop a second window being appended when the session has already moved on -- an appended item
 * for the wrong video plays the wrong video. The guards were unreachable from a test while this
 * lived inside `EnhancedPlayerManager`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GaplessPreloadControllerTest {
    private fun video(id: String) =
        Video(
            id = id,
            title = "Video $id",
            channelName = "Channel",
            channelId = "UC123",
            thumbnailUrl = "https://example.test/$id.jpg",
            duration = 120,
            viewCount = 10L,
            uploadDate = "today",
        )

    private fun resolved(
        id: String,
        streamType: StreamType? = StreamType.VIDEO_STREAM,
    ) = ResolvedStreamData(
        enrichedVideo = video(id),
        videoStream = null,
        audioStream = null,
        videoStreams = emptyList(),
        audioStreams = emptyList(),
        subtitles = emptyList(),
        durationSeconds = 120L,
        dashManifestUrl = null,
        streamType = streamType,
        relatedVideos = emptyList(),
        preferredCodec = "avc",
        itVideoFormats = emptyList(),
        itAudioFormats = emptyList(),
    )

    private fun playerWith(
        mediaItemCount: Int = 1,
        currentIndex: Int = 0,
    ): FlowPlayer =
        mockk(relaxed = true) {
            every { this@mockk.mediaItemCount } returns mediaItemCount
            every { currentMediaId } returns "current"
            every { currentMediaItemIndex } returns currentIndex
        }

    /** Wires a controller whose dependencies are all overridable per test. */
    private class Harness(
        val scope: CoroutineScope,
        val player: FlowPlayer? = null,
        var currentVideoId: String? = "current",
        var target: PreloadTarget? = null,
        var isLooping: Boolean = false,
        var isLive: Boolean = false,
        val resolve: suspend (Video) -> ResolvedStreamData? = { null },
        val source: LazyMediaSource? = mockk(relaxed = true),
        val downloaded: Set<String> = emptySet(),
        val gone: Set<String> = emptySet(),
    ) {
        val logs = mutableListOf<String>()
        var resolveCalls = 0

        val controller =
            GaplessPreloadController(
                scope = scope,
                player = { player },
                currentVideoId = { currentVideoId },
                nextTarget = { target },
                isLooping = { isLooping },
                isLiveStream = { isLive },
                resolveStreams = { v ->
                    resolveCalls++
                    resolve(v)
                },
                hasLocalCopy = { it.id in downloaded },
                buildMediaSource = { source },
                log = { logs += it },
                isGone = { it in gone },
            )
    }

    @Test
    fun aDownloadedNextVideoIsNotPreloadedSoAdvancePlaysTheFile() =
        runTest {
            val player = playerWith()
            val harness =
                Harness(
                    backgroundScope,
                    player = player,
                    target = PreloadTarget(video("next"), fromQueue = true),
                    resolve = { resolved("next") },
                    downloaded = setOf("next"),
                )

            harness.controller.schedule()
            runCurrent()

            verify(exactly = 0) { player.addMediaSource(any<LazyMediaSource>()) }
            assertThat(harness.resolveCalls).isEqualTo(0)
            assertThat(harness.controller.preloaded).isNull()
        }

    @Test
    fun preloadingAppendsTheResolvedNextVideoAsASecondWindow() =
        runTest {
            val player = playerWith()
            val harness =
                Harness(
                    backgroundScope,
                    player = player,
                    target = PreloadTarget(video("next"), fromQueue = true),
                    resolve = { resolved("next") },
                )

            harness.controller.schedule()
            runCurrent()

            verify { player.addMediaSource(any<LazyMediaSource>()) }
            assertThat(
                harness.controller.preloaded
                    ?.data
                    ?.enrichedVideo
                    ?.id,
            ).isEqualTo("next")
            assertThat(harness.controller.preloaded?.fromQueue).isTrue()
        }

    @Test
    fun loopingNeverPreloadsBecauseTheSameVideoRepeats() =
        runTest {
            val harness =
                Harness(
                    backgroundScope,
                    player = playerWith(),
                    target = PreloadTarget(video("next"), fromQueue = false),
                    isLooping = true,
                    resolve = { resolved("next") },
                )

            harness.controller.schedule()
            runCurrent()

            assertThat(harness.resolveCalls).isEqualTo(0)
            assertThat(harness.controller.preloaded).isNull()
        }

    @Test
    fun aLiveStreamIsNeverPreloaded() =
        runTest {
            val harness =
                Harness(
                    backgroundScope,
                    player = playerWith(),
                    target = PreloadTarget(video("next"), fromQueue = false),
                    isLive = true,
                    resolve = { resolved("next") },
                )

            harness.controller.schedule()
            runCurrent()

            assertThat(harness.resolveCalls).isEqualTo(0)
        }

    @Test
    fun aNextVideoThatResolvesAsLiveIsDroppedInsteadOfAppended() =
        runTest {
            val player = playerWith()
            val harness =
                Harness(
                    backgroundScope,
                    player = player,
                    target = PreloadTarget(video("next"), fromQueue = false),
                    resolve = { resolved("next", streamType = StreamType.LIVE_STREAM) },
                )

            harness.controller.schedule()
            runCurrent()

            verify(exactly = 0) { player.addMediaSource(any<LazyMediaSource>()) }
            assertThat(harness.controller.preloaded).isNull()
        }

    @Test
    fun aWindowIsNeverAppendedTwice() =
        runTest {
            val player = playerWith(mediaItemCount = 2)
            val harness =
                Harness(
                    backgroundScope,
                    player = player,
                    target = PreloadTarget(video("next"), fromQueue = false),
                    resolve = { resolved("next") },
                )

            harness.controller.schedule()
            runCurrent()

            assertThat(harness.resolveCalls).isEqualTo(0)
            verify(exactly = 0) { player.addMediaSource(any<LazyMediaSource>()) }
        }

    @Test
    fun nothingIsPreloadedWhileNoVideoIsPlaying() =
        runTest {
            val harness =
                Harness(
                    backgroundScope,
                    player = playerWith(),
                    currentVideoId = null,
                    target = PreloadTarget(video("next"), fromQueue = false),
                    resolve = { resolved("next") },
                )

            harness.controller.schedule()
            runCurrent()

            assertThat(harness.resolveCalls).isEqualTo(0)
        }

    @Test
    fun aFailedAttemptDoesNotBlockLaterOnesForTheSameVideo() =
        runTest {
            val harness =
                Harness(
                    backgroundScope,
                    player = playerWith(),
                    target = PreloadTarget(video("next"), fromQueue = false),
                    resolve = { null },
                )

            harness.controller.schedule()
            runCurrent()
            val afterFirst = harness.resolveCalls

            // A failure has to release the in-flight marker, or gapless stays off for the rest of
            // the session even once the network recovers.
            harness.controller.schedule()
            runCurrent()

            assertThat(afterFirst).isEqualTo(1)
            assertThat(harness.resolveCalls).isEqualTo(2)
        }

    @Test
    fun aVideoThatChangedWhileResolvingIsNotAppended() =
        runTest {
            val player = playerWith()
            val harness =
                Harness(
                    backgroundScope,
                    player = player,
                    target = PreloadTarget(video("next"), fromQueue = false),
                )
            val controller =
                GaplessPreloadController(
                    scope = backgroundScope,
                    player = { player },
                    currentVideoId = { harness.currentVideoId },
                    nextTarget = { harness.target },
                    isLooping = { false },
                    isLiveStream = { false },
                    resolveStreams = { _ ->
                        // The user skipped ahead while the network call was in flight.
                        harness.currentVideoId = "something-else"
                        resolved("next")
                    },
                    hasLocalCopy = { false },
                    buildMediaSource = { mockk(relaxed = true) },
                    log = {},
                )

            controller.schedule()
            runCurrent()

            verify(exactly = 0) { player.addMediaSource(any<LazyMediaSource>()) }
            assertThat(controller.preloaded).isNull()
        }

    @Test
    fun aFailedResolveIsRetriedAndEventuallyGivenUpOn() =
        runTest {
            val harness =
                Harness(
                    backgroundScope,
                    player = playerWith(),
                    target = PreloadTarget(video("next"), fromQueue = false),
                    resolve = { null },
                )

            harness.controller.schedule()
            runCurrent()
            assertThat(harness.resolveCalls).isEqualTo(1)

            // Three retries, then the attempt is abandoned rather than looping forever.
            repeat(4) {
                advanceTimeBy(11_000)
                runCurrent()
            }

            assertThat(harness.resolveCalls).isEqualTo(4)
            assertThat(harness.logs).contains("schedulePreloadRetry giving up next=next")
        }

    @Test
    fun aNextVideoYouTubeRemovedIsNotRetried() =
        runTest {
            val harness =
                Harness(
                    backgroundScope,
                    player = playerWith(),
                    target = PreloadTarget(video("next"), fromQueue = false),
                    resolve = { null },
                    gone = setOf("next"),
                )

            harness.controller.schedule()
            runCurrent()
            advanceTimeBy(40_000)
            runCurrent()

            assertThat(harness.resolveCalls).isEqualTo(1)
        }

    @Test
    fun reTargetingDropsTheWindowAppendedForTheOldNextVideo() =
        runTest {
            val player = playerWith(mediaItemCount = 1)
            val harness =
                Harness(
                    backgroundScope,
                    player = player,
                    target = PreloadTarget(video("first"), fromQueue = false),
                    resolve = { resolved(it.id) },
                )

            harness.controller.schedule()
            runCurrent()
            assertThat(
                harness.controller.preloaded
                    ?.data
                    ?.enrichedVideo
                    ?.id,
            ).isEqualTo("first")

            every { player.mediaItemCount } returns 2
            harness.target = PreloadTarget(video("second"), fromQueue = false)
            harness.controller.request("queue-change")
            runCurrent()

            // The appended window belongs to a video that is no longer next, so it has to go.
            verify { player.removeMediaItem(1) }
        }

    @Test
    fun consumingHandsOverThePreloadAndLeavesTheWindowPlaying() =
        runTest {
            val player = playerWith()
            val harness =
                Harness(
                    backgroundScope,
                    player = player,
                    target = PreloadTarget(video("next"), fromQueue = true),
                    resolve = { resolved("next") },
                )

            harness.controller.schedule()
            runCurrent()

            val consumed = harness.controller.consume()

            assertThat(consumed?.data?.enrichedVideo?.id).isEqualTo("next")
            assertThat(harness.controller.preloaded).isNull()
            assertThat(harness.controller.consume()).isNull()
            verify(exactly = 0) { player.removeMediaItem(any<Int>()) }
        }

    @Test
    fun clearingRemovesTheAppendedWindow() =
        runTest {
            val player = playerWith()
            val harness =
                Harness(
                    backgroundScope,
                    player = player,
                    target = PreloadTarget(video("next"), fromQueue = false),
                    resolve = { resolved("next") },
                )

            harness.controller.schedule()
            runCurrent()
            every { player.mediaItemCount } returns 2

            harness.controller.clear()

            assertThat(harness.controller.preloaded).isNull()
            verify { player.removeMediaItem(1) }
        }
}
