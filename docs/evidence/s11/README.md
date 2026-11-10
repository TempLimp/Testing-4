# S11: system integration on the iPhone

The user's iPhone 12 Pro Max (`iPhone13,4`), sideloaded with Sideloadly and a free Apple ID. Each run is the
`PLAYBACK_GATE` build from `ipa.yml`. The gate plays `s10Play` through the manager first (regression), then S11's
items (`PlaybackGateSystem.kt`). Each S11 item needs the person holding the phone and says on screen what to do; on
CI's simulator they are skipped (unattended).

## What each item proves

| Item | Engine | What it checks |
|---|---|---|
| `s11BackgroundAvPlayer` | AVPlayer (H.264) | The phone locked for at least 15 s: the clock keeps real time (0.9-1.1), the player is audio only while locked, the picture is back after unlocking; AVPlayer's item has an audio track (the S10 fix: Opus gives way to AAC beside H.264) |
| `s11BackgroundBundled` | bundled (VP9) | The same on the bundled engine |
| `s11LockScreen` | AVPlayer, then the queue's next | Now Playing shows the title; on the lock screen Pause and Play reach the player while locked, Next opens the queue's second video, Previous the first again, the progress bar seeks |
| `s11Pip` | AVPlayer, then the next video | The player's PiP request opens the window; the window's own Pause and Play reach the player (Media3's remote reason); the next video opens inside the same window (on the bundled engine when Auto picks VP9); the restore button brings the app back |
| `s11AutoPip` | bundled (VP9) | With upstream's auto PiP setting on, going Home opens the window by itself and playback goes on in it |
| `s11Gestures` | AVPlayer | Upstream's gestures over the video: brightness (left half), volume (right half, through the hidden MPVolumeView), sideways seek drag, double-tap skip, a volume key shown on the player's overlay; leaving gives the screen its own brightness back |
| `s11AirPlay` | bundled, then AVPlayer | With a receiver picked: the video reopens on AVPlayer and goes out over AirPlay. NOT_TESTED without one |

## Run 1: `iphone-playback-gate-1.json`

Build `da8ba7b` (Device IPA run 42622908197), 2026-11-09, iPhone 12 Pro Max, iOS 26.3, run in LiveContainer. The app
crashed when it reached `s11Gestures` (see below), so the file stops there (`"running": "s11Gestures: loading"`) and
`s11Gestures` and `s11AirPlay` did not run.

| Item | Result |
|---|---|
| cipher, poToken, keystore | Pass |
| `s10Play` | Pass: Auto opened 1080p60 H.264 in AVPlayer, first frame, real time 1.0 |
| `s11BackgroundAvPlayer` | **FAIL only on the picture coming back.** Locked 38.4 s; the clock kept real time (1.00003) audio only; AVPlayer's item had its audio track (the S10 fix: AAC beside H.264); Now Playing showed the title, "Blender", 634.6 s and the artwork. After unlocking the video stayed audio only |
| `s11BackgroundBundled` | **FAIL only on the picture coming back.** Locked 31.5 s, clock 0.98 of real time, audio only; Now Playing as above |
| `s11LockScreen` | PASS as the item then stood: Pause and Play from the lock screen reached the player while locked (`playWhenReady` false, then true), Next and Previous reached it, the progress bar seeked (to 199.6 s, 27.1 s, 511.1 s, 314.6 s). **But** the item only checked that Next and Previous moved the queue: the next video never opened (see below), so they are not proved |
| `s11Pip` | **FAIL.** The window opened from the player's request; its own Pause and Play reached the player as Media3's remote reason (4); 5.6 s played in the window; the restore button brought the app back. The queue's next video failed to open (`YouTubeRepository not initialized`), so the window kept across an engine change is not proved |
| `s11AutoPip` | PASS: with the setting on, going Home opened the window by itself on the bundled engine; 6.0 s played in it; restored |
| `s11Gestures` | Crashed: `child view controller ... should have parent view controller:(null) but actual parent is UIHostingController` (UIKit's exception) in the gate's `showCompose` |

What run 1 showed, fixed in `fix(system): S11 part 2` (7467c69):

1. **The picture did not come back after unlocking.** PlayerAppLifecycle restored the video on `willEnterForeground`,
   when iOS still reports the app in the background, so the manager deferred the restore ("restoreVideoOutput deferred
   interactive=false") and nothing asked again. It now restores on `didBecomeActive`, a deferred restore included.
2. **The queue's next video never opened.** The service layer loads through YouTubeRepository, which FlowApplication's
   start-up creates (S13); the gate had not created it. It does now, and the lock-screen item now requires the next
   and previous videos to play, not only to take the queue's place.
3. **The gate's gesture surface crashed the app.** It was a child of SwiftUI's hosting controller with its view in the
   window; the gate's Compose surfaces now get a window of their own. Gate code only; the app has no such surface.

Run 2 (7467c69, Device IPA run 42874110282) has not happened: that build got no macOS runner in three attempts
(2026-11-10, 18:20-19:06 UTC).
