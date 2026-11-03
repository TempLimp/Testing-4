package io.github.aedev.flow.data.playlist

import io.github.aedev.flow.testing.assertThat
import kotlin.test.Test

// The enum's case of upstream's PlaylistListOrderTest (S06 pulled the enum forward). S07 adds the three sortedFor
// cases with that function.
class PlaylistListOrderTest {
    @Test
    fun anUnknownStoredValueFallsBackToNewest() {
        assertThat(PlaylistListOrder.fromStorageValue("bogus")).isEqualTo(PlaylistListOrder.NEWEST)
        assertThat(PlaylistListOrder.fromStorageValue("custom")).isEqualTo(PlaylistListOrder.CUSTOM)
    }
}