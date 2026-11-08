package io.github.aedev.flow.player.datasource

import io.github.aedev.flow.innertube.YouTube
import io.github.aedev.flow.player.error.StreamDenialClassifier
import io.github.aedev.flow.utils.appFilesDir
import kotlin.concurrent.Volatile
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

/**
 * Debug builds only: refuses media the way GVS walls a visitor (#921), so the recovery can be
 * exercised on a network YouTube does not wall. A walled visitor gets a 403 for any request
 * starting past [FakeStreamWall.WALL_SECONDS] of stream position on the walled clients, with the
 * URL's deadline still valid, and a fresh visitor is served again.
 *
 * Switched on with a file `fake_stream_wall` in the app's files (Application Support on iOS), off by deleting it. The
 * visitor in use when the switch is first seen is the walled one. `files/fake_stream_wall_all`
 * instead walls every client for every visitor, so nothing can be swapped in and the player's own
 * reload and give-up are what run.
 *
 * On iOS the wall is asked by [LocalHlsProxy][io.github.aedev.flow.player.resolver.LocalHlsProxy] before it
 * forwards a byte range, where upstream wraps Media3's data source.
 */
internal class FakeStreamWall(
    private val isSwitchedOn: () -> Boolean,
    private val currentVisitor: () -> String?,
    private val wallsEveryone: () -> Boolean = { false },
) {
    @Volatile
    private var walledVisitor: String? = null

    fun refuses(
        url: String,
        position: Long,
    ): Boolean {
        if (wallsEveryone()) return isPastWall(url, position, anyClient = true)
        if (!isSwitchedOn()) {
            walledVisitor = null
            return false
        }
        val visitor = currentVisitor() ?: return false
        val walled = walledVisitor ?: visitor.also { walledVisitor = it }
        return visitor == walled && isPastWall(url, position)
    }

    companion object {
        const val WALL_SECONDS = 60.0
        private val WALLED_CLIENTS = setOf("VISIONOS", "ANDROID_VR")

        /** The byte offset of [WALL_SECONDS] in the file, read from the URL's own `clen` and `dur`. */
        fun isPastWall(
            url: String,
            position: Long,
            anyClient: Boolean = false,
        ): Boolean {
            if (!anyClient && StreamDenialClassifier.clientOf(url) !in WALLED_CLIENTS) return false
            val length = StreamDenialClassifier.queryParam(url, "clen")?.toLongOrNull() ?: return false
            val duration = StreamDenialClassifier.queryParam(url, "dur")?.toDoubleOrNull()?.takeIf { it > 0.0 } ?: return false
            if (duration <= WALL_SECONDS) return false
            return position >= (length * WALL_SECONDS / duration).toLong()
        }

        fun forDebugBuild(): FakeStreamWall {
            val switchFile = Path(appFilesDir(), "fake_stream_wall")
            val everyoneFile = Path(appFilesDir(), "fake_stream_wall_all")
            return FakeStreamWall(
                isSwitchedOn = { SystemFileSystem.exists(switchFile) },
                currentVisitor = { YouTube.visitorData },
                wallsEveryone = { SystemFileSystem.exists(everyoneFile) },
            )
        }
    }
}
