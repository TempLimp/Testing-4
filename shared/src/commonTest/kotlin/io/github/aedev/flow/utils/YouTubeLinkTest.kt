package io.github.aedev.flow.utils

import io.github.aedev.flow.testing.assertThat
import io.github.aedev.flow.utils.YouTubeLink.Album
import io.github.aedev.flow.utils.YouTubeLink.Channel
import io.github.aedev.flow.utils.YouTubeLink.ChannelHandle
import io.github.aedev.flow.utils.YouTubeLink.LegacyChannel
import io.github.aedev.flow.utils.YouTubeLink.Playlist
import io.github.aedev.flow.utils.YouTubeLink.Search
import io.github.aedev.flow.utils.YouTubeLink.Short
import io.github.aedev.flow.utils.YouTubeLink.Video
import kotlin.test.Test

class YouTubeLinkTest {
    private val video = "dQw4w9WgXcQ"
    private val channel = "UCXuqSBlHAE6Xw-yeJA0Tunw"

    private fun assertParses(
        expected: YouTubeLink?,
        vararg links: String,
    ) = links.forEach { assertThat(parseYouTubeLink(it)).isEqualTo(expected) }

    @Test
    fun readsAVideoOutOfEveryWatchUrlShape() {
        assertParses(
            Video(video, isMusic = false),
            "https://www.youtube.com/watch?v=$video",
            "https://youtube.com/watch?v=$video&si=abc123&pp=ygUFY2F0cw%3D%3D",
            "https://m.youtube.com/watch?t=30&v=$video",
            "http://www.youtube.com/watch?app=desktop&v=$video&t=1m2s",
            "https://youtu.be/$video",
            "https://youtu.be/$video?si=abc123&t=42",
            "https://www.youtube.com/embed/$video",
            "https://www.youtube-nocookie.com/embed/$video?start=5",
            "https://www.youtube.com/live/$video?si=abc",
            "https://www.youtube.com/v/$video",
            "youtu.be/$video",
        )
    }

    @Test
    fun aYoutubeMusicWatchLinkIsMusic() {
        assertParses(
            Video(video, isMusic = true),
            "https://music.youtube.com/watch?v=$video",
            "https://m.music.youtube.com/watch?v=$video",
        )
    }

    @Test
    fun aWatchLinkInsideAPlaylistKeepsThePlaylistItPlaysFrom() {
        assertParses(
            Video(video, isMusic = false, playlistId = "PLabcdef"),
            "https://www.youtube.com/watch?v=$video&list=PLabcdef",
            "https://www.youtube.com/watch?list=PLabcdef&v=$video&index=4",
        )
        assertParses(
            Video(video, isMusic = true, playlistId = "RDAMVM$video"),
            "https://music.youtube.com/watch?v=$video&si=abc&list=RDAMVM$video",
        )
        assertParses(Video(video, isMusic = false), "https://www.youtube.com/watch?v=$video&list=")
    }

    @Test
    fun aBrowseIdOpensAnAlbumAChannelOrAPlaylist() {
        assertThat(youTubeBrowseLink("MPREb_abc123")).isEqualTo(Album("MPREb_abc123"))
        assertThat(youTubeBrowseLink(channel)).isEqualTo(Channel(channel, isMusic = false))
        assertThat(youTubeBrowseLink("VLPLabcdef")).isEqualTo(Playlist("PLabcdef", isMusic = false))
        assertThat(youTubeBrowseLink("FEmusic_home")).isNull()
    }

    @Test
    fun readsAShort() {
        assertParses(
            Short(video),
            "https://www.youtube.com/shorts/$video",
            "https://youtube.com/shorts/$video?si=abc123&feature=share",
            "https://m.youtube.com/shorts/$video",
        )
    }

    @Test
    fun readsPlaylistsIncludingOnesLinkedWithoutAVideo() {
        assertParses(Playlist("PLabcdef-_123", isMusic = false), "https://www.youtube.com/playlist?list=PLabcdef-_123&si=x")
        assertParses(Playlist("PLabcdef", isMusic = false), "https://www.youtube.com/watch?list=PLabcdef")
        assertParses(Playlist("PLabcdef", isMusic = true), "https://music.youtube.com/playlist?list=PLabcdef")
        assertParses(Playlist("OLAK5uy_abcdef", isMusic = true), "https://www.youtube.com/playlist?list=OLAK5uy_abcdef")
        assertParses(Playlist("RDabcdef", isMusic = false), "https://www.youtube.com/playlist?list=RDabcdef")
        assertParses(Playlist("LL", isMusic = false), "https://www.youtube.com/playlist?list=LL")
        assertParses(Playlist("WL", isMusic = false), "https://www.youtube.com/playlist?list=WL")
        assertParses(Playlist("PLabcdef", isMusic = true), "https://music.youtube.com/browse/VLPLabcdef")
    }

