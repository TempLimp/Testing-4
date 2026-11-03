package io.github.aedev.flow.data.video.storage

import io.github.aedev.flow.testing.assertThat
import kotlin.test.Test

class DownloadNamingTest {
    @Test
    fun readableTitlesKeepTheirPunctuationAndLoseOnlyIllegalCharacters() {
        assertThat(DownloadNaming.sanitize("AC/DC: Back In Black (Live) | 1980?", "id"))
            .isEqualTo("AC DC Back In Black (Live) 1980")
        assertThat(DownloadNaming.sanitize("  ...hidden. ", "id")).isEqualTo("hidden")
        assertThat(DownloadNaming.sanitize("\u0000\u001F", "vid123")).isEqualTo("vid123")
    }

    @Test
    fun longMultiByteTitlesAreCutByBytesNotCharacters() {
        val japanese = "日本語のタイトル".repeat(30)
        val name = DownloadNaming.fileName(japanese, "id", "m4a")

        assertThat(name.encodeToByteArray().size).isAtMost(DownloadNaming.MAX_NAME_BYTES)
        assertThat(name).endsWith(".m4a")
    }

    @Test
    fun aCutNeverSplitsASurrogatePair() {
        val emoji = "🎵".repeat(100)

        val cut = DownloadNaming.truncateToBytes(emoji, 10)

        assertThat(cut).isEqualTo("🎵🎵")
    }

    @Test
    fun albumTracksLeadWithAZeroPaddedNumber() {
        assertThat(DownloadNaming.fileName("Intro", "id", "m4a", trackNumber = 1, trackTotal = 14)).isEqualTo("01 - Intro.m4a")
        assertThat(DownloadNaming.fileName("Coda", "id", "m4a", trackNumber = 7, trackTotal = 120)).isEqualTo("007 - Coda.m4a")
        assertThat(DownloadNaming.fileName("Single", "id", "mp4")).isEqualTo("Single.mp4")
    }

    @Test
    fun albumFoldersCarryTheArtistPlaylistFoldersOnlyTheirTitle() {
        assertThat(DownloadNaming.folderName("Album", "Artist", isAlbum = true, fallback = "MPREb")).isEqualTo("Artist - Album")
        assertThat(DownloadNaming.folderName("Road Trip", "Me", isAlbum = false, fallback = "PL")).isEqualTo("Road Trip")
        assertThat(DownloadNaming.folderName("", null, isAlbum = false, fallback = "PL123")).isEqualTo("PL123")
    }

    @Test
    fun aTakenNameGetsTheFirstFreeNumberedSuffix() {
        val taken = setOf("Intro.m4a", "Intro (2).m4a")

        assertThat(DownloadNaming.unique("Intro.m4a") { it in taken }).isEqualTo("Intro (3).m4a")
        assertThat(DownloadNaming.unique("Fresh.m4a") { it in taken }).isEqualTo("Fresh.m4a")
        assertThat(DownloadNaming.unique("Folder") { it == "Folder" }).isEqualTo("Folder (2)")
    }

    @Test
    fun aFolderTitleWithADotIsSuffixedAtItsEndNotBeforeTheDot() {
        assertThat(DownloadNaming.unique("Vol. 2", hasExtension = false) { it == "Vol. 2" }).isEqualTo("Vol. 2 (2)")
    }
}
