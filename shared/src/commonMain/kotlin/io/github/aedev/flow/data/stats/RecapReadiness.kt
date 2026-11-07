package io.github.aedev.flow.data.stats

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.aedev.flow.data.local.safePreferencesDataStore
import io.github.aedev.flow.data.profile.StoreScope
import io.github.aedev.flow.data.recommendation.music.MusicBrainEngine
import kotlinx.coroutines.flow.first
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.minusMonth
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.yearMonth
import kotlin.time.Clock

private val recapPreferences: DataStore<Preferences> by safePreferencesDataStore(name = "flow_recap", scope = StoreScope.PROFILE_LIBRARY)
private val LAST_SHOWN = stringPreferencesKey("last_shown_month")

/**
 * Whether a month or a year has just closed with enough in it to be worth a recap, checked only when a screen
 * that offers it opens. No job, alarm or notification ever runs for it.
 */
class RecapReadiness
    constructor(
        private val videoStats: VideoStatsRecorder,
        private val musicBrain: MusicBrainEngine,
    ) {
        /**
         * The recap waiting to be offered: in January last year's, when it held enough; otherwise the
         * month that just closed. Null when nothing is waiting or it was already opened or dismissed.
         */
        suspend fun readyPeriod(now: YearMonth = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.yearMonth): RecapPeriod? {
            val shown = recapPreferences.data.first()[LAST_SHOWN]
            if (now.month.number == JANUARY) {
                val year = now.year - 1
                val activity = (1..MONTHS_PER_YEAR).sumOf { activityIn(LedgerTime.monthKey(YearMonth(year, it))) }
                if (shown != year.toString() && activity >= MIN_YEAR_ACTIVITY) return RecapPeriod.Year(year)
            }
            val closed = now.minusMonth()
            val key = LedgerTime.monthKey(closed)
            if (shown == key) return null
            return RecapPeriod.Month(closed).takeIf { activityIn(key) >= MIN_ACTIVITY }
        }

        suspend fun markShown(period: RecapPeriod) {
            val key =
                when (period) {
                    is RecapPeriod.Month -> LedgerTime.monthKey(period.month)
                    is RecapPeriod.Year -> period.year.toString()
                    RecapPeriod.AllTime -> return
                }
            recapPreferences.edit { it[LAST_SHOWN] = key }
        }

        private suspend fun activityIn(monthKey: String): Int = videoStats.monthViews(monthKey) + musicBrain.monthPlays(monthKey)

        private companion object {
            const val MIN_ACTIVITY = 20
            const val MIN_YEAR_ACTIVITY = 100
            const val JANUARY = 1
            const val MONTHS_PER_YEAR = 12
        }
    }
