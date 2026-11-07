package io.github.aedev.flow.data.profile

/**
 * Web views keep cookies and storage per data store, and they carry the YouTube visitor identity BotGuard grades, so
 * each profile has its own. Android gives each profile a WebView data folder from Android 9; iOS 17, the minimum,
 * gives each its own WKWebsiteDataStore by identifier, so a switch never has to clear a shared one.
 */
object ProfileWebView {
    /** What [ProfileFolders.delete] reports for a removed data store. */
    internal const val WEB_DATA_NAME = "webkit_data_store"

    val clearedAtSwitch: Boolean get() = false

    /** The running profile's data store identifier: null for Default, which keeps the system's default store. */
    fun dataStoreId(): String? = dataStoreId(ActiveProfile.get())

    internal fun dataStoreId(profile: RunningProfile): String? {
        if (profile.isDefault) return null
        require(isSafeProfileId(profile.id)) { "Unsafe profile id" }
        return profile.id
    }
}

/** Removes [id]'s web data store; false when it had none or it could not be removed. */
internal expect suspend fun deleteProfileWebData(id: String): Boolean
