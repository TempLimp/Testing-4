package io.github.aedev.flow.player.sabr.integration

import io.github.aedev.flow.innertube.models.YouTubeClient
import io.github.aedev.flow.player.resolver.LocalHlsProxy
import io.github.aedev.flow.player.resolver.MediaSource
import io.github.aedev.flow.player.resolver.PlatformCodecSupport
import io.github.aedev.flow.player.resolver.PlaybackEngineKind
import io.github.aedev.flow.player.resolver.decodes
import io.github.aedev.flow.player.resolver.engineFor
import io.github.aedev.flow.player.sabr.core.SabrCpn
import io.github.aedev.flow.player.sabr.core.SabrSessionState
import io.github.aedev.flow.player.sabr.core.SabrStreamController
import io.github.aedev.flow.player.sabr.network.SabrDataSource
import io.github.aedev.flow.player.stream.InnerTubeVideoStreamExtractor
import io.github.aedev.flow.player.stream.VideoCodecUtils
import io.github.aedev.flow.utils.Log
import kotlin.time.Clock

/**
 * Upstream's factory, with ExoPlayer's half replaced: the session is built exactly as upstream builds it, and its media
 * goes to [LocalHlsProxy] ([SabrProxySession]) instead of a `MergingMediaSource` of two progressive pipes. The source
 * comes back for the engine that decodes the selected formats ([PlatformCodecSupport]): AVPlayer gets an HLS master,
 * the bundled engine each track as a stream it reads in order.
 */
object SabrMediaSourceFactory {
    private const val TAG = "SabrMediaSrcFactory"

    /**
     * Null when neither engine decodes the session's formats together. [onError] is the orchestrator's, set before the
     * session starts (upstream's MediaLoader sets it before `start()`), with the proxy session it belongs to.
     */
    suspend fun create(
        info: SabrStreamInfo,
        videoId: String,
        durationMs: Long,
        startPositionMs: Long = 0L,
        mediaId: String = videoId,
        onError: ((session: SabrProxySession, code: Int, message: String, recoverable: Boolean) -> Unit)? = null,
    ): SabrMediaSourceResult? {
        val engine = engineFor(info) ?: return null.also {
            Log.w(TAG, "No engine decodes SABR video ${info.videoMimeType} with audio ${info.audioMimeType}")
        }
        val sessionState =
            SabrSessionState().apply {
                this.streamingUrl = info.streamingUrl
                this.videoId = videoId
                this.selectedAudioItag = info.audioItag
                this.selectedAudioLmt = info.audioLmt
                this.selectedVideoItag = info.videoItag
                this.selectedVideoLmt = info.videoLmt
                this.audioTrackId = info.audioTrackId
                this.selectedAudioXtags = info.audioXtags
                this.selectedVideoXtags = info.videoXtags
                // stickyResolution = the user's explicit pick (0 in auto); selectedVideoHeight is
                // the actual chosen format height, used to floor sticky_resolution so auto mode
                // still asks the server for full quality instead of dropping to 360p.
                this.stickyResolution = info.targetHeight
                this.selectedVideoHeight = info.videoHeight
                this.playheadPositionMs = startPositionMs
                this.poToken = info.poToken
                this.visitorId = info.visitorId
                this.ustreamerConfig = info.ustreamerConfig
                this.durationMs = durationMs
                this.clientNameId = info.clientNameId
                this.clientVersion = info.clientVersion.ifEmpty { YouTubeClient.WEB.clientVersion }
                // A real web streamer_context carries no OS fields — the browser client reports
                // clientName/version + hl/gl only. Sending osName/osVersion made our streaming
                // request fingerprint-inconsistent with the player response, a RELOAD_PLAYER_RESPONSE
                // trigger.
                this.osName = ""
                this.osVersion = ""
                this.cpn = info.cpn.ifEmpty(SabrCpn::generate)
            }
        if (startPositionMs > 0) sessionState.lastSeekAtMs = Clock.System.now().toEpochMilliseconds()

        // The GVS/SABR request must be made as the same client that minted the PoToken, so the
        // user-agent comes from the session rather than being assumed to be WEB.
        val userAgent = info.clientUserAgent.ifEmpty { YouTubeClient.USER_AGENT_WEB }
        val dataSource = SabrDataSource(userAgent)
        val controller = SabrStreamController(dataSource, sessionState)
        val reloadHeight = info.targetHeight.takeIf { it > 0 } ?: info.videoHeight
        val reloadCodec = VideoCodecUtils.codecKeyFromMimeType(info.videoMimeType)
        // Keep the content-playback nonce stable across reloads — a fresh cpn each reload reads
        // as a new playback session and re-triggers the server's RELOAD_PLAYER_RESPONSE demand.
        val sessionCpn = sessionState.cpn
        val reloadClient = SabrClientIdentity.sabrClientFor(info.clientNameId)
        val orchestrator =
            SabrOrchestrator(controller) { event ->
                InnerTubeVideoStreamExtractor.resolveSabrDownload(
                    videoId = videoId,
                    targetHeight = reloadHeight,
                    preferredCodec = reloadCodec,
                    reloadToken = event.reloadToken,
                    cpn = sessionCpn.ifEmpty(SabrCpn::generate),
                    client = reloadClient,
                )
            }

        val session =
            SabrProxySession(
                orchestrator = orchestrator,
                events = controller.events,
                tracks =
                    listOf(
                        SabrProxySession.Track(info.videoItag, isAudio = false, mimeType = info.videoMimeType, height = info.videoHeight),
                        SabrProxySession.Track(info.audioItag, isAudio = true, mimeType = info.audioMimeType),
                    ),
                startPositionMs = startPositionMs,
                durationMs = durationMs,
            )
        onError?.let { report -> orchestrator.onError = { code, message, recoverable -> report(session, code, message, recoverable) } }
        val mediaSource = LocalHlsProxy.publishSabr(session, engine, mediaId)
        // The proxy serves the media itself, so the orchestrator's ExoPlayer pipes would only fill: closed, they take nothing.
        orchestrator.start()
        orchestrator.audioBuffer.close()
        orchestrator.videoBuffer.close()

        Log.d(
            TAG,
            "Created SABR MediaSource: video=$videoId on $engine, " +
                "audioItag=${info.audioItag} (${info.audioMimeType}), videoItag=${info.videoItag} (${info.videoMimeType}), " +
                "startPos=${startPositionMs}ms",
        )

        return SabrMediaSourceResult(
            mediaSource = mediaSource,
            orchestrator = orchestrator,
            session = session,
        )
    }

    /** The engine that decodes both of the session's formats, by the rules every other stream follows: the video decides. */
    fun engineFor(info: SabrStreamInfo): PlaybackEngineKind? {
        val engine = PlatformCodecSupport.engineFor(info.videoMimeType.substringBefore(';'), VideoCodecUtils.codecStringFromMimeType(info.videoMimeType)) ?: return null
        return engine.takeIf { decodes(it, info.audioMimeType.substringBefore(';'), VideoCodecUtils.codecStringFromMimeType(info.audioMimeType), PlatformCodecSupport.av1) }
    }
}

data class SabrMediaSourceResult(
    val mediaSource: MediaSource,
    val orchestrator: SabrOrchestrator,
    /** What the proxy serves; [SabrProxySession.close] ends the session with the orchestrator. */
    val session: SabrProxySession,
)
