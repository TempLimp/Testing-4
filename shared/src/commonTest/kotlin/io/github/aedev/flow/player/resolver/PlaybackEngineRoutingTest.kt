package io.github.aedev.flow.player.resolver

import io.github.aedev.flow.player.quality.AdaptiveLadder
import io.github.aedev.flow.player.quality.AutoQualityPolicy
import io.github.aedev.flow.player.quality.LadderTestStreams.audio
import io.github.aedev.flow.player.quality.LadderTestStreams.av1
import io.github.aedev.flow.player.quality.LadderTestStreams.h264
import io.github.aedev.flow.player.quality.LadderTestStreams.vp9
import io.github.aedev.flow.player.resolver.PlaybackEngineKind.AVPLAYER
import io.github.aedev.flow.player.resolver.PlaybackEngineKind.BUNDLED
import io.github.aedev.flow.player.stream.AudioStreamSelector
import io.github.aedev.flow.player.stream.VideoCodecUtils
import kotlinx.coroutines.runBlocking
import org.schabi.newpipe.extractor.stream.VideoStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Which engine plays a stream follows upstream's own choice of stream (VideoCodecUtils' codec ranking, Auto's
 * initial pick and ladder): the routing only says which of iOS's two engines decodes what upstream chose.
 */
class PlaybackEngineRoutingTest {
    // The test video's ladder as YouTube offered it to the user's iPhone in the S04 gate: H.264 to 1080p, VP9 and AV1 to 2160p.
    private val offered: List<VideoStream> =
        listOf(144 to 80_000, 240 to 150_000, 360 to 300_000, 480 to 600_000, 720 to 1_200_000, 1080 to 2_500_000)
            .flatMap { (height, bitrate) -> listOf(h264(height, bitrate), vp9(height, bitrate * 3 / 4), av1(height, bitrate * 2 / 3)) } +
            listOf(vp9(1440, 6_000_000), av1(1440, 5_000_000), vp9(2160, 14_000_000), av1(2160, 11_000_000))

    private val aac = audio(140, "audio/mp4; codecs=\"mp4a.40.2\"", 130_000)
    private val opus = audio(251, "audio/webm; codecs=\"opus\"", 150_000)
    private val opusLow = audio(250, "audio/webm; codecs=\"opus\"", 70_000)

    @Test
    fun eachFormatGoesToTheEngineThatDecodesIt() {
        assertEquals(AVPLAYER, engineFor("video/mp4", "avc1.64002a", airPlay = false, hardwareAv1 = false))
        assertEquals(AVPLAYER, engineFor("video/mp4", "hvc1.2.4.L153.B0", airPlay = false, hardwareAv1 = false))
        assertEquals(AVPLAYER, engineFor("audio/mp4", "mp4a.40.2", airPlay = false, hardwareAv1 = false))
        assertEquals(AVPLAYER, engineFor("audio/mp4", "ec-3", airPlay = false, hardwareAv1 = false))
        assertEquals(BUNDLED, engineFor("video/webm", "vp9", airPlay = false, hardwareAv1 = false))
        assertEquals(BUNDLED, engineFor("video/webm", "vp09.02.51.10.01.09.16.09.00", airPlay = false, hardwareAv1 = false))
        assertEquals(BUNDLED, engineFor("audio/webm", "opus", airPlay = false, hardwareAv1 = false))
        // AV1 stays on AVPlayer where VideoToolbox decodes it in hardware (A17 Pro, M3), else dav1d decodes it.
        assertEquals(BUNDLED, engineFor("video/mp4", "av01.0.08M.08", airPlay = false, hardwareAv1 = false))
        assertEquals(AVPLAYER, engineFor("video/mp4", "av01.0.08M.08", airPlay = false, hardwareAv1 = true))
        // Built into neither: VP8 and Vorbis (itag 43's WebM), FLV.
        assertNull(engineFor("video/webm", "vp8.0", airPlay = false, hardwareAv1 = false))
        assertNull(engineFor("audio/webm", "vorbis", airPlay = false, hardwareAv1 = false))
        assertNull(engineFor("video/x-flv", "avc1.42001e", airPlay = false, hardwareAv1 = false))
    }

