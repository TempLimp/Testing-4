package io.github.aedev.flow.player

import io.github.aedev.flow.innertube.YouTube
import io.github.aedev.flow.innertube.blockReason
import io.github.aedev.flow.innertube.models.SongItem
import io.github.aedev.flow.network.platformHttpClient
import io.github.aedev.flow.player.error.PlayerDiagnostics
import io.github.aedev.flow.player.quality.AdaptiveLadder
import io.github.aedev.flow.player.resolver.PlatformCodecSupport
import io.github.aedev.flow.player.resolver.PlaybackEngineKind
import io.github.aedev.flow.player.resolver.VideoPlaybackResolver
import io.github.aedev.flow.player.resolver.canMergeAudio
import io.github.aedev.flow.player.resolver.engineFor
import io.github.aedev.flow.player.stream.AudioStreamSelector
import io.github.aedev.flow.player.stream.InnerTubeStreamBridge
import io.github.aedev.flow.player.stream.InnerTubeVideoStreamExtractor
import io.github.aedev.flow.player.stream.PlaybackBlockedException
import io.github.aedev.flow.player.stream.VideoCodecUtils
import io.github.aedev.flow.utils.MusicPlayerUtils
import io.github.aedev.flow.utils.cipher.CipherDeobfuscator
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readRawBytes
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration.Companion.seconds

/**
 * Everything the playback gate depends on short of AVPlayer, against live YouTube on every target: the
 * client ladder, the stream bridge, the HLS the loopback proxy serves (master, media playlist from the
 * sidx, and a segment's bytes, which must be fMP4), for a video and for a YouTube Music song, and the
 * player script's cipher in the platform's JavaScript engine. The app's gate (iosApp) plays the same
 * chain in AVPlayer.
 *
 * As in LiveInnerTubeSmokeTest, a runner YouTube turns away prints `LIVE PLAYBACK: BLOCKED` and passes.
 */
class LivePlaybackChainTest {
    private val http = platformHttpClient()

    @Test
    fun aVideoBecomesHlsAVPlayerCanOpen() =
        live("video") {
            val result =
                InnerTubeVideoStreamExtractor.extract(VIDEO_ID) ?: run {
                    val block =
                        InnerTubeVideoStreamExtractor.blockOf(VIDEO_ID)
                            ?: fail("no client resolved $VIDEO_ID and none reported a block:\n${diagnostics()}")
                    return@live blocked("video", "every client refused $VIDEO_ID ($block): ${clientRefusals()}")
                }
            val videos =
                InnerTubeStreamBridge.convertVideoFormats(result.videoFormats).filter { PlatformCodecSupport.engineFor(it) == PlaybackEngineKind.AVPLAYER }
            val audio =
                AudioStreamSelector.selectPreferredAudioStream(
                    InnerTubeStreamBridge.convertAudioFormats(result.audioFormats),
                    preferredAudioLanguage = "original",
                    compatibilityFilter = { PlatformCodecSupport.canMergeAudio(it) },
                )
            assertTrue(videos.isNotEmpty(), "no playable video among ${result.videoFormats.map { it.mimeType }}")
            assertNotNull(audio, "no audio among ${result.audioFormats.map { it.mimeType }}")
            val anchor = videos.maxBy { VideoCodecUtils.qualityHeightFromStream(it) }
            val ladder = AdaptiveLadder.rungs(anchor, videos)
            val resolver = VideoPlaybackResolver(VIDEO_ID)
            val source =
                resolver.resolve(
                    videoStreams = ladder.ifEmpty { listOf(anchor) },
                    audioStream = audio,
                    dashManifestUrl = null,
                    hlsUrl = null,
                    durationSeconds = result.playerResponse.videoDetails?.lengthSeconds?.toLongOrNull() ?: 0,
                    asAdaptiveLadder = ladder.isNotEmpty(),
                )
            assertNotNull(source, "nothing resolved")
            val master = http.get(source.uri).bodyAsText()
            assertTrue("#EXT-X-STREAM-INF" in master && "#EXT-X-MEDIA:TYPE=AUDIO" in master, master)
            checkMediaPlaylist(source.uri, master.lines().first { it.endsWith(".m3u8") && !it.startsWith("#") })
            println(
                "LIVE PLAYBACK: video OK via ${result.usedClient.clientName}: ${ladder.size} rungs, " +
                    "top ${VideoCodecUtils.qualityHeightFromStream(anchor)}p ${VideoCodecUtils.codecKeyFromStream(anchor)}, audio itag ${audio.itag}",
            )
        }

