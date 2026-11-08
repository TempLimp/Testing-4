package io.github.aedev.flow.player.error

/** What the diagnostics report says about the device and the app build, in place of android.os.Build and PackageInfo. */
internal class DiagnosticsDevice(
    val model: String,
    val lines: List<Pair<String, String>>,
    val packageName: String,
    val build: String,
)

internal expect fun diagnosticsDevice(): DiagnosticsDevice

/** ClipboardManager.setPrimaryClip(ClipData.newPlainText(label, text)); throws where there is no clipboard. */
internal expect fun copyPlainText(
    label: String,
    text: String,
)
