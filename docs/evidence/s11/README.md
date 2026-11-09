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

## Runs

(Filled in after each run.)
