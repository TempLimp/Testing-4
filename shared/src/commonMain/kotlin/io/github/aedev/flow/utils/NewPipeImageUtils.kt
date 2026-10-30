package io.github.aedev.flow.utils

// Upstream's bestImageUrl and distinctBestImageUrls read NewPipe's Image lists; they arrive with the
// repository that replaces the NewPipe path (S07). The identity key is shared with the InnerTube parsers.

internal fun String?.avatarImageIdentityKey(): String =
    orEmpty()
        .substringBefore("?")
        .replace(Regex("=s\\d+.*$"), "")
