package io.github.aedev.flow.innertube.pages.reel

import io.github.aedev.flow.testing.assertEquals
import kotlin.test.Test

class ReelParamsTest {
    @Test
    fun seedTokenIsTheProtobufIdBase64urlWithoutPadding() {
        assertEquals("CgtwSldxdlZuRFQwQQ", ReelParams.seedSequenceParams("pJWqvVnDT0A"))
    }

    @Test
    fun initialSequenceTokenIsWhatYoutubesOwnPlayerSendsFirst() {
        assertEquals("CA8%3D", ReelParams.INITIAL_SEQUENCE)
    }

    @Test
    fun searchShortsFilterIsTheShortsContentType() {
        assertEquals("EgIQCQ%3D%3D", ReelParams.SEARCH_SHORTS_FILTER)
    }
}
