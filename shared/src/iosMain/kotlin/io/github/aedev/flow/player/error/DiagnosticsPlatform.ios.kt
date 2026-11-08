package io.github.aedev.flow.player.error

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import platform.Foundation.NSBundle
import platform.UIKit.UIDevice
import platform.UIKit.UIPasteboard
import platform.posix.uname
import platform.posix.utsname

@OptIn(ExperimentalForeignApi::class)
internal actual fun diagnosticsDevice(): DiagnosticsDevice {
    // The model identifier (iPhone15,2), as Build.MODEL names the hardware; UIDevice.model only says "iPhone".
    val machine = memScoped { alloc<utsname>().also { uname(it.ptr) }.machine.toKString() }
    val device = UIDevice.currentDevice
    val info = NSBundle.mainBundle.infoDictionary
    return DiagnosticsDevice(
        model = machine,
        lines =
            listOf(
                "Manufacturer" to "Apple",
                "Model" to "$machine (${device.model})",
                "ABI" to "arm64",
                "System" to "${device.systemName} ${device.systemVersion}",
            ),
        packageName = NSBundle.mainBundle.bundleIdentifier ?: "",
        build = info?.get("CFBundleVersion")?.toString() ?: "",
    )
}

internal actual fun copyPlainText(
    label: String,
    text: String,
) {
    UIPasteboard.generalPasteboard.string = text
}
