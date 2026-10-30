package io.github.aedev.flow.innertube.models.body

import io.github.aedev.flow.testing.assertThat
import io.github.aedev.flow.innertube.models.YouTubeClient
import io.github.aedev.flow.innertube.models.YouTubeLocale
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test

class NextBodyTest {
    private val json = Json { encodeDefaults = true }

    @Test
    fun aWatchNextRequestAsksPastTheContentWarning() {
        val body =
            NextBody(
                context = YouTubeClient.WEB.toContext(YouTubeLocale.EXTRACTION, null, null),
                videoId = "e59OqORcTC0",
                playlistId = null,
                playlistSetVideoId = null,
                index = null,
                params = null,
                continuation = null,
            )

        val sent = json.parseToJsonElement(json.encodeToString(NextBody.serializer(), body)).jsonObject

        assertThat(sent.getValue("contentCheckOk").jsonPrimitive.boolean).isTrue()
        assertThat(sent.getValue("racyCheckOk").jsonPrimitive.boolean).isTrue()
    }
}
