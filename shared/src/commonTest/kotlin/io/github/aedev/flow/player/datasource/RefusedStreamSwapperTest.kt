package io.github.aedev.flow.player.datasource

import io.github.aedev.flow.testing.assertThat
import io.github.aedev.flow.innertube.models.response.PlayerResponse
import io.github.aedev.flow.player.error.StreamDenialKind
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

class RefusedStreamSwapperTest {
    private val walled = "https://rr6.googlevideo.com/videoplayback?expire=9999999999&id=old&itag=140&c=VISIONOS&lmt=7&clen=100"
    private val fresh = "https://rr6.googlevideo.com/videoplayback?expire=9999999999&id=new&itag=140&c=TVHTML5&lmt=7&clen=100"
    // Upstream's DataSpec at byte 60; on iOS the proxy passes the position itself. runBlocking, not runTest: the
    // resolve runs on a real dispatcher under a real timeout, as on the proxy.
    private val spec = 60L

    private var kind = StreamDenialKind.ATTESTATION_GATED
    private val reported = mutableListOf<String>()
    private val resolved = mutableListOf<Pair<String, StreamDenialKind>>()
    private var replacement: List<PlayerResponse.StreamingData.Format>? = listOf(audio(fresh))

    private val swapper =
        RefusedStreamSwapper(
            table = StreamSwapTable(),
            reportDenied = { url -> kind.also { reported += url } },
            resolve = { videoId, kind -> replacement.also { resolved += videoId to kind } },
        )

    @Test
    fun aWalledStreamIsResolvedOnceAndTheReopenIsWorthIt(): Unit = runBlocking {
        swapper.register("wDQ0KNV3Tpc", listOf(walled))

        assertThat(swapper.onRefused(spec, walled)).isTrue()
        assertThat(swapper.onRefused(spec, walled)).isTrue()

        assertThat(resolved).containsExactly("wDQ0KNV3Tpc" to StreamDenialKind.ATTESTATION_GATED)
        assertThat(reported).containsExactly(walled)
    }

    @Test
    fun theKindOfRefusalReachesTheResolverSoAnExpiredUrlIsMintedAgain(): Unit = runBlocking {
        swapper.register("wDQ0KNV3Tpc", listOf(walled))
        kind = StreamDenialKind.URL_EXPIRED

        swapper.onRefused(spec, walled)

        assertThat(resolved.single().second).isEqualTo(StreamDenialKind.URL_EXPIRED)
    }

    @Test
    fun aRefusalWithNoReadableCauseIsLeftToThePlayer(): Unit = runBlocking {
        swapper.register("wDQ0KNV3Tpc", listOf(walled))
        kind = StreamDenialKind.UNKNOWN

        assertThat(swapper.onRefused(spec, walled)).isFalse()
        assertThat(resolved).isEmpty()
    }

    @Test
    fun aStreamNoVideoRegisteredIsLeftToThePlayerWithoutReportingIt(): Unit = runBlocking {
        assertThat(swapper.onRefused(spec, walled)).isFalse()
        assertThat(reported).isEmpty()
    }

    @Test
    fun aVideoThatHasUsedItsSwapsIsNotFetchedAgain(): Unit = runBlocking {
        val spent =
            RefusedStreamSwapper(
                table = StreamSwapTable(maxSwapsPerVideo = 0),
                reportDenied = { kind },
                resolve = { videoId, kind -> replacement.also { resolved += videoId to kind } },
            )
        spent.register("wDQ0KNV3Tpc", listOf(walled))

        assertThat(spent.onRefused(spec, walled)).isFalse()
        assertThat(resolved).isEmpty()
    }

    @Test
    fun noReplacementMeansThePlayerHandlesTheRefusal(): Unit = runBlocking {
        swapper.register("wDQ0KNV3Tpc", listOf(walled))
        replacement = null

        assertThat(swapper.onRefused(spec, walled)).isFalse()
    }

    private fun audio(url: String) =
        PlayerResponse.StreamingData.Format(
            itag = 140,
            url = url,
            mimeType = "audio/mp4; codecs=\"mp4a.40.2\"",
            bitrate = 128_000,
            width = null,
            height = null,
            contentLength = 100L,
            quality = "tiny",
            fps = null,
            qualityLabel = null,
            averageBitrate = null,
            audioQuality = null,
            approxDurationMs = null,
            audioSampleRate = null,
            audioChannels = null,
            loudnessDb = null,
            lastModified = 7L,
            signatureCipher = null,
        )
}
