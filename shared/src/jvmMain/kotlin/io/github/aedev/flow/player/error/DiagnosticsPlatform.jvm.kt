package io.github.aedev.flow.player.error

internal actual fun diagnosticsDevice(): DiagnosticsDevice =
    DiagnosticsDevice(
        model = System.getProperty("os.name").orEmpty(),
        lines =
            listOf(
                "System" to "${System.getProperty("os.name")} ${System.getProperty("os.version")}",
                "ABI" to System.getProperty("os.arch").orEmpty(),
            ),
        packageName = "io.github.aedev.flow",
        build = "",
    )

// The jvm target is for tests; it has no app clipboard.
internal actual fun copyPlainText(
    label: String,
    text: String,
): Unit = throw UnsupportedOperationException("no clipboard on the jvm target")
