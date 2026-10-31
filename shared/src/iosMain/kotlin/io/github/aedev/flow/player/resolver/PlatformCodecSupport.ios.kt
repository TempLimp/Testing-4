package io.github.aedev.flow.player.resolver

import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionPortAirPlay
import platform.AVFAudio.AVAudioSessionPortDescription
import platform.AVFAudio.currentRoute
import platform.VideoToolbox.VTIsHardwareDecodeSupported

actual object PlatformCodecSupport {
    // kCMVideoCodecType_AV1, 'av01'.
    private const val CODEC_AV1 = 0x61763031u

    actual val av1: Boolean by lazy { VTIsHardwareDecodeSupported(CODEC_AV1) }

    // Read at each resolution: the route changes while the app runs.
    actual val airPlayActive: Boolean
        get() =
            AVAudioSession.sharedInstance().currentRoute.outputs.any {
                (it as? AVAudioSessionPortDescription)?.portType == AVAudioSessionPortAirPlay
            }
}
