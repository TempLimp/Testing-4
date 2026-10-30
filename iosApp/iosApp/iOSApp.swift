import Shared
import SwiftUI

@main
struct iOSApp: App {
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
