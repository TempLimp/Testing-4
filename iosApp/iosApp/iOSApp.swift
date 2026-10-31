import Shared
import SwiftUI

@main
struct iOSApp: App {
    init() {
        // The bundled FFmpeg + dav1d engine (S04b) for what AVPlayer cannot play; Kotlin opens it through PlaybackEngines.
        PlaybackEngines.shared.bundled = { BundledEngine() }
        #if PLAYBACK_GATE
        // The S04 playback gate build CI makes for a phone (ipa.yml): runs the gate on every launch.
        PlaybackGateKt.startPlaybackGateIfRequested(always: true)
        #elseif DEBUG
        // The S04 playback gate: runs only with the -flowPlaybackGate launch argument (CI).
        PlaybackGateKt.startPlaybackGateIfRequested(always: false)
        #endif
    }

    var body: some Scene {
        WindowGroup {
            // Edge to edge like upstream: Compose reads the insets itself.
            ComposeView().ignoresSafeArea()
        }
    }
}

// SwiftUI's own context type, spelled out: `Context` alone now names the InnerTube request context Shared exports.
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: UIViewControllerRepresentableContext<ComposeView>) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: UIViewControllerRepresentableContext<ComposeView>) {}
}
