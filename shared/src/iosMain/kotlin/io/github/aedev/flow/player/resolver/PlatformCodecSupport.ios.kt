package io.github.aedev.flow.player.resolver

import platform.VideoToolbox.VTIsHardwareDecodeSupported

actual object PlatformCodecSupport {
    // kCMVideoCodecType_AV1, 'av01'.
    private const val CODEC_AV1 = 0x61763031u

    actual val av1: Boolean by lazy { VTIsHardwareDecodeSupported(CODEC_AV1) }
}
