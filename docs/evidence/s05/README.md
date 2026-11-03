# S05: SABR on the iPhone

The user's iPhone 12 Pro Max (`iPhone13,4`, A14, 6 cores), iOS 26.3, home Wi-Fi, Low Power Mode off, sideloaded with
Sideloadly and a free Apple ID. `iphone-playback-gate.json` and `.webp` are the `PLAYBACK_GATE` build of `0099714`
(Device IPA run 41442574898), 2026-11-02. Verdict **PASS**, every item that ran. Asked whether they heard the two SABR
videos, the user answered: "sound is heard".

## The two SABR items

Both use upstream's forced SABR (`extract(forceSabr = true)`) on Big Buck Bunny (`aqz-KE-bpKQ`). The session resolved
through MWEB with a PoToken minted in WKWebView, and LocalHlsProxy served it (`/s/{id}/...`). The request body went out
through NSURLSession, and GVS served media to it.

| Item | Formats | Engine | Result |
|---|---|---|---|
| `sabrH264` | H.264 1080p itag 299 (`avc1.64002A`), AAC itag 258 (`mp4a.40.2`); 1080p H.264 asked through `resolveSabrDownload` on the client the forced session used | AVPlayer, HLS written from the sidx in the SABR init segments | readyToPlay, video and audio tracks, audible option selected, 20.17 s played, 1920x1080 (frame and presentation size), no -12318. The error log holds five `-12938 HTTP 404`: see below |
| `sabrVp9` | VP9 2160p60 itag 315, Opus 251: the forced session as it comes | Bundled (FFmpeg reads each track as one stream: init segment, then segments in order) | Started in 1.94 s; 20.13 s played at real time (1.00005); 1209 frames shown, 0 dropped, none late; 0 audio underruns; 109 % of one core; thermal nominal throughout. Seeking not checked: a SABR session seeks by being rebuilt at the new position (upstream's `sabrSeekTo`, S10) |

## About a minute of media per SABR session

`sabrH264`'s five 404s come from YouTube, not from the proxy:

1. **The cut.** About 8 s in, with AVPlayer's buffer reaching 49.9 s, GVS stopped sending media and answered "attestation
   required" (STREAM_PROTECTION_STATUS 3).
2. **Upstream's recovery ran and failed.** The session refreshed the PoToken and then reloaded the player response
   ("SABR reload applied at 49920ms (attempt 1/2)"). After that it got only responses with no media, and called itself
   wedged.
3. **The 404s.** The proxy answered AVPlayer's later segment requests with 404. AVPlayer had already buffered past the
   20 s window, so the item passed.

The gate's diagnostics hold no "server demands player reload", so the reload came from the attestation path.
The same happens on Windows. `SabrReloadExploreJvmTest` (manual, `FLOW_EXPLORE_SABR`) runs the same code with a token
minted in headless Edge:

- **Read as fast as AVPlayer reads:** GVS sent the first 62 s of media within 3.6 s, every response "attestation pending"
  (status 2). Then it answered status 3 with no media. A refreshed token and two player-response reloads each drew
  status 3 from their first request.
- **Read 30 s ahead of the wall clock, as a player's buffer does:** the same 62 s, spread over 29 s, then the same
  status 3, refresh and reloads.

So while a PoToken's verdict stays pending, GVS serves about a minute of a video, however fast it is read, and then stops. Upstream's orchestrator
already says so: getting out of a pending verdict needs a new visitor identity, which upstream has not built. That is
why the session ladder keeps deciphered direct formats beside every SABR session ("direct fallback 41/41 formats"), and
upstream's MediaLoader moves to them when the orchestrator reports the error (S10).

`sabrVp9` ran into no cut. The bundled engine reads each track only a few seconds ahead of the picture, so 20 s of play
fetched less than a minute of media.

## The other items

| Item | Result |
|---|---|
| Cipher, PoToken mint, condensed font | Pass, as in S04 and S04b: JavaScriptCore (player `11a45ffb`), WKWebView (120-character tokens, not low-trust), Avenir Next Condensed in five distinct faces |
| Picture in Picture by a tap | Opened over the bundled engine's VP9 1080p, stayed up, 6.04 s played inside it, 363 frames, 0 dropped |
| Song (Opus 251, bundled engine) | 20.1 s at real time, 0 underruns, 3.6 % CPU, seek landing 6 ms before target |
| H.264 in AVPlayer (720p window; held at 1080p for 30 s) | Both readyToPlay with video and audio tracks, empty error logs, no -12318 |
| VP9 and AV1, 2160p60 and 1440p60 | 1208-1210 frames shown per 20 s, 0 dropped, none late, 0 underruns; highest sustained 2160p for both codecs. CPU: VP9 102 % / 50 %, AV1 202 % / 134 % |
| 10-bit AV1 HDR (`tO01J-M3g0U`), 2160p60 and 1440p60 | P010 tagged BT.2020 and PQ, EDR headroom up to 6.76 and 8.0 of 8.0, 0 dropped; 377 % CPU at 2160p; thermal fair |
| Ten minutes of VP9 2160p60 (`longRun`) | **Switched off** for this run (`SKIPPED` in the file): it passed in S04b's verification pass (`../s04b-verify/`) and took the battery under 5 %. It is back on for later runs (`SWITCHED_OFF` in `PlaybackGate.kt`) |

## Second run, same build

`iphone-playback-gate-2.json` and `.webp`: the same IPA (`0099714`) on the same phone, 2026-11-03, verdict **PASS**.
The user: "audio confirmed for all".

- **`sabrH264` passed again** (20.04 s, 1920x1080). The cut came at 62.2 s of media, as on jvm. This time recovery
  ended the other way: two reloads, neither bringing media back, then "SABR reload budget spent (2/2)". That path sends the proxy no
  event, so AVPlayer's next requests got no answer and timed out (three `-12889 No response for media file`) instead of
  404s. It is the 60 s stall S10 must end by closing the session when the orchestrator reports the error. The first
  run's "SABR wedged ... (reload loop)" after its reload matches the re-sent-segment bug fixed in `8b41f9d`, which this
  build predates; the phone's log alone does not prove it.
- **`sabrVp9` passed again**: started in 3.84 s, 20.11 s at real time, 1207 frames, 0 dropped, 0 underruns, 117 % CPU.
- **Not S05's: two direct items held less than in the first run.** VP9 2160p60 dropped 377 of 1198 frames at 75 % CPU,
  so the highest VP9 held was 1440p. HDR AV1 held only 1080p (2160p dropped 161 at 319 % CPU, 1440p dropped 46). The
  first run, same build, held 2160p for both with 0 dropped. Those two 2160p rungs started 3 to 6 times slower this
  time (VP9 4.6 s against 0.7 s, HDR AV1 3.4 s against 1.0 s) and used less CPU, not more, which points to the network rather than the decoder; that is not
  established. Each item still passes: every rung played in sync, and one held without drops.

Big Buck Bunny is (c) Blender Foundation, www.bigbuckbunny.org, CC BY 3.0.
