//==================================================================================================
//This implementation was based on metrolist's (https://github.com/MetrolistGroup/Metrolist)
//==================================================================================================

package io.github.aedev.flow.data.lyrics.kugou.models

import kotlinx.serialization.Serializable
@Serializable
data class DownloadLyricsResponse(
    val content: String = ""
)
