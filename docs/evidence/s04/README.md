# S04 playback gate on an iPhone

The user's iPhone, iOS 26.3, on home Wi-Fi, sideloaded with Sideloadly and a free Apple ID: the `PLAYBACK_GATE`
build of commit `054e070` (IPA from CI run 41220267535).

- `iphone-playback-gate.json`: the gate's result, as the app wrote it to Documents. Verdict PASS.
  - cipher: JavaScriptCore solved player `11a45ffb` (signature timestamp 20753, n transformed, signature solved).
  - poToken: WKWebView minted 120-character player and streaming tokens, not low-trust.
  - music: Queen, "Bohemian Rhapsody" (`BSTsnWoslP4`) via VISIONOS, itag 140 (`mp4a.40.2`) through the loopback HLS
    proxy: readyToPlay, an audio track, 5.08 s played. `audibleOptionSelected` is false because an audio-only master
    has no `EXT-X-MEDIA` group to select from, not because it was silent.
  - video: Big Buck Bunny (`aqz-KE-bpKQ`) via VISIONOS, a ladder of six H.264 rungs, playing 1080p (`avc1.64002a`):
    readyToPlay, video and audio tracks, 21 decoded 1920x1080 frames, 5.08 s played.
  - codecs: H.264 and HEVC in hardware; no VP9 or AV1 decoder, and AVFoundation reports neither VP9 nor AV1 as
    playable, so YouTube's 1440p and 2160p (VP9 and AV1 only) are out of reach and 1080p H.264 is the top rung.
  - errorLog: one `-12318 Segment exceeds specified bandwidth`: the master stated average bitrates as BANDWIDTH.
    Fixed after this run (fcc0d6c, measured peak and average from each rendition's sidx); not yet re-run on a device.
- `iphone-playback-gate.webp`: the screen during that run, the gate's verdicts over the playing video.

Big Buck Bunny is (c) Blender Foundation, www.bigbuckbunny.org, CC BY 3.0.