    @Test
    fun readsAlbumsAndMusicChannelsFromBrowseLinks() {
        assertParses(Album("MPREb_abc123"), "https://music.youtube.com/browse/MPREb_abc123")
        assertParses(Channel(channel, isMusic = true), "https://music.youtube.com/browse/$channel")
        assertParses(Channel(channel, isMusic = true), "https://music.youtube.com/channel/$channel?si=x")
    }

    @Test
    fun readsEveryChannelLinkShape() {
        assertParses(
            Channel(channel, isMusic = false),
            "https://www.youtube.com/channel/$channel",
            "https://m.youtube.com/channel/$channel/videos?si=abc",
        )
        assertParses(
            ChannelHandle("@LinusTechTips"),
            "https://www.youtube.com/@LinusTechTips",
            "https://youtube.com/@LinusTechTips/videos?si=abc",
        )
        assertParses(ChannelHandle("@ハンドル"), "https://www.youtube.com/@%E3%83%8F%E3%83%B3%E3%83%89%E3%83%AB")
        assertParses(LegacyChannel("c", "LinusTechTips"), "https://www.youtube.com/c/LinusTechTips")
        assertParses(LegacyChannel("user", "LinusTechTips"), "https://www.youtube.com/user/LinusTechTips/featured")
    }

    @Test
    fun aBareCustomNameIsAChannelUnlessItNamesAYoutubePage() {
        assertParses(LegacyChannel("", "officialpsy"), "http://www.youtube.com/officialpsy", "https://youtube.com/officialpsy/videos")
        assertThat(LegacyChannel("", "officialpsy").url).isEqualTo("https://www.youtube.com/officialpsy")
        assertParses(
            null,
            "https://www.youtube.com/premium",
            "https://www.youtube.com/officialpsy/videos/extra",
            "https://www.youtube.com/officialpsy/unknownTab",
            "https://music.youtube.com/library",
        )
    }

    @Test
    fun anElevenCharacterCustomNameIsAChannelNotAVideo() {
        assertParses(LegacyChannel("c", "abcdefghijk"), "https://www.youtube.com/c/abcdefghijk")
        assertParses(LegacyChannel("user", "abcdefghijk"), "https://www.youtube.com/user/abcdefghijk")
    }

    @Test
    fun readsASearch() {
        assertParses(Search("cute cats"), "https://www.youtube.com/results?search_query=cute+cats&sp=EgIQAQ%3D%3D")
        assertParses(null, "https://www.youtube.com/results?search_query=")
    }

    @Test
    fun findsTheLinkInsideAShareMessage() {
        assertParses(Video(video, isMusic = false), "Check out this video: Never Gonna\nhttps://youtube.com/watch?v=$video")
        assertParses(Video(video, isMusic = true), "Check out this song: A by B (https://music.youtube.com/watch?v=$video).")
        assertParses(Playlist("PLabcdef", isMusic = false), "Watch this https://www.youtube.com/playlist?list=PLabcdef, it is great")
    }

    @Test
    fun readsTheSupportedFrontEnds() {
        assertParses(Video(video, isMusic = false), "https://yewtu.be/watch?v=$video", "https://piped.video/watch?v=$video")
        assertParses(Playlist("PLabcdef", isMusic = false), "https://piped.video/playlist?list=PLabcdef")
    }

    @Test
    fun ordinarySearchPhrasesAreNotLinks() {
        assertParses(null, "sam sulek", "how to build a pc", "linus tech tips", "cats", "")
    }

    @Test
    fun lookalikeHostsAreNotYoutube() {
        assertParses(
            null,
            "https://youtube.com.evil.example/watch?v=$video",
            "https://notyoutube.com/watch?v=$video",
            "https://evil.example/youtube.com/watch?v=$video",
            "https://youtu.be.evil.example/$video",
        )
    }

    @Test
    fun anUnknownOrMalformedPathIsNeverGuessedToBeAVideo() {
        assertParses(
            null,
            "https://www.youtube.com/",
            "https://www.youtube.com/feed/subscriptions",
            "https://www.youtube.com/post/UgkxAbCdEfGhIjKlMnOpQrStUvWxYz",
            "https://www.youtube.com/watch?v=short",
            "https://www.youtube.com/shorts/tooShort",
            "https://www.youtube.com/channel/notAChannelId",
            "https://www.youtube.com/@a",
            "https://music.youtube.com/browse/FEmusic_home",
            "https://youtu.be/",
        )
    }
}
