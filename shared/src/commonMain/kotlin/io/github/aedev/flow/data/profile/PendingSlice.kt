package io.github.aedev.flow.data.profile

import io.github.aedev.flow.utils.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlin.concurrent.Volatile

/**
 * A seed or restore that waits in a profile's private folder until the profile opens. The start destination waits
 * for it, so a copied profile opens on Home with its settings instead of flashing onboarding.
 */
object PendingSlice {
    private const val TAG = "PendingSlice"
    const val FILE_NAME = "pending_restore.zip"
    private const val APPLYING_NAME = "pending_restore.applying"

    @Volatile
    private var applying: Deferred<Unit>? = null

    internal fun file(root: Path) = Path(root, FILE_NAME)

    /** Starts applying what waits in [root], if anything does. */
    internal fun start(
        root: Path,
        scope: CoroutineScope,
        apply: suspend (Path) -> Unit,
    ) {
        if (!SystemFileSystem.exists(file(root)) && !SystemFileSystem.exists(Path(root, APPLYING_NAME))) return
        applying =
            scope.async {
                val claimed = claim(root) ?: return@async
                try {
                    apply(claimed)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Could not apply the pending slice", e)
                } finally {
                    SystemFileSystem.delete(claimed, mustExist = false)
                }
            }
    }

    /** Returns once a pending slice, if one was started, has been applied or has failed. */
    suspend fun await() {
        runCatching { applying?.await() }.onFailure { Log.e(TAG, "Could not apply the pending slice", it) }
    }

    /**
     * Moves the waiting file aside before it is applied. A file already moved aside means a run died while applying
     * it, so it is dropped rather than retried into the same crash.
     */
    internal fun claim(root: Path): Path? {
        val claimed = Path(root, APPLYING_NAME)
        if (SystemFileSystem.exists(claimed)) {
            Log.w(TAG, "Dropping a pending slice that was being applied when the app stopped")
            SystemFileSystem.delete(claimed, mustExist = false)
        }
        // java.io.File.renameTo: false when there is nothing to move.
        return claimed.takeIf { runCatching { SystemFileSystem.atomicMove(file(root), it) }.isSuccess }
    }
}
