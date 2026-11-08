/*
 * Ported from NewPipe Extractor v0.26.5: ContentNotAvailableException.java and the exceptions that extend it,
 * ContentNotSupportedException.java and ReCaptchaException.java, which VideoErrorMapper tells apart.
 * Copyright (C) 2016 Christian Schabesberger <chris.schabesberger@mailbox.org>
 * NewPipe Extractor is free software under the GNU General Public License v3 or later.
 */
package org.schabi.newpipe.extractor.exceptions

open class ContentNotAvailableException : ParsingException {
    constructor(message: String?) : super(message)
    constructor(message: String?, cause: Throwable?) : super(message, cause)
}

class AccountTerminatedException(
    message: String?,
) : ContentNotAvailableException(message)

class AgeRestrictedContentException(
    message: String?,
) : ContentNotAvailableException(message)

class GeographicRestrictionException(
    message: String?,
) : ContentNotAvailableException(message)

class PaidContentException(
    message: String?,
) : ContentNotAvailableException(message)

class PrivateContentException(
    message: String?,
) : ContentNotAvailableException(message)

class UnsupportedContentInCountryException(
    message: String?,
) : ContentNotAvailableException(message)

class YoutubeMusicPremiumContentException : ContentNotAvailableException("This video is a YouTube Music Premium video")

class ContentNotSupportedException(
    message: String?,
) : ParsingException(message)

class ReCaptchaException(
    message: String?,
    val url: String,
) : ExtractionException(message)
