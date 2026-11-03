/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/Flow-Tube/Flow).
 */

package io.github.aedev.flow.data.music.model

import io.github.aedev.flow.data.local.GsonCompatJson
import io.github.aedev.flow.testing.assertThat
import kotlin.test.Test

/**
 * MusicTrack moved from io.github.aedev.flow.ui.screens.music to this package. Queues saved by
 * earlier installs live in DataStore as Gson JSON written by QueuePersistence, so the payload
 * emitted before the move must still deserialize here or every upgrading user loses their queue.
 * The port reads and writes that JSON with [GsonCompatJson], which these cases hold to Gson's format.
 */
class MusicTrackGsonCompatibilityTest {
    private val legacyQueueJson =
        """
        [
          {
            "videoId": "dQw4w9WgXcQ",
            "title": "Never Gonna Give You Up",
            "artist": "Rick Astley",
            "thumbnailUrl": "https://i.ytimg.com/vi/dQw4w9WgXcQ/hq720.jpg",
            "duration": 213,
            "views": 1500000000,
            "sourceUrl": "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            "album": "Whenever You Need Somebody",
            "channelId": "UCuAXFkgsw1L7xaCfnd5JJOw",
            "isExplicit": false,
            "isVideoSong": true,
            "albumId": "MPREb_album",
            "artists": [{ "name": "Rick Astley", "id": "UCuAXFkgsw1L7xaCfnd5JJOw" }],
            "itemType": "SONG"
          }
        ]
        """.trimIndent()

    private val preArtistsQueueJson =
        """
        [
          {
            "videoId": "abc123",
            "title": "Older Save",
            "artist": "Someone",
            "thumbnailUrl": "",
            "duration": 180,
            "views": 0,
            "sourceUrl": "",
            "album": "",
            "channelId": "UCsomeone",
            "isExplicit": false,
            "isVideoSong": false,
            "itemType": "SONG"
          }
        ]
        """.trimIndent()

    @Test
    fun aQueueSavedBeforeThePackageMoveStillDeserializes() {
        val queue: List<MusicTrack> = GsonCompatJson.decodeFromString<List<MusicTrack>>(legacyQueueJson)

        assertThat(queue).hasSize(1)
        val track = queue.single()
        assertThat(track.videoId).isEqualTo("dQw4w9WgXcQ")
        assertThat(track.title).isEqualTo("Never Gonna Give You Up")
        assertThat(track.artist).isEqualTo("Rick Astley")
        assertThat(track.duration).isEqualTo(213)
        assertThat(track.views).isEqualTo(1_500_000_000L)
        assertThat(track.album).isEqualTo("Whenever You Need Somebody")
        assertThat(track.channelId).isEqualTo("UCuAXFkgsw1L7xaCfnd5JJOw")
        assertThat(track.isExplicit).isFalse()
        assertThat(track.isVideoSong).isTrue()
        assertThat(track.albumId).isEqualTo("MPREb_album")
        assertThat(track.itemType).isEqualTo(MusicItemType.SONG)
        assertThat(track.artists).containsExactly(MusicArtist("Rick Astley", "UCuAXFkgsw1L7xaCfnd5JJOw"))
    }

    @Test
    fun theRestorePathSurvivesASaveWrittenBeforeTheArtistsFieldExisted() {
        val queue: List<MusicTrack> = GsonCompatJson.decodeFromString<List<MusicTrack>>(preArtistsQueueJson)
        val restored = queue.map { it.withTypedArtists() }

        assertThat(restored.single().videoId).isEqualTo("abc123")
        assertThat(restored.single().artists).isEmpty()
    }

    @Test
    fun aRoundTripThroughGsonKeysOnFieldNamesNotOnTheClassName() {
        val original: List<MusicTrack> = GsonCompatJson.decodeFromString<List<MusicTrack>>(legacyQueueJson)
        val reparsed: List<MusicTrack> = GsonCompatJson.decodeFromString<List<MusicTrack>>(GsonCompatJson.encodeToString(original))

        assertThat(reparsed).isEqualTo(original)
        assertThat(GsonCompatJson.encodeToString(original)).doesNotContain("io.github.aedev.flow")
    }

    @Test
    fun everyFieldFlowWritesIsRoundTrippedSoAFreshSaveReloadsIntact() {
        val saved =
            MusicTrack(
                videoId = "v9",
                title = "Title",
                artist = "Artist",
                thumbnailUrl = "https://example.invalid/t.jpg",
                duration = 321,
                views = 42,
                sourceUrl = "https://example.invalid/watch",
                album = "Album",
                channelId = "UCchannel",
                isExplicit = true,
                isVideoSong = true,
                albumId = "MPREb_x",
                artists = listOf(MusicArtist("Artist", "UCchannel")),
                itemType = MusicItemType.ALBUM,
            )

        val reparsed: List<MusicTrack> = GsonCompatJson.decodeFromString<List<MusicTrack>>(GsonCompatJson.encodeToString(listOf(saved)))

        assertThat(reparsed.single()).isEqualTo(saved)
    }
}
