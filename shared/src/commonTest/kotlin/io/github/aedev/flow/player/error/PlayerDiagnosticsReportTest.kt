package io.github.aedev.flow.player.error

import io.github.aedev.flow.BuildConfig
import io.github.aedev.flow.testing.assertThat
import kotlin.test.AfterTest
import kotlin.test.Test

/** The report a user pastes into a bug: device, app version, the player's state and the event log. */
class PlayerDiagnosticsReportTest {
    @AfterTest
    fun tearDown() = PlayerDiagnostics.clear()

    @Test
    fun theReportCarriesTheAppVersionAndTheLoggedEvents() {
        PlayerDiagnostics.clear()
        PlayerDiagnostics.logWarning("Proxy", "403 c=WEB itag=137")

        val report = PlayerDiagnostics.buildReport()

        assertThat(report).contains("FLOW PLAYER DIAGNOSTICS")
        assertThat(report).contains("Version   : ${BuildConfig.VERSION_NAME}")
        assertThat(report).contains("── Event Log (last 1 entries) ──")
        assertThat(report).contains("403 c=WEB itag=137")
        assertThat(report).contains("── Player State ───────────────────")
    }
}
