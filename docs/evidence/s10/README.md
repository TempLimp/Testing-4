# S10: the video player on the iPhone

The user's iPhone 12 Pro Max (`iPhone13,4`, A14, 6 cores), iOS 26.3, home Wi-Fi, Low Power Mode off. It was
sideloaded with Sideloadly and a free Apple ID. Each run is the `PLAYBACK_GATE` build from `ipa.yml`. The gate runs
the S04/S05 playback items as regression checks, then S10's items through `EnhancedPlayerManager` (`PlaybackGatePlayer.kt`).

## Run 1: `iphone-playback-gate-1.json` / `.webp`

Build `2634e3b` (Device IPA run 42498355488), 2026-11-08. Verdict **PASS**. The user heard sound.

| Item | Result |
|---|---|
| Regression: cipher, PoToken, `sabrH264`, `sabrVp9`, `music`, `video`, `h264At1080p` | Pass, as in S05. SABR VP9 2160p60 on the bundled engine: 1208 frames, 0 dropped, real time 1.00005. The song: 20.1 s, 0 underruns, its seek landed 5 ms early |
| `keystore` | Pass (`ks1:` box sealed and opened) |
| `s10Play` | Auto on Big Buck Bunny opened on the bundled engine (VP9) with a first frame, at real time (1.0002). **At 144p:** see below |
| `s10Quality` | Picking 720p reopened at the position (6214 → 6269 ms) with a 1280x720 picture, then back to Auto |
| `s10Speed` | 2.0005x, 0.5000x, 0.2500x measured on the engine's clock |
| `s10Seek` | A seek to 60 s landed at 60.016 s; a resume at 30 s started at 30.062 s |
| `s10Captions` | `iG9CE55wbtY` offers 65 tracks; a translated track (Afrikaans, "(Gelag)") and the auto-generated English one each gave a cue at the position |
| `s10SponsorBlock` | Passed, but **did not prove a skip**: see below |
| `s10Recovery` | Passed, but **did not prove the recovery**: see below |
| `s10SabrFallback` | Forced SABR (MWEB) played on until GVS stopped it at about 60 s. The orchestrator reloaded twice, spent its budget and gave up. The manager closed the session and reloaded on direct formats (VISIONOS, AVPlayer 1080p) at 65.6 s, and played on to 90.4 s. **S05's finding is fixed on the phone** |

What run 1 showed, fixed in `fix(player): S10 part 7`:

1. **Auto stayed at 144p on the bundled engine.** The resolver started a bundled ladder on its lowest rung. QualityManager
   believed the anchor rung was playing, so its Auto checks never stepped up. Separately, the source resolves when the
   player prepares, not inside `loadMedia` as upstream's does, so the manager read the ladder flag before it was set.
2. **SponsorBlock:** the item seeked to 3 s before the segment and waited for the position to pass the segment's end. Right
   after a seek the bundled engine still reports its old position, here past the segment, so the wait ended at once.
   Media3 reports the seek's target instead; EnginePlayer now does too. The item now also requires the segments loaded
   and a jump faster than playing through.
3. **Recovery:** the fake wall refused a read-ahead past 4 MB of the 144p file, which the engine never needed before 80 s.
   No swap was tried either: the gate never set the current video, which the player screen sets and under which the
   manager registers streams for swapping. The item now requires the wall's 403 and a swap or a reload.
