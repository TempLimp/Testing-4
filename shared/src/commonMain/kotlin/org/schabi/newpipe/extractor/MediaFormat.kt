/*
 * Ported from NewPipe Extractor v0.26.5, MediaFormat.java
 * Copyright (c) 2015 Christian Schabesberger <chris.schabesberger@mailbox.org>
 *     and Adam Howard <achdisposable1@gmail.com>
 * NewPipe Extractor is free software under the GNU General Public License v3 or later.
 */

package org.schabi.newpipe.extractor

/**
 * The Java enum's public `name` field cannot be declared on a Kotlin enum (it would hide `Enum.name`),
 * so the display name is [getName] only and `.name` is the constant's name. Every format YouTube
 * serves yields the same lowercase tokens either way ("webm", "mpeg"), which is all upstream reads.
 */
@Suppress("EnumEntryName")
enum class MediaFormat(
    val id: Int,
    private val displayName: String,
    val suffix: String,
    val mimeType: String,
) {
    MPEG_4(0x0, "MPEG-4", "mp4", "video/mp4"),
    v3GPP(0x10, "3GPP", "3gp", "video/3gpp"),
    WEBM(0x20, "WebM", "webm", "video/webm"),
    M4A(0x100, "m4a", "m4a", "audio/mp4"),
    WEBMA(0x200, "WebM", "webm", "audio/webm"),
    MP3(0x300, "MP3", "mp3", "audio/mpeg"),
    MP2(0x310, "MP2", "mp2", "audio/mpeg"),
    OPUS(0x400, "opus", "opus", "audio/opus"),
    OGG(0x500, "ogg", "ogg", "audio/ogg"),
    WEBMA_OPUS(0x200, "WebM Opus", "webm", "audio/webm"),
    AIFF(0x600, "AIFF", "aiff", "audio/aiff"),
    AIF(0x600, "AIFF", "aif", "audio/aiff"),
    WAV(0x700, "WAV", "wav", "audio/wav"),
    FLAC(0x800, "FLAC", "flac", "audio/flac"),
    ALAC(0x900, "ALAC", "alac", "audio/alac"),
    VTT(0x1000, "WebVTT", "vtt", "text/vtt"),
    TTML(0x2000, "Timed Text Markup Language", "ttml", "application/ttml+xml"),
    TRANSCRIPT1(0x3000, "TranScript v1", "srv1", "text/xml"),
    TRANSCRIPT2(0x4000, "TranScript v2", "srv2", "text/xml"),
    TRANSCRIPT3(0x5000, "TranScript v3", "srv3", "text/xml"),
    SRT(0x6000, "SubRip file format", "srt", "text/srt"),
    ;

    fun getName(): String = displayName

    companion object {
        fun getNameById(id: Int): String = entries.firstOrNull { it.id == id }?.displayName ?: ""

        fun getSuffixById(id: Int): String = entries.firstOrNull { it.id == id }?.suffix ?: ""

        fun getMimeById(id: Int): String? = entries.firstOrNull { it.id == id }?.mimeType

        fun getFromMimeType(mimeType: String): MediaFormat? = entries.firstOrNull { it.mimeType == mimeType }

        fun getAllFromMimeType(mimeType: String): List<MediaFormat> = entries.filter { it.mimeType == mimeType }

        fun getFormatById(id: Int): MediaFormat? = entries.firstOrNull { it.id == id }

        fun getFromSuffix(suffix: String): MediaFormat? = entries.firstOrNull { it.suffix == suffix }
    }
}
