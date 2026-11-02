package io.github.aedev.flow.player.sabr

import io.github.aedev.flow.player.sabr.core.SabrRequestBuilder
import io.github.aedev.flow.player.sabr.core.SabrSessionState
import io.github.aedev.flow.player.sabr.proto.ProtobufReader
import io.github.aedev.flow.testing.assertThat
import kotlin.test.Test

/**
 * Guards the SABR `VideoPlaybackAbrRequest` wire format — specifically the fields that decide
 * playback quality on the server side. The regression these tests lock down: in auto mode the
 * builder used to emit `sticky_resolution = 0`, which the encoder then dropped entirely, so the
 * SABR server fell back to its lowest rung (~360p) no matter which format we preferred.
 */
class SabrRequestBuilderTest {

    private fun state(block: SabrSessionState.() -> Unit): SabrSessionState =
        SabrSessionState().apply {
            clientNameId = 1
            clientVersion = "2.20260101.00.00"
            osName = "Windows"
            osVersion = "10.0"
            ustreamerConfig = byteArrayOf(1, 2, 3)
            block()
        }

    private fun topLevel(bytes: ByteArray) = ProtobufReader(bytes).readAllFields()

    private fun clientAbrState(bytes: ByteArray): Map<Int, List<ProtobufReader.Field>> {
        val abrBytes = topLevel(bytes)[1]!!.first().asBytes()
        return ProtobufReader(abrBytes).readAllFields()
    }

    @Test
    fun autoModeSendsStickyResolutionDerivedFromTheSelectedVideoHeight() {
        val s = state {
            selectedVideoItag = 137
            selectedVideoHeight = 1080
            selectedAudioItag = 251
            stickyResolution = 0 // auto
        }
        val abr = clientAbrState(SabrRequestBuilder.buildInitialRequest(s))

        // field 21 must be present and equal the selected video height (the fix)
        assertThat(abr[21]?.first()?.asInt()).isEqualTo(1080)
        // field 16 (last_manual_selected_resolution) is only for an explicit user pick
        assertThat(abr[16]).isNull()
    }

    @Test
    fun lowQualityVideoIsFlooredTo360pInStickyResolution() {
        val s = state {
            selectedVideoItag = 133
            selectedVideoHeight = 240
            stickyResolution = 0
        }
        val abr = clientAbrState(SabrRequestBuilder.buildInitialRequest(s))
        assertThat(abr[21]?.first()?.asInt()).isEqualTo(360)
    }

    @Test
    fun userPinnedQualityDrivesBothStickyAndLastManualResolution() {
        val s = state {
            selectedVideoItag = 137
            selectedVideoHeight = 1080
            stickyResolution = 720
        }
        val abr = clientAbrState(SabrRequestBuilder.buildInitialRequest(s))
        assertThat(abr[21]?.first()?.asInt()).isEqualTo(720)
        assertThat(abr[16]?.first()?.asInt()).isEqualTo(720)
    }

    @Test
    fun playbackRateIsAlwaysPresentAsAFixed32Float() {
        val s = state {
            selectedVideoItag = 137
            selectedVideoHeight = 1080
            playbackRate = 1.0f
        }
        val abr = clientAbrState(SabrRequestBuilder.buildInitialRequest(s))
        val field35 = abr[35]?.first()
        assertThat(field35).isNotNull()
        assertThat(field35!!.wireType).isEqualTo(ProtobufReader.WIRE_FIXED32)
        assertThat(Float.fromBits(field35.varintValue.toInt())).isEqualTo(1.0f)
    }

    @Test
    fun streamerContextCarriesWebClientInfoWithHlAndGl() {
        val s = state {
            selectedVideoItag = 137
            selectedVideoHeight = 1080
        }
        val top = topLevel(SabrRequestBuilder.buildInitialRequest(s))
        val streamerCtx = ProtobufReader(top[19]!!.first().asBytes()).readAllFields()
        val clientInfo = ProtobufReader(streamerCtx[1]!!.first().asBytes()).readAllFields()

        assertThat(clientInfo[16]?.first()?.asInt()).isEqualTo(1) // WEB client id
        assertThat(clientInfo[21]?.first()?.asString()).isEqualTo("en-US") // hl
        assertThat(clientInfo[22]?.first()?.asString()).isEqualTo("US")    // gl
    }

    @Test
    fun ustreamerConfigIsSentAsTopLevelBytesField5() {
        val s = state {
            selectedVideoItag = 137
            selectedVideoHeight = 1080
        }
        val top = topLevel(SabrRequestBuilder.buildInitialRequest(s))
        assertThat(top[5]?.first()?.asBytes()).isEqualTo(byteArrayOf(1, 2, 3))
    }

    @Test
    fun preferredVideoAndAudioFormatIdsArePinnedInFields17And16() {
        val s = state {
            selectedVideoItag = 137
            selectedVideoHeight = 1080
            selectedAudioItag = 251
        }
        val top = topLevel(SabrRequestBuilder.buildInitialRequest(s))

        val prefVideo = ProtobufReader(top[17]!!.first().asBytes()).readAllFields()
        val prefAudio = ProtobufReader(top[16]!!.first().asBytes()).readAllFields()
        assertThat(prefVideo[1]?.first()?.asInt()).isEqualTo(137) // itag
        assertThat(prefAudio[1]?.first()?.asInt()).isEqualTo(251)
    }

    // Not upstream's: the init-segment fix (SabrRequestBuilder). Formats go into selected_format_ids (field 2) only once
    // the server has initialized them, or GVS sends no init segment; the preferred formats (16, 17) go from the start.
    @Test
    fun selectedFormatIdsWaitForTheServersInitializationMetadata() {
        val s = state {
            selectedVideoItag = 137
            selectedVideoHeight = 1080
            selectedAudioItag = 251
        }
        val initial = topLevel(SabrRequestBuilder.buildInitialRequest(s))
        assertThat(initial[2]).isNull()
        assertThat(initial[17]).isNotNull()
        assertThat(initial[16]).isNotNull()

        s.initializedFormats += setOf(137, 251)
        val followUp = topLevel(SabrRequestBuilder.buildFollowUpRequest(s))
        assertThat(followUp[2]?.map { ProtobufReader(it.asBytes()).readAllFields()[1]!!.first().asInt() }).containsExactly(137, 251)
    }
}
