package io.github.aedev.flow.player.renderer.subtitle

import io.github.aedev.flow.player.subtitle.SubtitleDelay

/**
 * Decodes a caption file with [parser] and shifts it by the user's subtitle offset, read on every call so a new
 * offset applies to captions already loaded. Upstream runs it as Media3's text decoder; on iOS the player
 * manager calls [decode] when a caption file arrives.
 */
class DelayedSubtitleDecoder(
    private val parser: SubtitleParser,
    private val delay: SubtitleDelay,
) {
    fun decode(
        data: ByteArray,
        reset: Boolean = true,
    ): Subtitle {
        if (reset) parser.reset()
        return DelayedSubtitle(parser.parseToLegacySubtitle(data)) { delay.offsetUs }
    }
}

/** [subtitle] moved by [offsetUs]: a positive offset shows each caption later. */
internal class DelayedSubtitle(
    private val subtitle: Subtitle,
    private val offsetUs: () -> Long,
) : Subtitle {
    override fun getNextEventTimeIndex(timeUs: Long): Int = subtitle.getNextEventTimeIndex(timeUs - offsetUs())

    override fun getEventTimeCount(): Int = subtitle.getEventTimeCount()

    override fun getEventTime(index: Int): Long = subtitle.getEventTime(index) + offsetUs()

    override fun getCues(timeUs: Long): List<Cue> = subtitle.getCues(timeUs - offsetUs())
}
