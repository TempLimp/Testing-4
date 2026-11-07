package io.github.aedev.flow.data.profile

/**
 * Background work that belongs to the profile that queued it. It carries the profile in its input and in a tag, so
 * a switch can cancel it first, and a run that still outlives a switch does nothing in the next profile.
 *
 * Upstream also builds WorkManager's input data here, checks a running worker and cancels the profile's work; those
 * come with the background work they serve (S18, on BGTaskScheduler).
 */
object ProfileWork {
    const val KEY_PROFILE_ID = "flow_profile_id"
    private const val TAG_PREFIX = "profile:"

    fun tag(): String = TAG_PREFIX + ActiveProfile.get().id

    /** True when existing work with [tags] was queued by a profile other than the running one. */
    fun queuedByOther(tags: Set<String>): Boolean = queuedByOther(tags, ActiveProfile.get())

    // Work queued before profiles existed carries no profile; Default was the only one then.
    internal fun belongs(
        stamped: String?,
        running: RunningProfile,
    ): Boolean = stamped?.let { it == running.id } ?: running.isDefault

    internal fun queuedByOther(
        tags: Set<String>,
        running: RunningProfile,
    ): Boolean = !belongs(tags.firstOrNull { it.startsWith(TAG_PREFIX) }?.removePrefix(TAG_PREFIX), running)
}