    @Test
    fun whileAirPlayIsOnOnlyAvPlayersFormatsPlay() {
        assertNull(engineFor("video/webm", "vp9", airPlay = true, hardwareAv1 = false))
        assertNull(engineFor("audio/webm", "opus", airPlay = true, hardwareAv1 = false))
        assertNull(engineFor("video/mp4", "av01.0.08M.08", airPlay = true, hardwareAv1 = false))
        assertEquals(AVPLAYER, engineFor("video/mp4", "avc1.64002a", airPlay = true, hardwareAv1 = false))
        assertEquals(AVPLAYER, engineFor("video/mp4", "av01.0.08M.08", airPlay = true, hardwareAv1 = true))
        // Upstream's Auto, given only what AVPlayer plays, starts on H.264.
        val viaAirPlay = offered.filter { engineFor(it.format?.mimeType, it.itagItem?.codec, airPlay = true, hardwareAv1 = false) != null }
        val pick = assertNotNull(AutoQualityPolicy.initialPick(viaAirPlay, 100_000_000, null))
        assertEquals("h264", VideoCodecUtils.codecKeyFromStream(pick))
        assertEquals(1080, VideoCodecUtils.qualityHeightFromStream(pick))
    }

    @Test
    fun upstreamsAutoOnAFastNetworkStartsAt2160pVp9OnTheBundledEngine() {
        val playable = offered.filter { PlatformCodecSupport.canPlay(it) }
        assertEquals(offered.size, playable.size)
        val pick = assertNotNull(AutoQualityPolicy.initialPick(playable, 100_000_000, null))
        // playbackCodecRank: h264, vp9, vp8, hevc, av1; at 2160p only VP9 and AV1 exist, and VP9 ranks first.
        assertEquals("2160 vp9", "${VideoCodecUtils.qualityHeightFromStream(pick)} ${VideoCodecUtils.codecKeyFromStream(pick)}")
        assertEquals(BUNDLED, PlatformCodecSupport.engineFor(pick))
        // The ladder keeps the anchor's codec, so every rung plays in the same engine.
        val ladder = AdaptiveLadder.rungs(pick, playable)
        assertTrue(ladder.size == 8 && ladder.all { PlatformCodecSupport.engineFor(it) == BUNDLED }, ladder.map { it.codec }.toString())
    }

    @Test
    fun upstreamsAutoWhere1080pFitsStartsOnH264InAvPlayer() {
        // 2.5 Mb/s fits a 3.6 Mb/s estimate's 70 % budget; 1440p's 5-6 Mb/s does not.
        val pick = assertNotNull(AutoQualityPolicy.initialPick(offered.filter { PlatformCodecSupport.canPlay(it) }, 3_600_000, null))
        assertEquals("1080 h264", "${VideoCodecUtils.qualityHeightFromStream(pick)} ${VideoCodecUtils.codecKeyFromStream(pick)}")
        assertEquals(AVPLAYER, PlatformCodecSupport.engineFor(pick))
    }

    @Test
    fun aPreferredCodecIsHonouredInWhicheverEngineDecodesIt() {
        val playable = offered.filter { PlatformCodecSupport.canPlay(it) }
        val av1Pick = assertNotNull(AutoQualityPolicy.initialPick(playable, 3_600_000, "av1"))
        assertEquals("1080 av1", "${VideoCodecUtils.qualityHeightFromStream(av1Pick)} ${VideoCodecUtils.codecKeyFromStream(av1Pick)}")
        // Without a hardware AV1 decoder, as on the user's iPhone (S04 gate) and the jvm target, dav1d decodes it.
        assertEquals(if (PlatformCodecSupport.av1) AVPLAYER else BUNDLED, PlatformCodecSupport.engineFor(av1Pick))
        val vp9Pick = assertNotNull(AutoQualityPolicy.initialPick(playable, 3_600_000, "vp9"))
        assertEquals("1080 vp9", "${VideoCodecUtils.qualityHeightFromStream(vp9Pick)} ${VideoCodecUtils.codecKeyFromStream(vp9Pick)}")
        assertEquals(BUNDLED, PlatformCodecSupport.engineFor(vp9Pick))
    }

