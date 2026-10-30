/*
 * Ported from NewPipe Extractor v0.26.5: ExtractionException.java and ParsingException.java.
 * Copyright (C) 2016 Christian Schabesberger <chris.schabesberger@mailbox.org>
 * NewPipe Extractor is free software under the GNU General Public License v3 or later.
 */

package org.schabi.newpipe.extractor.exceptions

open class ExtractionException : Exception {
    constructor(message: String?) : super(message)
    constructor(cause: Throwable?) : super(cause)
    constructor(message: String?, cause: Throwable?) : super(message, cause)
}

class ParsingException : ExtractionException {
    constructor(message: String?) : super(message)
    constructor(message: String?, cause: Throwable?) : super(message, cause)
}