    @Test
    fun aVp9VideoWithOpusBecomesFilesTheBundledEngineCanRead() =
        live("bundled") {
            val result =
                InnerTubeVideoStreamExtractor.extract(VIDEO_ID) ?: run {
                    val block =
                        InnerTubeVideoStreamExtractor.blockOf(VIDEO_ID)
                            ?: fail("no client resolved $VIDEO_ID and none reported a block:\n${diagnostics()}")
                    return@live blocked("bundled", "every client refused $VIDEO_ID ($block): ${clientRefusals()}")
                }
            val vp9 =
                InnerTubeStreamBridge
                    .convertVideoFormats(result.videoFormats)
                    .filter { VideoCodecUtils.codecKeyFromStream(it) == "vp9" }
                    .maxByOrNull { VideoCodecUtils.qualityHeightFromStream(it) }
                    ?: return@live blocked("bundled", "no VP9 offered to this network among ${result.videoFormats.map { it.mimeType }}")
            val audio =
                AudioStreamSelector.selectPreferredAudioStream(
                    InnerTubeStreamBridge.convertAudioFormats(result.audioFormats),
                    preferredAudioLanguage = "original",
                    compatibilityFilter = { PlatformCodecSupport.canMergeAudio(it, PlaybackEngineKind.BUNDLED) },
                )
            assertNotNull(audio, "no audio among ${result.audioFormats.map { it.mimeType }}")
            val source =
                VideoPlaybackResolver(VIDEO_ID).resolve(
                    videoStreams = listOf(vp9),
                    audioStream = audio,
                    dashManifestUrl = null,
                    hlsUrl = null,
                    durationSeconds = result.playerResponse.videoDetails?.lengthSeconds?.toLongOrNull() ?: 0,
                )
            assertNotNull(source, "nothing resolved")
            assertTrue(source.engine == PlaybackEngineKind.BUNDLED && source.audioUri != null, "$source")
            checkFile(source.uri)
            checkFile(source.audioUri!!)
            println(
                "LIVE PLAYBACK: bundled OK via ${result.usedClient.clientName}: ${VideoCodecUtils.qualityHeightFromStream(vp9)}p ${vp9.codec}, " +
                    "audio itag ${audio.itag} ${audio.codec} of ${result.audioFormats.joinToString { "${it.itag} ${VideoCodecUtils.codecStringFromMimeType(it.mimeType)} ${it.averageBitrate}" }}",
            )
        }

    @Test
    fun aMusicTrackBecomesASourceItsEngineCanOpen() =
        live("music") {
            val songId =
                YouTube
                    .search("Queen Bohemian Rhapsody", YouTube.SearchFilter.FILTER_SONG)
                    .getOrNull()
                    ?.items
                    ?.filterIsInstance<SongItem>()
                    ?.firstOrNull()
                    ?.id ?: MUSIC_ID
            val playback = MusicPlayerUtils.playerResponseForPlayback(songId).getOrThrow()
            val stream = InnerTubeStreamBridge.convertAudioFormats(listOf(playback.format)).single()
            val source =
                VideoPlaybackResolver(songId).resolve(
                    videoStreams = emptyList(),
                    audioStream = stream,
                    dashManifestUrl = null,
                    hlsUrl = null,
                    durationSeconds = playback.videoDetails?.lengthSeconds?.toLongOrNull() ?: 0,
                )
            assertNotNull(source, "nothing resolved for $songId")
            // Upstream's music format choice prefers WebM (Opus), which the bundled engine plays; AAC plays in AVPlayer.
            if (source.engine == PlaybackEngineKind.BUNDLED) {
                checkFile(source.uri)
            } else {
                val master = http.get(source.uri).bodyAsText()
                checkMediaPlaylist(source.uri, master.lines().first { it.endsWith(".m3u8") && !it.startsWith("#") })
            }
            println("LIVE PLAYBACK: music OK $songId via ${playback.usedClient.clientName} itag ${playback.format.itag} ${playback.format.mimeType} on ${source.engine}")
        }

