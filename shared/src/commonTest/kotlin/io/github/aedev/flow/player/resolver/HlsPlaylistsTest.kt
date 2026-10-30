package io.github.aedev.flow.player.resolver

import io.github.aedev.flow.testing.assertThat
import kotlin.test.Test

class HlsPlaylistsTest {
    private fun sidx(
        version: Int,
        timescale: Long,
        firstOffset: Long,
        references: List<Pair<Long, Long>>,
    ): ByteArray {
        val bytes = mutableListOf<Byte>()

        fun u(
            value: Long,
            size: Int,
        ) = (size - 1 downTo 0).forEach { bytes += ((value shr (8 * it)) and 0xFF).toByte() }
        val size = 12 + 8 + (if (version == 0) 8 else 16) + 4 + references.size * 12
        u(size.toLong(), 4)
        "sidx".encodeToByteArray().forEach { bytes += it }
        u(version.toLong(), 1)
        u(0, 3)
        u(1, 4)
        u(timescale, 4)
        u(0, if (version == 0) 4 else 8)
        u(firstOffset, if (version == 0) 4 else 8)
        u(0, 2)
        u(references.size.toLong(), 2)
        references.forEach { (referencedSize, duration) ->
            u(referencedSize, 4)
            u(duration, 4)
            u(0x9000_0000, 4)
        }
        return bytes.toByteArray()
    }

    @Test
    fun subsegmentsStartRightAfterTheIndexPlusItsFirstOffset() {
        val box = sidx(version = 0, timescale = 1000, firstOffset = 0, references = listOf(1000L to 5005L, 2000L to 4000L))

        val segments = SidxParser.parse(box, boxStart = 741)!!

        assertThat(segments).containsExactly(
            SidxSegment(offset = 741L + box.size, size = 1000, durationSeconds = 5.005),
            SidxSegment(offset = 741L + box.size + 1000, size = 2000, durationSeconds = 4.0),
        ).inOrder()
    }

    @Test
    fun versionOneUsesSixtyFourBitTimesAndOffsets() {
        val box = sidx(version = 1, timescale = 90_000, firstOffset = 16, references = listOf(500L to 450_000L))

        val segments = SidxParser.parse(box, boxStart = 0)!!

        assertThat(segments.single()).isEqualTo(SidxSegment(offset = box.size + 16L, size = 500, durationSeconds = 5.0))
    }

    @Test
    fun anythingButASidxIsRejected() {
        val box = sidx(version = 0, timescale = 1000, firstOffset = 0, references = listOf(1L to 1L))
        "moof".encodeToByteArray().copyInto(box, destinationOffset = 4)

        assertThat(SidxParser.parse(box, 0)).isNull()
        assertThat(SidxParser.parse(ByteArray(8), 0)).isNull()
    }

    private val video =
        HlsRendition(
            url = "https://v",
            codecs = "avc1.4d401f",
            bandwidth = 2_500_000,
            isAudio = false,
            initStart = 0,
            initEnd = 740,
            indexStart = 741,
            indexEnd = 1560,
            width = 1280,
            height = 720,
            frameRate = 30,
        )
    private val audio =
        HlsRendition(
            url = "https://a",
            codecs = "mp4a.40.2",
            bandwidth = 130_000,
            isAudio = true,
            initStart = 0,
            initEnd = 631,
            indexStart = 632,
            indexEnd = 1219,
            audioLanguage = "en",
            audioChannels = 2,
        )

    @Test
    fun theMasterPutsAudioInAGroupEveryVariantNames() {
        val master = HlsPlaylists.master(HlsPresentation(listOf(video), audio, 212_000)) { if (it.isAudio) "1.m3u8" else "0.m3u8" }

        assertThat(master).contains("""#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="audio",NAME="en",LANGUAGE="en",CHANNELS="2",DEFAULT=YES,AUTOSELECT=YES,URI="1.m3u8"""")
        assertThat(master).contains(
            """#EXT-X-STREAM-INF:BANDWIDTH=2630000,CODECS="avc1.4d401f,mp4a.40.2",RESOLUTION=1280x720,FRAME-RATE=30.000,AUDIO="audio"""" +
                "\n0.m3u8",
        )
    }

    @Test
    fun anAudioOnlyMasterHasOneVariant() {
        val master = HlsPlaylists.master(HlsPresentation(emptyList(), audio, 212_000)) { "0.m3u8" }

        assertThat(master).contains("#EXT-X-STREAM-INF:BANDWIDTH=130000,CODECS=\"mp4a.40.2\"\n0.m3u8")
        assertThat(master).doesNotContain("#EXT-X-MEDIA")
    }

    @Test
    fun theMediaPlaylistMapsTheInitAndEverySubsegment() {
        val media =
            HlsPlaylists.media(
                video,
                listOf(SidxSegment(1561, 1000, 5.005), SidxSegment(2561, 2000, 4.0)),
                "0.mp4",
            )

        assertThat(media).contains("#EXT-X-TARGETDURATION:6\n")
        assertThat(media).contains("#EXT-X-MAP:URI=\"0.mp4\",BYTERANGE=\"741@0\"\n")
        assertThat(media).contains("#EXTINF:5.005000,\n#EXT-X-BYTERANGE:1000@1561\n0.mp4\n")
        assertThat(media).contains("#EXTINF:4.000000,\n#EXT-X-BYTERANGE:2000@2561\n0.mp4\n#EXT-X-ENDLIST")
    }
}
