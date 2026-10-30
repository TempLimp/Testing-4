package io.github.aedev.flow.player.stream

import io.github.aedev.flow.player.error.StreamDenialClassifier
import io.github.aedev.flow.player.error.StreamDenialKind
import io.github.aedev.flow.utils.potoken.WebPoTokenSession
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlin.time.Clock

/**
 * Remembers which InnerTube clients GVS is currently refusing, so the ladder stops restarting at
 * one: a 403 arrives on a URL that has already been discarded, so without this nothing survives the
 * re-extraction and every video plays for a minute and stalls.
 *
 * Keyed by `clientName`, which is what the failing URL's `c=` parameter reports. Entries lapse on a
 * timer rather than a connectivity callback, which would cost battery in every session to serve a
 * minority failure.
 */
open class ClientGateRegistry(
    private val ttlMs: Long,
    private val clockMs: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val onGated: (clientName: String) -> Unit = {},
) {
    // One lock for both maps stands in for ConcurrentHashMap's atomic merge and conditional remove.
    private val lock = SynchronizedObject()
    private val gatedUntilMs = HashMap<String, Long>()
    private val refusalStrikes = HashMap<String, Int>()

    fun reportGated(clientName: String?) {
        val key = clientName?.takeIf { it.isNotBlank() }?.uppercase() ?: return
        synchronized(lock) { gatedUntilMs[key] = clockMs() + ttlMs }
        onGated(key)
    }

    /**
     * GVS took the client's PO Token and refused it. Demoted on the second strike, because one
     * refusal can be a cold attestation the next mint fixes and the first strike would drop the
     * only client whose token the app can mint at all.
     *
     * @return true when this strike demoted the client.
     */
    fun reportRefused(clientName: String?): Boolean {
        val key = clientName?.takeIf { it.isNotBlank() }?.uppercase() ?: return false
        return synchronized(lock) {
            val strikes = (refusalStrikes[key] ?: 0) + 1
            refusalStrikes[key] = strikes
            if (strikes < REFUSALS_BEFORE_DEMOTION) return@synchronized false
            refusalStrikes.remove(key)
            gatedUntilMs[key] = clockMs() + ttlMs
            true
        }
    }

    /** Records what a refused googlevideo [url] says about the client that minted it. */
    fun reportDenied(url: String?): StreamDenialKind {
        val kind = StreamDenialClassifier.classify(url, nowSeconds = clockMs() / 1000)
        val client = StreamDenialClassifier.clientOf(url)
        when (kind) {
            StreamDenialKind.ATTESTATION_GATED -> reportGated(client)
            StreamDenialKind.TOKEN_REJECTED -> reportRefused(client)
            StreamDenialKind.URL_EXPIRED, StreamDenialKind.UNKNOWN -> Unit
        }
        return kind
    }

    fun isGated(clientName: String?): Boolean {
        val key = clientName?.takeIf { it.isNotBlank() }?.uppercase() ?: return false
        return synchronized(lock) {
            val until = gatedUntilMs[key] ?: return@synchronized false
            if (clockMs() >= until) {
                gatedUntilMs.remove(key)
                return@synchronized false
            }
            true
        }
    }

    /** The clients currently demoted, for diagnostics. Lapsed entries are dropped on the way out. */
    fun gatedClients(): Set<String> {
        val now = clockMs()
        return synchronized(lock) {
            gatedUntilMs.entries.removeAll { it.value <= now }
            gatedUntilMs.keys.toSet()
        }
    }

    fun clear() {
        synchronized(lock) {
            gatedUntilMs.clear()
            refusalStrikes.clear()
        }
    }

    private companion object {
        const val REFUSALS_BEFORE_DEMOTION = 2
    }
}

/**
 * The process-wide registry. Deliberately not persisted: a gate is a property of the current
 * network and visitor identity, and a stale one written to disk would demote a working client for
 * a user who has since moved networks.
 */
object ClientGateTracker : ClientGateRegistry(
    ttlMs = 30 * 60_000L,
    onGated = { client -> if (client in VISITOR_WALLED_CLIENTS) WebPoTokenSession.rerollAfterWall(client) },
)

/**
 * The app clients GVS walls per visitor rather than per client (#921): a fresh visitor is served
 * on them again, so a gate on one of these replaces the identity as well as demoting the client.
 */
private val VISITOR_WALLED_CLIENTS = setOf("VISIONOS", "ANDROID_VR")
