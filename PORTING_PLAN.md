# Flow for iOS — porting plan

Port of [Flow-Tube/Flow](https://github.com/Flow-Tube/Flow) (Android, Kotlin, Jetpack Compose) to iOS.
Every session reads this file first, does exactly one session from the list, and updates the log at the bottom.

## Decisions (fixed — do not re-litigate)

| Topic | Decision |
|---|---|
| Approach | Kotlin Multiplatform + Compose Multiplatform. Upstream Kotlin moves into `shared/` with the **same package names** (`io.github.aedev.flow.*`) so upstream diffs stay mechanical. Swift only for what iOS owns (player, PiP, Now Playing, widgets, CarPlay, extensions). |
| Upstream pin | `77ae9d9e3e878b5867489239279f60c591719fc8` (2026-10-29), vendored read-only as git submodule `upstream/`. Bumping it is its own task. |
| Build/verify | No Mac. Windows: JDK 21 + `jvm()` target for compiling and testing `commonMain` locally. GitHub Actions `macos-latest`: iOS framework, `xcodebuild`, simulator tests, screenshots. |
| Repo | `TempLimp/Testing-4`, **public** since 2026-10-31. While private, macOS minutes counted 10x and the free allowance ran out in S04, and GitHub stopped starting jobs. Public repos get standard runners free, macOS included. The iOS job still runs only on pushes to `main` and manual dispatch. Push once per finished session step, not per commit. |
| Toolchain | Kotlin 2.4.21, Compose Multiplatform 1.13.0-beta01 (material3 1.5.0-beta01), Gradle 9.6.1 (wrapper generated from the checksum-verified distribution), JDK 21, XcodeGen (`iosApp/project.yml`; `.xcodeproj` is generated, not committed). |
| App identity | Bundle id `io.github.templimp.flow`, display name "Flow", iOS 17.0 minimum (interactive widgets need 17), iPhone + iPad. |
| Skipped | Android TV UI (`ui/tv`), in-app APK updater (`data/update`, `ui/screens/update`), Discord Rich Presence (`discord`), nightly flavor, `benchmark/` module, baseline profiles. Base is the **foss** flavor. |
| License | GPLv3, same as upstream. Keep upstream copyright (A-EDev) and acknowledgments. |
| Distribution | Sideload (AltStore/SideStore/Xcode). App Store is impossible (guideline 5.2.3, GPLv3). |
| Adaptive streams on iOS (S04) | `player/resolver/LocalHlsProxy`: a loopback HTTP server (Ktor CIO, 127.0.0.1) serves each video as HLS: a master playlist (video rendition or ladder plus an audio group), media playlists written from the stream's sidx, and byte ranges forwarded to googlevideo. Chosen over an AVAssetResourceLoaderDelegate because a resource loader can answer HLS playlists and keys but not media segments, and SABR (S05) must feed AVPlayer segments through the same server. |
| VP9 / AV1 / Opus (S04, revised 2026-10-31) | Two engines. H.264 and AAC (and HEVC and AV1 where VideoToolbox decodes them in hardware) stay on AVPlayer through `LocalHlsProxy`, which keeps AirPlay, efficient hardware decode and the system player. VP9, AV1 without hardware decode, and Opus go to a bundled software engine: FFmpeg built minimal from pinned source in CI (matroska and mov demuxers; vp9 and opus decoders; an LGPL-only configuration) plus dav1d for AV1, rendering to `AVSampleBufferDisplayLayer` and `AVSampleBufferAudioRenderer` under an `AVSampleBufferRenderSynchronizer`. Which engine plays a stream follows upstream's own codec preference and quality logic (`VideoCodecUtils`, `QualityManager`), not a new iOS rule. The user chose the bundled decoder on 2026-10-31 over a 1080p cap, after the S04 iPhone gate (no AV1 hardware; YouTube offered 2160p and 1440p only as VP9 or AV1). Built in S04b. |
| JavaScript (S04) | YouTube's player script runs in JavaScriptCore (`utils/cipher/JsContext`), BotGuard in a WKWebView; the jvm test target uses GraalJS, never shipped. |

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
| NewPipeExtractor (57 files) | Flow's own InnerTube client; pieces it still needs are ported to Kotlin | JVM-only library. S04 ported the stream model (MediaFormat, Stream, VideoStream, AudioStream, ItagItem, enums) under NewPipe's own packages in commonMain. |
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
2. AVPlayer plays neither DASH nor WebM, nor VP9 or Opus in any container, nor AV1 without a hardware decoder (A17 Pro, M3 and newer). Adaptive H.264 and AAC (and HEVC and AV1 where hardware decodes them) are served to AVPlayer as local HLS (fMP4 byte ranges) by `LocalHlsProxy`, which keeps AirPlay, efficient hardware decode and the system player. VP9, AV1 without hardware decode and Opus go to the bundled FFmpeg + dav1d engine on `AVSampleBufferDisplayLayer` and `AVSampleBufferAudioRenderer` (S04b; the user chose it on 2026-10-31). Which engine plays a stream follows upstream's `VideoCodecUtils` and `QualityManager`.
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
- **S04b Bundled decoder engine + device gate.** Build FFmpeg (minimal and LGPL-only: matroska and mov demuxers, vp9 and opus decoders) and dav1d as an xcframework from pinned, checksummed sources in CI. A Swift engine behind the same player contract as AVPlayer, fed by the existing request policy (`GoogleVideoRequestPolicy`) and the proxy's byte ranges, rendering to `AVSampleBufferDisplayLayer` and `AVSampleBufferAudioRenderer` under an `AVSampleBufferRenderSynchronizer`: A/V sync, seeking, and PiP with the sample-buffer layer. While AirPlay video is active, AVPlayer plays H.264 instead. Which engine plays a stream follows upstream's `VideoCodecUtils` and `QualityManager`. *Gate (the user's iPhone):* VP9 2160p and 1440p, AV1 and Opus each play in sync, with dropped frames, CPU and thermal state measured; if the phone cannot sustain 2160p, record the highest rung it sustains, with the numbers. From here on there are two engines: S05 feeds both, and S10, S11, S12 and S18 handle both.
- **S05 SABR on iOS.** `player/sabr/**` (UMP, protobuf, routing), feeding both engines: AVPlayer through the S04 proxy, and the S04b bundled engine. *Done:* a SABR-only format plays in the simulator test.

### Phase 2 — Data
- **S06 Persistence.** `data/local/**` (entities, DAOs, migrations, converters), DataStore preferences. *Done:* Room migration tests ported and passing, and the schema JSON is identical to upstream.
- **S07 ∥ Data layer A.** `data/video`, `data/shorts`, `data/subscriptions`, `data/search`, `data/paging`, `data/comments`, `data/transcript`, `data/playlist`, `data/feed`, `data/repository`.
- **S08 ∥ Data layer B.** `data/music`, `data/newmusic`, `data/lyrics`, `data/stats`, `data/engagement`, `data/notes`, `data/profile`, `data/scrobble`, `data/audio`, `data/model`, `data/download` (metadata only).
- **S09 ∥ FlowNeuro.** `data/recommendation/**` (~20k lines) plus the `NeuroBenchmarkTest`/`MusicBenchmarkTest` regression floors on jvm. *Done:* benchmark scores match upstream on the same fixtures.

### Phase 3 — Playback
- **S10 Video player.** `player/**` minus sabr/stream: a Swift manager behind the `EnhancedPlayerManager` contract, driving AVPlayer or the S04b bundled engine per stream. Covers quality switching, speed 0.25–2x, subtitles (size/color/background), chapters, SponsorBlock, DeArrow, RYD, resume, preload/cache, error recovery and music-video mode.
- **S11 System integration.** `AVAudioSession`, background playback, PiP, Now Playing + remote commands (replaces `service`, `notification`), AirPlay, brightness/volume/seek gestures, for both engines (AVPlayer and the S04b bundled engine).
- **S12 Music player.** `EnhancedMusicPlayerManager` parity: queue add/remove/reorder, shuffle/repeat, sleep timer, the 1 Hz + refcounted 250 ms cadence, EQ and visualizer via `MTAudioProcessingTap` on AVPlayer; in the S04b bundled engine they tap the PCM before it is queued to the audio renderer.

### Phase 4 — UI (bulk is the S02 codemod plus Context→platform fixes)
- **S13 App shell + shared components.** `ui/*.kt` (root, navigation, MainActivity→`App()`), `ui/components/shared`, `ui/components/layout`, `ui/utils`, `ui/startup`.
- **S14 Video screens.** home, search, categories, channel, video player screen, comments/description sheets.
- **S15 ∥ Library screens.** shorts (AVPlayer pool replacing `ShortsPlayerPool`), subscriptions, history, library, downloads list, playlists, notes, notifications.
- **S16 ∥ Music screens.** music hub, artist, album, player sheet + mini player, lyrics, queue, equalizer, recap.
- **S17 ∥ Settings & the rest.** All settings pages, onboarding, crash screen, "Your Taste" dashboard, widget config, sync screens (UI only).

### Phase 5 — Platform features (all ∥ after Phase 4)
- **S18 Downloads + background.** A background `URLSession` and muxing with AVFoundation, and downloads in both engines' formats: H.264/AAC for AVPlayer, VP9/AV1/Opus played back by the S04b bundled engine. The new-upload notifications that ran on WorkManager move to `BGAppRefreshTask` and `UNUserNotificationCenter`.
- **S19 Import/export + media.** `data/backup`, `data/migration`, NewPipe import, recommendation-profile export/import, `data/localmedia` → Music library + Files import, Share Extension, `flow://` deep links.
- **S20 Device sync.** `sync/**`, `sync2/**`: LAN transport, Bonjour discovery, crypto, QR scanning and generation, bip39. *Done:* two simulators pair and sync in a CI test.
- **S21 Recognition, widgets, CarPlay, casting.** `data/recognition` (Shazam signature + `AVAudioEngine` mic capture), `widget/**` → 9 WidgetKit widgets, `service/auto` → CarPlay, `player/dlna`.

### Phase 6 — Ship
- **S22 Parity audit + release.** Screen-by-screen simulator screenshots against upstream's `Assets/*.jpeg` and a fresh Android capture. PARITY.md must have zero open lines. Locale check. Unsigned IPA artifact for sideloading. README with attribution.

Expect 23 sessions (S04b was added on 2026-10-31). S03, S07, S09, S10 and S13–S17 may each need a second sitting, so 22–30 in total. PARITY.md section 1 gives each session's upstream line count. The largest are S14 (~40k: split it into home/search/categories/channel, then the video player screen and its 94 `videoplayer` components), S16 (~30k), S13 and S17 (~26k each).

## Starting a session

Paste into a new session in this folder:

> Read PORTING_PLAN.md and PARITY.md. Do session SNN only, following the Definition of done. Port from `upstream/` at the pinned commit; keep package names. Update PARITY.md and the session log, commit, and push.

## Session log

| Session | Status | Branch / commit | Unverified / notes |
|---|---|---|---|
| S00 Analysis & plan | done | — | Upstream analysed at 77ae9d9. No code yet. |
| S01 Scaffold & CI | done | `main` 166d986, d312d4e | Local `jvmTest` 1/1. CI runs 41018580969 and 41020320714 green: jvm 1/1, iOS simulator 1/1 (`AppResourcesTest` XML in the `ios-evidence` artifact). macOS 26.6.2, Xcode 26.6, iPhone 17 Pro simulator; iOS job 12 min cold, 8 min warm. The app launches and stays up 15 s, and the screenshot shows edge-to-edge "Flow" from compose resources. CI fails if zero tests run. Not verified: device (arm64) build, signing, any Mac other than the CI runner. |
| S02 Theme, resources, codemod | done | `main` 53488ce..ab3b34b | Local `jvmTest` 39/39. CI run 41029990779 green: jvm 39/39, iOS simulator 36/36. `launch.png` shows the Flow theme with a palette picker; `launch-id.png` shows the Indonesian strings. `ui/theme` 16/16 files (12 verbatim) and upstream's 6 theme tests ported. All strings in 33 locales: UpstreamStringsTest matches 90,352 values against upstream's aapt2-resolved XML. 76 vectors and the app icon imported. Tools: `tools/codemod.py` (run it on every ported file) and `tools/import_resources.py`, both self-tested in CI; CI also fails if a re-import changes composeResources. Strings need `io.github.aedev.flow.ui.utils.stringResource`, because CMP's own formats only `%n$s`/`%n$d`. The iOS job took 19 min: the libs.versions.toml change reset the konan cache key, plus about 4k string accessors to compile. Not verified: Avenir Next Condensed on a device; locale-aware number formatting (S13 todo); zh-HK/zh-SG fallback (S22 todo). |
| S03 Networking + InnerTube | done | `main` c1e6dbd..e9d56c3 | Local `jvmTest` 514 run, 0 failed, 39 skipped. CI run 41056924212 green: jvm 514, iOS simulator 505 (the 9 jvm-only tests compare against ICU4J and java.text), app build and launch. Live API smoke test (YouTube Music search, YouTube Music home browse, video search with the WEB client version read off youtube.com): "Live API reached: no block reported" on jvm and on iOS through NSURLSession, with no account or key (F19, F35). Ported: innertube 141/141 files plus `WebClientVersion` (NewPipe's getClientVersion), network 4/4 on Ktor 3.5.2 (Darwin on iOS, CIO on jvm), utils 14 of 28 (the other 14 moved to the sessions that use them, each with a PARITY row), utils/protobuf, data/innertube 1 of 2 (RssSubscriptionService to S07). Pulled forward because the parsers need them: utils/relativedate, data/model Models.kt + RichText.kt, data/local/ThumbnailQuality.kt, player/sabr/proto/ProtobufReader.kt. 456 upstream tests ported; 39 of them are `@Ignore`d where upstream skips them too (its explore/ and shorts/ fixtures are gitignored). New: `utils/Locale`, `utils/PlatformFormat` (ICU4J / Foundation) and `utils/CompactNumbers`, whose CLDR data from ICU4J 77.1 matches CompactDecimalFormat on 671,216 cases across every ICU locale. iOS code type-checks on Windows (`compileKotlinIosSimulatorArm64`). Four pushes, not one: run 41052838841 failed in 0 s on a YAML error in ci.yml; 41052928251 failed only the 3 live tests, with TLS error -1200, because Kotlin's standalone simulator spawn has no trust daemon (tests now run on a booted simulator); 41055168264 failed the app build because Shared's exported `Context` shadowed SwiftUI's in iOSApp.swift. The konan cache is now saved even when the iOS job fails; the green run's iOS job took 7.6 min warm. Not verified: the app proxy against a real HTTP or SOCKS5 proxy (PARITY section 7, S17), captive-portal and VPN detection on a device (iOS limits), a device (arm64) run. |
| S04 Stream extraction + playback gate | done | `main` dd72c5e..fcc0d6c, then the docs commit logging this | Local `jvmTest` 753 run, 0 failed, 39 skipped; iOS arm64 and simulator compile. **Gate PASS on the user's iPhone** (iOS 26.3, home Wi-Fi, the sideloaded `PLAYBACK_GATE` build of 054e070 from Device IPA run 41220267535; `docs/evidence/s04/iphone-playback-gate.json` and `.webp`): the cipher in JavaScriptCore (player 11a45ffb, n transformed, signature solved), a PoToken mint in WKWebView (120-character player and streaming tokens, not low-trust), a YouTube Music song (VISIONOS, AAC itag 140: readyToPlay, an audio track, 5.08 s played) and a video (Big Buck Bunny, VISIONOS, six H.264 rungs playing 1080p: readyToPlay, video and audio tracks, 21 decoded 1920x1080 frames, 5.08 s played), both through the loopback HLS proxy into AVPlayer. The song's `audibleOptionSelected=false` is expected: an audio-only master has no EXT-X-MEDIA group, so AVPlayer has no audible selection group to report; its audio track and advancing clock show it played. AVPlayer logged one `-12318 Segment exceeds specified bandwidth`, because the master stated YouTube's average bitrates as BANDWIDTH; fcc0d6c states each rendition's peak and average segment rate measured from its sidx (HlsPlaylistsTest), to be re-verified on a device in S10. **CI simulator gate BLOCKED** (network wall, a warning), run 41222019979 green: jvm 751, iOS simulator 745, 0 failed; from GitHub's runners every client is bot-walled and WEB answers SABR only (S05). The gate fails only on a fault in a layer the port owns: cipher, PoToken mint, HLS proxy, AVPlayer once a stream was offered, a crash, or no verdict. The gate passed with H.264 and AAC. On the iPhone, H.264 and HEVC decode in hardware, VP9 and AV1 do not and av01 is not playable, and YouTube offered 1440p and 2160p only as VP9 or AV1, so AVPlayer alone tops out at 1080p; the user chose a bundled decoder on 2026-10-31, so VP9, AV1 and Opus are handed to S04b. Verified short of AVPlayer from a residential network too: LivePlaybackChainTest, and ffmpeg decoding 10 s of the proxied HLS (LocalHlsFfmpegJvmTest). Decisions: loopback HLS proxy (Ktor CIO) over a resource loader; H.264/AAC on AVPlayer, VP9, AV1 without hardware and Opus on a bundled engine (S04b; the simulator too finds vp09 not playable even in MP4, WebM not at all). Ported: NewPipe's stream model, player/stream 26 of 27, player/quality, player/resolver (as HLS), utils/cipher, utils/potoken 9 of 10, MusicPlayerUtils; 35 of upstream's 37 test files (PlaybackLoadResolver and its two tests move to S10, NewPipePoTokenProvider to S07). Pulled forward: player/state, player/config, GoogleVideoRequestPolicy, StreamDenialClassifier, PlayerDiagnostics' buffer, LoudnessGain, five SABR files, SponsorBlockSegment, three PlayerPreferences enums. Also fixed S03's iOS `Log`: NSLog "%@" with a Kotlin String crashed every log call (LogTest). Also added: the dispatch-only Device IPA workflow (`ipa.yml`), an unsigned gate build to sideload. Runs: 41083027289 (live test counted a block as a failure), 41084633678 (no macOS runner on 5 attempts; the 6th crashed on the Log bug), 41213254541 (Log crash, found from the .ips report), 41215728299 (gate BLOCKED, then still a failed step). Not verified: the -12318 fix on a device (S10). |
| S04b Bundled decoder engine + device gate | in progress: CI green; the iPhone gate awaits the user | `main` 4812913..494b6a6 | Local `jvmTest` 763 run, 0 failed, 39 skipped; iOS arm64 and simulator compile. **CI run 41245514593 green** (494b6a6): jvm 763, iOS simulator 756 (0 failed), engine tests 7 run (6 passed; the Picture in Picture one skipped: CI's simulator reports no PiP), app build and launch; the live gate BLOCKED (NETWORK_WALL, a warning) after the cipher and PoToken passed. Decoders: `tools/build_ffmpeg_xcframework.sh` builds FFmpeg 8.1.3 (LGPL-2.1-or-later, matroska and mov demuxers, vp9/opus/aac decoders, swresample, swscale; configure must report LGPL 2.1+ with pthreads on) and dav1d 1.5.4 into `build/ffmpeg/FFmpeg.xcframework` (arm64 device and simulator slices, ~6.6 MB each) from tarballs checked against pinned SHA-256; 131 s cold, then cached on the script's hash. 9.0.2 is newer; 8.1.3 was pinned for the 8.x API. Licences: THIRD_PARTY_NOTICES.md, and the texts inside the app (`ThirdPartyLicenses`). Engine (`iosApp/iosApp/Engine`, Swift): a byte-range reader on LocalHlsProxy's file route (GoogleVideoRequestPolicy applied once, in the proxy), FFmpeg decoding to NV12/P010 and float PCM, AVSampleBufferDisplayLayer and AVSampleBufferAudioRenderer under one AVSampleBufferRenderSynchronizer, late frames dropped (40 ms) or skipped to a keyframe (1 s), accurate seeks, PiP through `AVPictureInPictureController.ContentSource(sampleBufferDisplayLayer:)`, an `audioTap` for S12. Contract: Kotlin `player/engine/PlaybackEngine` with `AvPlayerEngine` and the Swift engine behind it (`close()`, not `release()`: Kotlin/Native renames that). Routing (`PlatformCodecSupport.engineFor`) follows upstream's VideoCodecUtils and AutoQualityPolicy; AirPlay keeps to AVPlayer's formats. CI proof on clips from `tools/make_engine_clips.py` (BundledEngineTests): VP9, 10-bit VP9, AV1 with Opus and VP9 with AAC, 140-141 frames shown, 0 dropped, A/V drift 0.19-0.69 ms (bound 5 ms), seeks landing exactly. iOS limits checked (PARITY section 7): AC-3/EC-3 and live DASH stay limits, with reasons. Runs: 41239412046 (one Swift error, the flush notification's name), 41241274318 (PiP unsupported on the simulator), 41243654147 (a seek landed one frame late: the clock started before the first frame; fixed in 494b6a6). Device IPA run 41245528071 (494b6a6). **Unverified until the iPhone gate:** VP9 2160p and 1440p, AV1 and Opus on the phone, dropped frames, CPU, thermal state, the highest sustained rung, PiP, and whether AVPlayer still logs -12318. Not verified anywhere: switching a playing bundled stream to AVPlayer when AirPlay starts (S10/S11; routing only, no receiver), HDR output (the 10-bit path ran on an SDR clip only). |
