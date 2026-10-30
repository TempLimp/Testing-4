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

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
