package io.github.aedev.flow.utils.potoken

import io.github.aedev.flow.testing.assertThat
import kotlin.test.Test

class VisitorIdentityAgeTest {
    private val savedAt = 1_000_000L

    @Test
    fun aVisitorYoungerThanAWeekIsReused() {
        assertThat(VisitorIdentityAge.isReusable("CgtB", savedAt, savedAt + VisitorIdentityAge.MAX_AGE_MS - 1)).isTrue()
    }

    @Test
    fun aWeekOldVisitorIsReplaced() {
        assertThat(VisitorIdentityAge.isReusable("CgtB", savedAt, savedAt + VisitorIdentityAge.MAX_AGE_MS)).isFalse()
    }

    @Test
    fun aVisitorWithNoSaveTimeOrNoValueIsReplaced() {
        assertThat(VisitorIdentityAge.isReusable("CgtB", 0L, savedAt)).isFalse()
        assertThat(VisitorIdentityAge.isReusable("", savedAt, savedAt)).isFalse()
        assertThat(VisitorIdentityAge.isReusable(null, savedAt, savedAt)).isFalse()
    }

    @Test
    fun aSaveTimeInTheFutureAfterAClockChangeIsNotTrusted() {
        assertThat(VisitorIdentityAge.isReusable("CgtB", savedAt, savedAt - 1)).isFalse()
    }
}
