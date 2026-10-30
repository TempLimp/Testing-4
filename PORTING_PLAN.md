# Flow for iOS — porting plan

Port of [Flow-Tube/Flow](https://github.com/Flow-Tube/Flow) (Android, Kotlin, Jetpack Compose) to iOS.
Every session reads this file first, does exactly one session from the list, and updates the log at the bottom.

## Decisions (fixed — do not re-litigate)

| Topic | Decision |
|---|---|
| Approach | Kotlin Multiplatform + Compose Multiplatform. Upstream Kotlin moves into `shared/` with the **same package names** (`io.github.aedev.flow.*`) so upstream diffs stay mechanical. Swift only for what iOS owns (player, PiP, Now Playing, widgets, CarPlay, extensions). |
| Upstream pin | `77ae9d9e3e878b5867489239279f60c591719fc8` (2026-10-29), vendored read-only as git submodule `upstream/`. Bumping it is its own task. |
| Build/verify | No Mac. Windows: JDK 21 + `jvm()` target for compiling and testing `commonMain` locally. GitHub Actions `macos-latest`: iOS framework, `xcodebuild`, simulator tests, screenshots. |
| Repo | `TempLimp/Testing-4`, **private**. macOS minutes count 10x against the plan's included minutes (about 200 macOS minutes a month on GitHub Free), so the iOS job runs only on pushes to `main` and manual dispatch, never on PRs. Push once per finished session step, not per commit. |
| Toolchain | Kotlin 2.4.21, Compose Multiplatform 1.13.0-beta01 (material3 1.5.0-beta01), Gradle 9.6.1 (wrapper generated from the checksum-verified distribution), JDK 21, XcodeGen (`iosApp/project.yml`; `.xcodeproj` is generated, not committed). |
| App identity | Bundle id `io.github.templimp.flow`, display name "Flow", iOS 17.0 minimum (interactive widgets need 17), iPhone + iPad. |
| Skipped | Android TV UI (`ui/tv`), in-app APK updater (`data/update`, `ui/screens/update`), Discord Rich Presence (`discord`), nightly flavor, `benchmark/` module, baseline profiles. Base is the **foss** flavor. |
| License | GPLv3, same as upstream. Keep upstream copyright (A-EDev) and acknowledgments. |
| Distribution | Sideload (AltStore/SideStore/Xcode). App Store is impossible (guideline 5.2.3, GPLv3). |

## Size (upstream `app/src/main`, measured)

1,994 Kotlin files / 275,745 lines; 647 Compose files; 4,028 strings x 33 locales; 20 Room entities, 18 DAOs; 762 test files.
UI is ~140k lines (`ui/screens` 73k, `ui/components` 65k) and ports near-verbatim; the Android-bound remainder (player, service, notification, widget, localmedia, WebView code) is rewritten against iOS.

## Library mapping

| Upstream (Android) | Port | Notes |
|---|---|---|
| Jetpack Compose + Material 3 1.5.0-alpha26 (Expressive) | Compose Multiplatform material3 | S01 verifies which Expressive APIs exist; any gap is listed in PARITY.md, never silently swapped |
| material-icons-extended | CMP `material-icons-extended` or the used icons copied as `ImageVector` | S02 |
| Navigation Compose, Lifecycle/ViewModel, Paging 3 | JetBrains/AndroidX KMP artifacts | |
| Hilt / `getInstance()` singletons | Koin (`single {}` keeps one-instance identity) | no KSP |
| Room 2.8, DataStore Preferences | Room KMP + `sqlite-bundled`, DataStore KMP | schema identical to upstream (backups stay compatible) |
| OkHttp, Ktor client | Ktor client (Darwin engine on iOS, CIO on jvm) | |
| Gson DTOs | kotlinx.serialization | |
| java.time / SimpleDateFormat / Calendar | kotlinx-datetime | |
| Coil 3 | Coil 3 KMP + `coil-network-ktor3` | |
| palette-ktx | KMP color quantizer (decide in S13) | |
| NewPipeExtractor (57 files) | Flow's own InnerTube client; pieces it still needs are ported to Kotlin | JVM-only library |
| WebView (cipher, PoToken/BotGuard) | JavaScriptCore / WKWebView via `iosMain` | |
| Media3 / ExoPlayer, MediaSession | AVFoundation (`AVPlayer`), `MPNowPlayingInfoCenter`, `MPRemoteCommandCenter`, AVKit PiP | Swift, behind the upstream player-manager interfaces |
| audiofx Equalizer/Visualizer | `MTAudioProcessingTap` + Accelerate (vDSP) | |
| WorkManager | `BGTaskScheduler` | best-effort on iOS |
| Glance widgets (9) | WidgetKit + App Intents | needs App Group |
| Android Auto | CarPlay | needs Apple entitlement |
| DLNA (SSDP/UPnP) | ported over Network.framework; AirPlay via `AVRoutePickerView` | multicast entitlement |
| MediaStore local media | `MPMediaQuery` + Files import | iOS exposes less |
| Ktor server + NsdManager (LAN sync) | Ktor server on Kotlin/Native if supported, else Network.framework + Bonjour | verify in S20 |
| BouncyCastle/Conscrypt, kotlin-bip39 | cryptography-kotlin (CryptoKit provider); bip39 KMP or ported | verify in S20 |
| zxing + CameraX | AVFoundation metadata scanner + CoreImage QR generator | |
| brotli, re2j | dropped (NSURLSession decodes br; re2j unused upstream) | |

## Unavoidable platform differences (record each in PARITY.md as "iOS limit")

1. iOS won't let a third-party app claim `youtube.com` links. Use a Share Extension and the `flow://` scheme instead.
2. AVPlayer plays neither DASH nor WebM. Adaptive streams are served as local HLS (fMP4 byte ranges). Whether VP9/WebM plays (bundled decoder) or falls back to H.264/AV1 is decided at the **S04 gate**. AV1 hardware decoding exists only on A17 Pro / M-series and newer.
3. iOS has no public API to set system volume, so the volume gesture drives a hidden `MPVolumeView` slider.
4. iOS suspends background apps. Audio keeps playing (background audio mode), downloads use a background `URLSession`, and feed checks via `BGAppRefreshTask` run when iOS schedules them.
5. Widgets refresh on the WidgetKit budget, and their buttons are App Intents.
6. Free Apple IDs re-sign every 7 days. Widgets (App Group), CarPlay and multicast likely need a paid account (verify in S21).

## Definition of done (every session, no exceptions)

1. `gradlew :shared:jvmTest` passes locally on Windows. Every upstream unit test covering the ported code is ported, not dropped.
2. CI on `macos-latest` passes: iOS framework build, `xcodebuild` simulator build, `iosSimulatorArm64Test`. UI sessions also upload simulator screenshots as artifacts.
3. No `TODO()`, stub or fake on a shipped path. A known limit gets `// shortcut: <limit>, <when to upgrade>`.
4. Upstream behavior rules still apply where they make sense on iOS (AGENTS.md: hidden-layer animation gating, 1 Hz / refcounted 250 ms position cadence, one fetch per cause, bounded parallelism, no hardcoded colors or strings).
5. PARITY.md lines for this session are set to `done` with evidence (test name or screenshot), or `iOS limit` with the reason.
6. One commit per logical step, `type(scope): description`. The session log below is updated.

What CI cannot verify (real-device feel, CarPlay, widgets on a home screen) is written into the log as **unverified**, never claimed.

## Sessions

Order matters within a phase. Sessions marked ∥ can run in parallel on separate branches once their phase's prerequisites have landed.

### Phase 0 — Foundation
- **S01 Scaffold & CI.** Install JDK 21 locally. Create the GitHub repo. Set up the KMP project (`shared`: `commonMain`/`iosMain`/`jvmMain`; `iosApp` Xcode project), pin Kotlin/CMP versions compatible with each other, add the `upstream/` submodule and the CI workflow. Generate PARITY.md from upstream (every screen directory, nav route, settings key, README feature and widget). *Done:* an empty app launches in the CI simulator and the screenshot artifact exists.
- **S02 Theme, resources, codemod.** Port `ui/theme` (11 themes, typography, shapes, motion), the strings for all 33 locales to `composeResources`, drawables and fonts. Write the conversion script (`R.string`→`Res.string`, `hiltViewModel`→`koinViewModel`, `stringResource` imports, etc.) that every later session uses. *Done:* theme switcher demo plus a string-count check that all 4,028 keys and 33 locales are present.

### Phase 1 — Core (go/no-go)
- **S03 Networking + InnerTube.** `network`, `innertube/**`, `data/innertube`, `utils/protobuf`, `innertube/utils` (~18k lines). Gson goes to kotlinx.serialization. *Done:* ported tests pass, plus a live search/browse smoke test in CI.
- **S04 Stream extraction + playback gate.** `utils/cipher`, `utils/potoken`, `player/stream`, `player/resolver`, `player/quality`. JS runs through JavaScriptCore and BotGuard through WKWebView. Swift spike: a local HLS proxy feeding AVPlayer. *Gate:* a real video and a music track play in the CI simulator test (sound + video frames verified by player state and `AVPlayerItem.tracks`). If the gate fails, stop and redesign before any further session.
- **S05 SABR on iOS.** `player/sabr/**` (UMP, protobuf, routing), feeding AVPlayer through the S04 proxy. *Done:* a SABR-only format plays in the simulator test.

### Phase 2 — Data
- **S06 Persistence.** `data/local/**` (entities, DAOs, migrations, converters), DataStore preferences. *Done:* Room migration tests ported and passing, and the schema JSON is identical to upstream.
- **S07 ∥ Data layer A.** `data/video`, `data/shorts`, `data/subscriptions`, `data/search`, `data/paging`, `data/comments`, `data/transcript`, `data/playlist`, `data/feed`, `data/repository`.
- **S08 ∥ Data layer B.** `data/music`, `data/newmusic`, `data/lyrics`, `data/stats`, `data/engagement`, `data/notes`, `data/profile`, `data/scrobble`, `data/audio`, `data/model`, `data/download` (metadata only).
- **S09 ∥ FlowNeuro.** `data/recommendation/**` (~20k lines) plus the `NeuroBenchmarkTest`/`MusicBenchmarkTest` regression floors on jvm. *Done:* benchmark scores match upstream on the same fixtures.

### Phase 3 — Playback
- **S10 Video player.** `player/**` minus sabr/stream: a Swift `AVPlayer` manager behind the `EnhancedPlayerManager` contract. Covers quality switching, speed 0.25–2x, subtitles (size/color/background), chapters, SponsorBlock, DeArrow, RYD, resume, preload/cache, error recovery and music-video mode.
- **S11 System integration.** `AVAudioSession`, background playback, PiP, Now Playing + remote commands (replaces `service`, `notification`), AirPlay, brightness/volume/seek gestures.
- **S12 Music player.** `EnhancedMusicPlayerManager` parity: queue add/remove/reorder, shuffle/repeat, sleep timer, the 1 Hz + refcounted 250 ms cadence, EQ and visualizer via `MTAudioProcessingTap`.

### Phase 4 — UI (bulk is the S02 codemod plus Context→platform fixes)
- **S13 App shell + shared components.** `ui/*.kt` (root, navigation, MainActivity→`App()`), `ui/components/shared`, `ui/components/layout`, `ui/utils`, `ui/startup`.
- **S14 Video screens.** home, search, categories, channel, video player screen, comments/description sheets.
- **S15 ∥ Library screens.** shorts (AVPlayer pool replacing `ShortsPlayerPool`), subscriptions, history, library, downloads list, playlists, notes, notifications.
- **S16 ∥ Music screens.** music hub, artist, album, player sheet + mini player, lyrics, queue, equalizer, recap.
- **S17 ∥ Settings & the rest.** All settings pages, onboarding, crash screen, "Your Taste" dashboard, widget config, sync screens (UI only).

### Phase 5 — Platform features (all ∥ after Phase 4)
- **S18 Downloads + background.** A background `URLSession` and muxing with AVFoundation (format handling per the S04 decision). The new-upload notifications that ran on WorkManager move to `BGAppRefreshTask` and `UNUserNotificationCenter`.
- **S19 Import/export + media.** `data/backup`, `data/migration`, NewPipe import, recommendation-profile export/import, `data/localmedia` → Music library + Files import, Share Extension, `flow://` deep links.
- **S20 Device sync.** `sync/**`, `sync2/**`: LAN transport, Bonjour discovery, crypto, QR scanning and generation, bip39. *Done:* two simulators pair and sync in a CI test.
- **S21 Recognition, widgets, CarPlay, casting.** `data/recognition` (Shazam signature + `AVAudioEngine` mic capture), `widget/**` → 9 WidgetKit widgets, `service/auto` → CarPlay, `player/dlna`.

### Phase 6 — Ship
- **S22 Parity audit + release.** Screen-by-screen simulator screenshots against upstream's `Assets/*.jpeg` and a fresh Android capture. PARITY.md must have zero open lines. Locale check. Unsigned IPA artifact for sideloading. README with attribution.

Expect 22 sessions. S03, S07, S09, S10 and S13–S17 may each need a second sitting, so 22–30 in total. PARITY.md section 1 gives each session's upstream line count. The largest are S14 (~40k: split it into home/search/categories/channel, then the video player screen and its 94 `videoplayer` components), S16 (~30k), S13 and S17 (~26k each).

## Starting a session

Paste into a new session in this folder:

> Read PORTING_PLAN.md and PARITY.md. Do session SNN only, following the Definition of done. Port from `upstream/` at the pinned commit; keep package names. Update PARITY.md and the session log, commit, and push.

## Session log

| Session | Status | Branch / commit | Unverified / notes |
|---|---|---|---|
| S00 Analysis & plan | done | — | Upstream analysed at 77ae9d9. No code yet. |
