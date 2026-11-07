package io.github.aedev.flow.data.profile

// The jvm target runs tests only and has no web views, so no profile has web data there.
internal actual suspend fun deleteProfileWebData(id: String): Boolean = false
