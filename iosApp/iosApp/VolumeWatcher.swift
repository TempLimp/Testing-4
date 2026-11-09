import AVFAudio
import Shared

/// AVAudioSession's volume can be watched only by key-value observing, which Kotlin/Native cannot override (S11).
enum VolumeWatcher {
    private static var observation: NSKeyValueObservation?

    static func install() {
        observation = AVAudioSession.sharedInstance().observe(\.outputVolume) { session, _ in
            let volume = session.outputVolume
            DispatchQueue.main.async { SystemIntegration.shared.systemVolumeChanged(volume: volume) }
        }
    }
}
