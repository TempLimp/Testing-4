package io.github.aedev.flow.player.sabr.core

import io.github.aedev.flow.player.sabr.proto.ClientAbrState
import io.github.aedev.flow.player.sabr.proto.ClientInfo
import io.github.aedev.flow.player.sabr.proto.StreamerContext
import io.github.aedev.flow.player.sabr.proto.VideoPlaybackAbrRequest
import io.github.aedev.flow.utils.Log
import kotlin.time.Clock

object SabrRequestBuilder {
    private const val TAG = "SabrRequestBuilder"

    private const val LOG_REQUEST_SEQUENCE_LIMIT = 3

    fun buildInitialRequest(state: SabrSessionState): ByteArray = buildRequest(state, isInitial = true)

    fun buildFollowUpRequest(state: SabrSessionState): ByteArray = buildRequest(state, isInitial = false)

    private fun buildRequest(
        state: SabrSessionState,
        isInitial: Boolean,
    ): ByteArray {
        state.requestSequence++

        val playheadMs = state.playheadPositionMs
        // selected_format_ids lists the formats the client already holds an init segment for. Upstream also listed
        // the preferred formats before the server had initialized any, and GVS then sent neither
        // FORMAT_INITIALIZATION_METADATA nor the init segment (measured on aqz-KE-bpKQ, 2026-11-02): media alone,
        // which no demuxer can open. Listed only once initialized, the first response carries both.
        val selected =
            listOfNotNull(
                state.selectedVideoFormatId.takeIf { state.selectedVideoItag > 0 && state.selectedVideoItag in state.initializedFormats },
                state.selectedAudioFormatId.takeIf { state.selectedAudioItag > 0 && state.selectedAudioItag in state.initializedFormats },
            )
        val buffered = if (isInitial) emptyList() else (state.videoBufferedRanges + state.audioBufferedRanges)
        val timeSinceSeekMs =
            if (state.lastSeekAtMs > 0) {
                (Clock.System.now().toEpochMilliseconds() - state.lastSeekAtMs).coerceAtLeast(0)
            } else {
                0L
            }

        val includeFollowUpState = playheadMs > 0 || buffered.isNotEmpty()

        val effectiveResolution =
            (if (state.stickyResolution > 0) state.stickyResolution else state.selectedVideoHeight)
                .coerceAtLeast(360)

        if (state.requestSequence <= LOG_REQUEST_SEQUENCE_LIMIT) {
            Log.d(
                TAG,
                "req#${state.requestSequence} initial=$isInitial " +
                    "selectedItags=${selected.map { it.itag }} initialized=${state.initializedFormats} " +
                    "audioXtags='${state.selectedAudioXtags}' " +
                    "prefAudio=${state.selectedAudioItag}/${state.selectedAudioLmt} " +
                    "prefVideo=${state.selectedVideoItag}/${state.selectedVideoLmt} " +
                    "track='${state.audioTrackId}' enabledTracks=${state.enabledTrackTypes} " +
                    "playhead=${playheadMs}ms buffered=${buffered.size} sticky=$effectiveResolution",
            )
        }

        val request =
            VideoPlaybackAbrRequest(
                clientAbrState =
                    ClientAbrState(
                        playerTimeMs = playheadMs,
                        bandwidthEstimateBps = if (includeFollowUpState) state.estimatedBandwidthBps else 0L,
                        viewportWidthPx = if (includeFollowUpState) state.screenWidthPixels else 0,
                        viewportHeightPx = if (includeFollowUpState) state.screenHeightPixels else 0,
                        lastManualSelectedResolution = if (state.stickyResolution > 0) state.stickyResolution else 0,
                        stickyResolution = effectiveResolution,
                        timeSinceLastSeekMs = timeSinceSeekMs,
                        visibility = state.visibility,
                        playbackRate = state.playbackRate,
                        enabledTrackTypesBitfield = state.enabledTrackTypes,
                        audioTrackId = state.audioTrackId,
                    ),
                selectedFormatIds = selected,
                bufferedRanges = buffered,
                playerTimeMs = playheadMs,
                videoPlaybackUstreamerConfig = state.ustreamerConfig,
                preferredAudioFormatIds = listOfNotNull(state.selectedAudioFormatId.takeIf { state.selectedAudioItag > 0 }),
                preferredVideoFormatIds = listOfNotNull(state.selectedVideoFormatId.takeIf { state.selectedVideoItag > 0 }),
                streamerContext =
                    StreamerContext(
                        clientInfo =
                            ClientInfo(
                                clientName = state.clientNameId,
                                clientVersion = state.clientVersion,
                                osName = state.osName,
                                osVersion = state.osVersion,
                            ),
                        poToken = state.poTokenBytes(),
                        playbackCookie = state.playbackCookie,
                        sabrContexts = state.activeSabrContexts(),
                        unsentSabrContextTypes = state.unsentSabrContextTypes(),
                    ),
            )
        return request.encode()
    }
}
