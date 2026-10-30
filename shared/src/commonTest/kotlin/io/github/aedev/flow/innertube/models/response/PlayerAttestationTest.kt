package io.github.aedev.flow.innertube.models.response

import io.github.aedev.flow.testing.assertThat
import kotlinx.serialization.json.Json
import kotlin.test.Test

/**
 * The attestation block YouTube attaches to a player response, in the shape the probes captured
 * from every client on 2026-09-16.
 */
class PlayerAttestationTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun theChallengeAndItsSharedFlagAreRead() {
        val response =
            json.decodeFromString<PlayerResponse>(
                """
                {"responseContext":{"visitorData":null,"serviceTrackingParams":null},"playabilityStatus":{"status":"OK","reason":null},"playerConfig":null,
                 "streamingData":null,"videoDetails":null,"playbackTracking":null,
                 "attestation":{"playerAttestationRenderer":{
                   "challenge":"a=6&a2=1&b=nonce&c=1789553682&d=28&e=3xngArcFpek&hh=binding",
                   "useSharedChallenge":true}}}
                """.trimIndent(),
            )

        val renderer = response.attestation?.playerAttestationRenderer
        assertThat(renderer?.challenge).contains("e=3xngArcFpek")
        assertThat(renderer?.challenge).contains("d=28")
        assertThat(renderer?.useSharedChallenge).isTrue()
    }

    @Test
    fun aResponseWithoutAnAttestationBlockStillParses() {
        val response =
            json.decodeFromString<PlayerResponse>(
                """
                {"responseContext":{"visitorData":null,"serviceTrackingParams":null},"playabilityStatus":{"status":"OK","reason":null},"playerConfig":null,
                 "streamingData":null,"videoDetails":null,"playbackTracking":null}
                """.trimIndent(),
            )

        assertThat(response.attestation).isNull()
    }

    @Test
    fun theSharedFlagIsOptional() {
        val response =
            json.decodeFromString<PlayerResponse>(
                """
                {"responseContext":{"visitorData":null,"serviceTrackingParams":null},"playabilityStatus":{"status":"OK","reason":null},"playerConfig":null,
                 "streamingData":null,"videoDetails":null,"playbackTracking":null,
                 "attestation":{"playerAttestationRenderer":{"challenge":"a=6&e=vid"}}}
                """.trimIndent(),
            )

        assertThat(response.attestation?.playerAttestationRenderer?.useSharedChallenge).isNull()
    }
}
