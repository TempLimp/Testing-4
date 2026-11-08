package io.github.aedev.flow.player

import io.github.aedev.flow.testing.assertThat
import kotlin.test.Test

@Suppress("DEPRECATION")
class MemoryPressurePolicyTest {
    private val nonPressureLevels =
        listOf(
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE,
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW,
            ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN,
            ComponentCallbacks2.TRIM_MEMORY_BACKGROUND,
        )

    private val pressureLevels =
        listOf(
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL,
            ComponentCallbacks2.TRIM_MEMORY_MODERATE,
            ComponentCallbacks2.TRIM_MEMORY_COMPLETE,
        )

    @Test
    fun runningCriticalWhileTheVideoIsVisibleKeepsThePictureAndDropsThePreload() {
        assertThat(MemoryPressurePolicy.responseTo(ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL, videoVisible = true))
            .isEqualTo(MemoryPressureResponse.DROP_PRELOAD)
    }

    @Test
    fun runningCriticalWhileHiddenReleasesVideoPlayback() {
        assertThat(MemoryPressurePolicy.responseTo(ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL, videoVisible = false))
            .isEqualTo(MemoryPressureResponse.RELEASE_VIDEO)
    }

    @Test
    fun moderateBackgroundPressureWhileHiddenReleasesRebuildableVideoPlayback() {
        assertThat(MemoryPressurePolicy.responseTo(ComponentCallbacks2.TRIM_MEMORY_MODERATE, videoVisible = false))
            .isEqualTo(MemoryPressureResponse.RELEASE_VIDEO)
    }

    @Test
    fun completePressureWhileHiddenReleasesRebuildableVideoPlayback() {
        assertThat(MemoryPressurePolicy.responseTo(ComponentCallbacks2.TRIM_MEMORY_COMPLETE, videoVisible = false))
            .isEqualTo(MemoryPressureResponse.RELEASE_VIDEO)
    }

    @Test
    fun completePressureInPictureInPictureDoesNotReleaseVideoPlayback() {
        assertThat(MemoryPressurePolicy.responseTo(ComponentCallbacks2.TRIM_MEMORY_COMPLETE, videoVisible = true))
            .isEqualTo(MemoryPressureResponse.DROP_PRELOAD)
    }

    @Test
    fun aVisibleVideoNeverReleasesAtAnyLevel() {
        for (level in pressureLevels + nonPressureLevels) {
            assertThat(MemoryPressurePolicy.responseTo(level, videoVisible = true))
                .isNotEqualTo(MemoryPressureResponse.RELEASE_VIDEO)
        }
    }

    @Test
    fun levelsThatDoNotSignalPressureChangeNothingWhetherVisibleOrHidden() {
        for (level in nonPressureLevels) {
            for (visible in listOf(true, false)) {
                assertThat(MemoryPressurePolicy.responseTo(level, visible))
                    .isEqualTo(MemoryPressureResponse.NONE)
            }
        }
    }

    @Test
    fun enteringPictureInPictureRestoresVideoLeftInAudioOnlyMode() {
        assertThat(pipRestore(isAudioOnly = true)).isTrue()
    }

    @Test
    fun enteringPictureInPictureCompletesADeferredSurfaceRestore() {
        assertThat(pipRestore(isVideoRestorePending = true)).isTrue()
    }

    @Test
    fun enteringPictureInPictureWithVideoAlreadyOnDoesNothing() {
        assertThat(pipRestore()).isFalse()
    }

    @Test
    fun leavingPictureInPictureDoesNotRestoreVideo() {
        assertThat(pipRestore(isInPip = false, isAudioOnly = true, isVideoRestorePending = true)).isFalse()
    }

    @Test
    fun explicitBackgroundPlaybackStaysAudioOnlyWhenAPictureInPictureWindowOpens() {
        assertThat(pipRestore(isAudioOnly = true, explicitBackground = true)).isFalse()
    }

    private fun pipRestore(
        isInPip: Boolean = true,
        isAudioOnly: Boolean = false,
        isVideoRestorePending: Boolean = false,
        explicitBackground: Boolean = false,
    ): Boolean =
        MemoryPressurePolicy.shouldRestoreVideoOnPipEntry(
            isInPictureInPictureMode = isInPip,
            isAudioOnly = isAudioOnly,
            isVideoRestorePending = isVideoRestorePending,
            explicitBackgroundPlaybackActive = explicitBackground,
        )
}
