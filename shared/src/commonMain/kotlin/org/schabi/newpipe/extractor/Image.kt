/*
 * Ported from NewPipe Extractor v0.26.5, org.schabi.newpipe.extractor.Image.
 * NewPipe Extractor is free software under the GNU General Public License v3 or later.
 */
package org.schabi.newpipe.extractor

/** An image and its size where known, as NewPipe's extractor reports one. */
data class Image(
    val url: String,
    val height: Int,
    val width: Int,
    val estimatedResolutionLevel: ResolutionLevel,
) {
    enum class ResolutionLevel {
        HIGH,
        MEDIUM,
        LOW,
        UNKNOWN,
        ;

        companion object {
            fun fromHeight(heightPx: Int): ResolutionLevel =
                when {
                    heightPx <= 0 -> UNKNOWN
                    heightPx < 175 -> LOW
                    heightPx < 720 -> MEDIUM
                    else -> HIGH
                }
        }
    }

    companion object {
        const val HEIGHT_UNKNOWN = -1
        const val WIDTH_UNKNOWN = -1
    }
}
