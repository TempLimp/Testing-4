# Flow for iOS

An iOS port of [Flow](https://github.com/Flow-Tube/Flow), the privacy-respecting YouTube and YouTube Music client with the on-device FlowNeuro recommendation engine, by A-EDev.

Work in progress. The port reuses Flow's Kotlin code and Compose UI through Kotlin Multiplatform; iOS-only parts (player, Picture-in-Picture, Now Playing, widgets, CarPlay) are written in Swift.

- [PORTING_PLAN.md](PORTING_PLAN.md): decisions, sessions, definition of done, progress log.
- [PARITY.md](PARITY.md): every upstream package, feature, route, screen and widget, and its port status.
- `upstream/`: the Android source at the pinned commit (git submodule, read-only reference).

## Build

- Shared code on any OS with JDK 21: `./gradlew :shared:jvmTest`
- iOS app on macOS with Xcode and [XcodeGen](https://github.com/yonaskolb/XcodeGen): `xcodegen generate --spec iosApp/project.yml`, then open `iosApp/iosApp.xcodeproj`.
- CI (`.github/workflows/ci.yml`) runs both and uploads a simulator launch screenshot.

## License

GPL-3.0, same as upstream. Flow is Copyright © 2025-2026 A-EDev. See [LICENSE](LICENSE).
