package io.github.aedev.flow.player

import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.model.SponsorBlockSegment
import io.github.aedev.flow.innertube.models.response.PlayerResponse
import io.github.aedev.flow.player.engine.EngineControl
import io.github.aedev.flow.player.engine.FlowPlayer
import io.github.aedev.flow.player.resolver.MediaSource
import io.github.aedev.flow.player.resolver.PlaybackEngineKind
import io.github.aedev.flow.player.stream.CaptionFormat
import io.github.aedev.flow.player.stream.InnerTubeStreamBridge
import io.github.aedev.flow.player.stream.ResolvedCaption
import io.github.aedev.flow.testing.assertEquals
import io.github.aedev.flow.testing.assertThat
import io.github.aedev.flow.testing.assertTrue
import io.github.aedev.flow.ui.screens.player.fakeInnerTubeFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test

/**
 * The manager ported over EnginePlayer, driven through its public calls with engines that only record what they are
 * told: which engine a source opens on, where it starts, how quality, speed, captions and SponsorBlock reach it.
 * Sources resolve through the real resolver and LocalHlsProxy (on loopback; nothing is fetched).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EnhancedPlayerManagerTest {
    private class RecordingEngine(
        val kind: PlaybackEngineKind,
    ) : EngineControl {
        var source: MediaSource? = null
        var state = FlowPlayer.STATE_IDLE
        var playing = false
        var wantsToPlay = false
        var position = 0L
        var rate = 1f
        var closed = false
        val seeks = mutableListOf<Long>()

        override fun prepare(source: MediaSource) {
            this.source = source
            state = FlowPlayer.STATE_BUFFERING
        }

        override fun play() {
            wantsToPlay = true
            playing = state == FlowPlayer.STATE_READY
        }

        override fun pause() {
            wantsToPlay = false
            playing = false
        }

        override fun seekTo(positionMs: Long) {
            seeks += positionMs
            position = positionMs
        }

        override fun currentPositionMs() = position

        override fun durationMs() = 120_000L

        override fun bufferedPositionMs() = position

        override fun isPlaying() = playing

        override fun playbackState() = state

        override fun error(): String? = null

        override fun errorCode() = 1000

        override fun setPlaybackRate(rate: Float) {
            this.rate = rate
        }

        override fun setVolume(volume: Float) {}

        override fun setSeekExact(exact: Boolean) {}

        override fun setVideoEnabled(enabled: Boolean) {}

        override fun setVideoLimits(
            maxWidth: Int,
            maxHeight: Int,
            maxBitrate: Long,
        ) {}

        override fun videoDecoderName() = "fake"

        override fun droppedFrameCount() = 0

        override fun hasRenderedFirstFrame() = state == FlowPlayer.STATE_READY

        override fun videoWidth() = 1280

        override fun videoHeight() = 720

        override fun close() {
            closed = true
        }

        fun becomeReady() {
            state = FlowPlayer.STATE_READY
            playing = wantsToPlay
        }
    }

    private val engines = mutableListOf<RecordingEngine>()
    private val manager = EnhancedPlayerManager.getInstance()

    /**
     * The manager's ticks run on the test dispatcher and never end by themselves, so each test releases the player
     * before runTest waits for the dispatcher to go idle.
     */
    private fun managerTest(body: suspend TestScope.() -> Unit) =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            EnhancedPlayerManager.engineFactory = { kind -> RecordingEngine(kind).also { engines += it } }
            manager.initialize()
            try {
                body()
            } finally {
                manager.release()
                runCurrent()
            }
        }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun format(
        itag: Int,
        height: Int,
    ) = fakeInnerTubeFormat(itag, "video/mp4; codecs=\"avc1.64001F\"", height = height, width = height * 16 / 9).copy(
        contentLength = 5_000_000,
        approxDurationMs = "120000",
        initRange = PlayerResponse.StreamingData.Format.Range("0", "740"),
        indexRange = PlayerResponse.StreamingData.Format.Range("741", "1200"),
        url = "https://rr1.googlevideo.com/videoplayback?itag=$itag&id=abc&lmt=1&clen=5000000",
    )

    private val audio =
        fakeInnerTubeFormat(140, "audio/mp4; codecs=\"mp4a.40.2\"", bitrate = 128_000).copy(
            contentLength = 2_000_000,
            approxDurationMs = "120000",
            initRange = PlayerResponse.StreamingData.Format.Range("0", "631"),
            indexRange = PlayerResponse.StreamingData.Format.Range("632", "1000"),
            url = "https://rr1.googlevideo.com/videoplayback?itag=140&id=abc&lmt=1&clen=2000000",
        )

    private val videoStreams = InnerTubeStreamBridge.convertVideoFormats(listOf(format(22, 720), format(18, 360)))
    private val audioStreams = InnerTubeStreamBridge.convertAudioFormats(listOf(audio))

    /** Waits for work on real threads (the proxy, DataStore) to land on the test dispatcher. */
    private fun TestScope.waitFor(condition: () -> Boolean) {
        repeat(250) {
            runCurrent()
            if (condition()) return
            Thread.sleep(20)
        }
    }

    private suspend fun TestScope.load(
        videoId: String = "vid",
        startPosition: Long = 0L,
        subtitles: List<ResolvedCaption> = emptyList(),
    ) {
        manager.setStreams(
            videoId = videoId,
            videoStream = videoStreams.first(),
            audioStream = audioStreams.first(),
            videoStreams = videoStreams,
            audioStreams = audioStreams,
            subtitles = subtitles,
            durationSeconds = 120,
            startPosition = startPosition,
        )
        runCurrent()
        advanceTimeBy(100)
    }

    @Test
    fun aVideoOpensOnTheEngineItsStreamsNeedWhereItWasLeft() =
        managerTest {
            load(startPosition = 30_000)

            val engine = engines.single()
            assertEquals(PlaybackEngineKind.AVPLAYER, engine.kind)
            assertTrue(engine.source!!.uri.startsWith("http://127.0.0.1:"))
            assertThat(engine.seeks).contains(30_000L)
            assertTrue(engine.wantsToPlay)
            engine.becomeReady()
            advanceTimeBy(300)
            assertTrue(manager.playerState.value.isPlaying)
        }

    @Test
    fun aQualityPickReopensTheStreamAtThePosition() =
        managerTest {
            load()
            engines.single().apply {
                becomeReady()
                position = 42_000
            }
            advanceTimeBy(300)

            manager.switchQualityByHeight(360)
            runCurrent()
            advanceTimeBy(100)

            assertEquals(2, engines.size)
            assertTrue(engines.first().closed)
            assertThat(engines.last().seeks).contains(42_000L)
            assertEquals(360, manager.playerState.value.currentQuality)
        }

    @Test
    fun theSpeedReachesTheEngineAndTheState() =
        managerTest {
            load()

            manager.setPlaybackSpeed(2f)

            assertEquals(2f, engines.single().rate)
            assertEquals(2f, manager.playerState.value.playbackSpeed)
        }

    @Test
    fun aPickedCaptionIsLoadedAndShownAtThePosition() =
        managerTest {
            val file = File.createTempFile("captions", ".vtt").apply { writeText("WEBVTT\n\n00:00:01.000 --> 00:00:05.000\nHello there\n") }
            val caption = ResolvedCaption("en", isAutoGenerated = false, url = file.toURI().toString().replace("file:/", "file:///"), format = CaptionFormat.VTT)
            load(subtitles = listOf(caption))
            engines.single().apply {
                becomeReady()
                position = 2_000
            }

            manager.selectSubtitle(manager.playerState.value.availableSubtitles.single().url)
            // The file is read on a worker thread.
            repeat(50) {
                if (manager.currentCues.value.isNotEmpty()) return@repeat
                Thread.sleep(20)
                advanceTimeBy(300)
            }

            assertThat(manager.currentCues.value.map { it.text.toString() }).containsExactly("Hello there")
            file.delete()
        }

    @Test
    fun aSponsorSegmentIsSkippedWhenPlaybackReachesIt() =
        managerTest {
            // The stored preference is what the manager follows; its first value arrives from DataStore's thread.
            val preferences = PlayerPreferences()
            preferences.setSponsorBlockEnabled(true)
            manager.toggleSponsorBlock(true)
            val file = File.createTempFile("video", ".mp4")
            manager.playLocalFile(
                videoId = "local-vid",
                filePath = file.absolutePath,
                savedSegments = listOf(SponsorBlockSegment("sponsor", listOf(10f, 20f), "uuid-1", "skip")),
            )
            waitFor { manager.sponsorSegments.value.isNotEmpty() && engines.isNotEmpty() }
            engines.single().apply {
                becomeReady()
                position = 12_000
            }
            advanceTimeBy(1_500)

            assertThat(engines.single().seeks).contains(20_000L)
            preferences.setSponsorBlockEnabled(false)
            file.delete()
        }

    @Test
    fun aDeviceFileInWebmOpensOnTheBundledEngineThroughTheProxy() =
        managerTest {
            val file = File.createTempFile("video", ".webm")
            manager.playLocalFile(videoId = "local-webm", filePath = file.absolutePath)
            runCurrent()
            advanceTimeBy(100)

            val engine = engines.single()
            assertEquals(PlaybackEngineKind.BUNDLED, engine.kind)
            assertTrue(engine.source!!.uri.contains("/f/"))
            assertEquals(engine.source!!.uri, engine.source!!.audioUri)
            file.delete()
        }
}
