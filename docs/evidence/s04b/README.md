# S04b playback gate on an iPhone

The user's iPhone 12 Pro Max (`iPhone13,4`, A14, 6 cores), iOS 26.3, on home Wi-Fi, Low Power Mode off, sideloaded
with Sideloadly and a free Apple ID: the `PLAYBACK_GATE` build of commit `494b6a6` (IPA from Device IPA run
41245528071), run on 2026-11-01.

- `iphone-playback-gate.json`: the gate's result, as the app wrote it to Documents. Verdict **PASS**
  (`tools/check_playback_gate.py` agrees).
- `iphone-playback-gate.webp`: the screen at the end, the verdicts over the last AV1 rung's final frame.

Each bundled-engine rung plays for a fixed 20 s window once its clock runs. "CPU" is the whole app process (decoders,
renderers, the loopback proxy, Compose) as a share of one core, from `getrusage`; the phone has 6 cores.
"Sustained" means at most 1 % of frames dropped, the clock within 2 % of real time, and no gap in the sound.

| Item | Engine, decoders | Shown / dropped | Clock vs real time | Latest frame | Sound gaps | CPU | Thermal (start/max/end) | Seek landing |
|---|---|---|---|---|---|---|---|---|
| VP9 2160p60 + Opus 251 | bundled: vp9, opus | 1208 / 0 (60.0 fps) | 1.00005 | 0 ms | 0 | 103 % | nominal / nominal / nominal | -1 ms (bound 17) |
| VP9 1440p60 + Opus 251 | bundled: vp9, opus | 1208 / 0 (60.0 fps) | 1.00005 | 0 ms | 0 | 50 % | nominal throughout | -11 ms |
| AV1 2160p60 + AAC 140 | bundled: libdav1d, aac | 1209 / 0 (60.0 fps) | 1.00005 | 0 ms | 0 | 191 % | nominal throughout | -15 ms |
| AV1 1440p60 + AAC 140 | bundled: libdav1d, aac | 1208 / 0 (60.0 fps) | 1.00005 | 0 ms | 0 | 124 % | nominal throughout | -8 ms |
| Song, Opus 251 (BSTsnWoslP4) | bundled: opus | audio only | 1.00005 | — | 0 | 1.9 % | nominal throughout | -6 ms (bound 21) |

- **Highest sustained rung: 2160p60** for both VP9 and AV1, so the gate did not need to walk down past 1440p.
- **A/V sync:** every shown frame was handed to the display before its time ("latest frame" 0 ms, the bound being the
  engine's 40 ms drop tolerance) and the sound never ran dry, so frames were shown at their timestamps on the clock the
  sound drives. The decode-side drift (a white flash against a tone onset) was measured on CI's clips, not here.
- **Audio choices are upstream's:** the song resolved to Opus 251 on Wi-Fi (MusicAudioQuality AUTO resolves to HIGH);
  VP9 played beside Opus 251 as the gate asks; AV1 beside AAC 140, which upstream's highest-bitrate rule picks for this
  video (129,481 against 128,612 b/s).
- **AVPlayer (S04's check, kept):** the H.264 ladder through LocalHlsProxy played (readyToPlay, video and audio tracks,
  21 decoded frames, 5.06 s), at 1280x720 in that window. Its error log was empty, so no `-12318 Segment exceeds
  specified bandwidth` this time; S04's came from the 1080p variant, which AVPlayer did not reach in these 5 s, so this
  is weak evidence for fcc0d6c and the full check stays with S10.
- **Picture in Picture:** supported and possible with `AVPictureInPictureController.ContentSource(sampleBufferDisplayLayer:)`
  on the engine's layer; the gate's programmatic start without a tap did not open the window (`started: false`), as
  iOS allows only from a user action. Opening it from a button is S11's.
- The device has no hardware VP9 or AV1 decoder (`hardwareVp9`/`hardwareAv1` false), as in S04, so all of the above
  is FFmpeg and dav1d in software.

Not shown by this run: heat and battery over long viewing (about 2.5 minutes of bundled playback in all, thermal state
stayed nominal); HDR (Big Buck Bunny is SDR); AirPlay.

Big Buck Bunny is (c) Blender Foundation, www.bigbuckbunny.org, CC BY 3.0.