    @Test
    fun audioBesideEachEngineIsUpstreamsChoiceAmongWhatThatEngineDecodes() {
        val streams = listOf(aac, opusLow, opus)
        // Upstream picks the highest bitrate: Opus 251 beside the bundled engine, AAC 140 in AVPlayer's HLS.
        val bundled = AudioStreamSelector.selectPreferredAudioStream(streams, "original", compatibilityFilter = { PlatformCodecSupport.canMergeAudio(it, BUNDLED) })
        assertEquals(251, bundled?.itag)
        val avPlayer = AudioStreamSelector.selectPreferredAudioStream(streams, "original", compatibilityFilter = { PlatformCodecSupport.canMergeAudio(it, AVPLAYER) })
        assertEquals(140, avPlayer?.itag)
        assertTrue(PlatformCodecSupport.canMergeAudio(aac, BUNDLED))
        assertTrue(!PlatformCodecSupport.canMergeAudio(opus, AVPLAYER))
    }

    @Test
    fun theResolverHandsTheBundledEnginesStreamsToItAsFiles() =
        runBlocking {
            val source = assertNotNull(VideoPlaybackResolver("v").resolve(listOf(vp9(2160, 14_000_000)), opus, null, null, 212))
            assertEquals(BUNDLED, source.engine)
            assertTrue(source.uri.startsWith("http://127.0.0.1:") && source.uri.endsWith("/0.webm"), source.uri)
            assertTrue(source.audioUri.orEmpty().endsWith("/1.webm"), "${source.audioUri}")
            assertEquals("video/webm", source.mimeType)
            assertEquals(212_000L, source.durationMs)

            // AAC beside VP9 plays in the bundled engine too, read from its MP4.
            val withAac = assertNotNull(VideoPlaybackResolver("v").resolve(listOf(vp9(1440, 6_000_000)), aac, null, null, 212))
            assertTrue(withAac.engine == BUNDLED && withAac.audioUri.orEmpty().endsWith("/1.mp4"), "$withAac")

            // A song in Opus (upstream's music choice prefers WebM) plays in the bundled engine, audio only.
            val song = assertNotNull(VideoPlaybackResolver("s").resolve(emptyList(), opus, null, null, 212))
            assertTrue(song.engine == BUNDLED && song.uri.endsWith("/0.webm") && song.audioUri == null, "$song")
            assertEquals("audio/webm", song.mimeType)
        }

    @Test
    fun theResolverKeepsAvPlayersStreamsInItsHlsAndPlaysABundledLadderAsOneStream() =
        runBlocking {
            val h264Source = assertNotNull(VideoPlaybackResolver("v").resolve(listOf(h264(1080, 2_500_000)), aac, null, null, 212))
            assertTrue(h264Source.engine == AVPLAYER && h264Source.uri.endsWith("/master.m3u8"), "$h264Source")
            // Opus cannot go beside H.264 in AVPlayer; the resolver leaves it out rather than hand AVPlayer what it cannot decode.
            val h264WithOpus = assertNotNull(VideoPlaybackResolver("v").resolve(listOf(h264(720, 1_200_000)), opus, null, null, 212))
            assertEquals(AVPLAYER, h264WithOpus.engine)

            val resolver = VideoPlaybackResolver("v")
            val ladder = listOf(vp9(480, 450_000), vp9(720, 900_000), vp9(1080, 1_875_000))
            val source = assertNotNull(resolver.resolve(ladder, opus, null, null, 212, asAdaptiveLadder = true))
            assertEquals(BUNDLED, source.engine)
            assertEquals(false, resolver.resolvedAdaptiveLadder)
            assertTrue(source.uri.endsWith("/0.webm") && source.audioUri.orEmpty().endsWith("/1.webm"), "$source")
        }
}
