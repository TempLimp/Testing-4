package io.github.aedev.flow.data.profile

import io.github.aedev.flow.notification.UpcomingReminder

/**
 * Queues again, once per process, the work a switch cancelled for the profile this process runs as. Its `reconcile`
 * queues premiere reminders, the scrobble flush and wellbeing alarms, so it comes with that background work (S18);
 * which reminders survive is decided here.
 */
class ProfileWorkReconciler private constructor() {
    companion object {
        /** A premiere this long past its start is over; a reminder for it would only arrive late. */
        private const val STALE_AFTER_MS = 6 * 60 * 60 * 1000L

        internal fun remindersToRestore(
            reminders: List<UpcomingReminder>,
            nowMs: Long,
        ): Pair<List<UpcomingReminder>, List<UpcomingReminder>> = reminders.partition { it.releaseTimeMs >= nowMs - STALE_AFTER_MS }
    }
}