    @Test
    fun thePlayerScriptsCipherRunsInThePlatformEngine() =
        live("cipher") {
            val sts = CipherDeobfuscator.ensureSignatureTimestamp()
            assertNotNull(sts, "no signature timestamp in the player script")
            val url = "https://rr1---sn-test.googlevideo.com/videoplayback?itag=18&n=$SAMPLE_N&c=WEB"
            val transformed = CipherDeobfuscator.transformNParamInUrl(url)
            val n = Regex("[?&]n=([^&]+)").find(transformed)?.groupValues?.get(1)
            val signature = CipherDeobfuscator.deobfuscateSignature(SAMPLE_SIGNATURE)
            println(
                "LIVE CIPHER: player ${CipherDeobfuscator.playerHash()} sts=$sts n=${if (n != null && n != SAMPLE_N) "transformed" else "UNCHANGED"} " +
                    "signature=${if (signature != null) "solved" else "UNSOLVED"}" +
                    (CipherDeobfuscator.unparseablePlayerHash?.let { " unparseable=$it" } ?: ""),
            )
        }

    private suspend fun checkMediaPlaylist(
        masterUri: String,
        mediaUri: String,
    ) {
        val base = masterUri.substringBeforeLast('/')
        val media = http.get("$base/$mediaUri").bodyAsText()
        assertTrue("#EXT-X-MAP:URI=" in media && "#EXTINF:" in media && "#EXT-X-ENDLIST" in media, media.take(600))
        val segment = Regex("""#EXT-X-BYTERANGE:(\d+)@(\d+)\n(\S+)""").find(media)
        assertNotNull(segment, "no byte-range segment in ${media.take(600)}")
        val (length, offset, file) = segment.destructured
        val bytes =
            http
                .get("$base/$file") { header("Range", "bytes=$offset-${offset.toLong() + length.toLong() - 1}") }
                .readRawBytes()
        assertTrue(bytes.size.toLong() == length.toLong(), "segment is ${bytes.size} bytes, playlist says $length")
        assertTrue(bytes.copyOfRange(4, 8).decodeToString() == "moof", "segment does not start with a moof box")
    }

    // The bundled engine's read: a bounded byte range of the proxy's file route, answered 206 with the container's magic.
    private suspend fun checkFile(uri: String) {
        val response = http.get(uri) { header("Range", "bytes=0-65535") }
        val bytes = response.readRawBytes()
        assertTrue(response.status.value == 206 && bytes.size == 65_536, "$uri answered ${response.status} with ${bytes.size} bytes")
        val webm = bytes.copyOfRange(0, 4).contentEquals(byteArrayOf(0x1A, 0x45, 0xDF.toByte(), 0xA3.toByte()))
        val mp4 = bytes.copyOfRange(4, 8).decodeToString() == "ftyp"
        assertTrue(if (uri.endsWith(".webm")) webm else mp4, "$uri does not start like its container")
    }

    private fun diagnostics() = PlayerDiagnostics.entries().takeLast(40).joinToString("\n")

    private fun blocked(
        what: String,
        reason: String,
    ) = println("LIVE PLAYBACK: BLOCKED $what: $reason")

    private companion object {
        // Big Buck Bunny (Blender Foundation, CC BY 3.0): H.264 to 1080p60, VP9 and AV1 to 2160p60.
        const val VIDEO_ID = "aqz-KE-bpKQ"

        // Queen, Bohemian Rhapsody, the song when the YouTube Music search does not answer.
        const val MUSIC_ID = "lZUgQPTCTlo"
        const val SAMPLE_N = "KdrqFlzJXl9EcCwlmEy"
        const val SAMPLE_SIGNATURE = "AOq0QJ8wRgIhAKhdt2X3VbN3nk_dS3K1yh0E2D9bY6dQjR0j8XyAq4uiAiEA2n4K3p2s0mZq4f5p0wq5yY8hF0d4yQ6kq1xT6bU0aGw="
    }
}

private fun live(
    what: String,
    block: suspend () -> Unit,
) = runBlocking {
    try {
        withTimeout(120.seconds) { block() }
    } catch (error: Throwable) {
        val reason = blockReason(error) ?: playbackBlock(error) ?: throw error
        println("LIVE PLAYBACK: BLOCKED $what: $reason")
    }
}

// The ladder's own verdict that YouTube refused this viewer (a bot wall, sign-in, region), as MusicPlayerUtils reports it.
private fun playbackBlock(error: Throwable): String? {
    var cause: Throwable? = error
    while (cause != null) {
        if (cause is PlaybackBlockedException) return "every client refused (${cause.block}): ${clientRefusals()}"
        cause = cause.cause
    }
    return null
}

// One line per CI log: what each client of the ladder answered.
private fun clientRefusals(): String =
    PlayerDiagnostics
        .entries()
        .filter { "skip " in it.message || "unavailable" in it.message || "FAILED" in it.message }
        .takeLast(15)
        .joinToString(" | ") { it.message }
