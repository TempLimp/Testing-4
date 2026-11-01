# S04b verification pass: the phone gate and the proxy check

Everything still logged as "not verified" that could be settled before S05, in one phone build and one CI check.

## The iPhone runs

The user's iPhone 12 Pro Max (`iPhone13,4`, A14, 6 cores), iOS 26.3, home Wi-Fi, Low Power Mode off, unplugged (the
gate read `batteryState` "unplugged" both times), sideloaded with Sideloadly and a free Apple ID.

- **Run 2 (the result):** `iphone-playback-gate.json` and `.webp`, the `PLAYBACK_GATE` build of `c60ae1a` (Device IPA run
  41343146235), 2026-11-02. Verdict **PASS**, every item. The tables below are this run.
- **Run 1:** `iphone-run1-playback-gate.json` and `.webp`, the build of `7dad063` (Device IPA run 41316002962),
  2026-11-01. Verdict FAIL: every HDR rung failed to open with `1.mp4 bytes 4194304-: HTTP 416`. The HDR video's audio
  file is about 2.5 MB, and the engine's range reader, before it knew the size, also asked for the 2 MiB chunk at 4 MiB,
  which the server refused (416); it took that as fatal. Every earlier file was over 4 MiB. Fixed in `c60ae1a` (no read
  ahead before the size is known; a 416 is the end of the file), with `testAShortFileIsReadToItsEndWithoutAskingPastIt`.
  Every other item of run 1 passed too; its numbers are in its file.

### HDR (10-bit) on the phone

The HDR video is "The World in HDR in 4K (ULTRA HD)" (`tO01J-M3g0U`, Jacob + Katie Schwarz): through VISIONOS, the
client upstream's ladder asks first, it is offered as 10-bit AV1 HDR (itags 694-701, `av01.0.13m.10.0.110.09.16.09.0`:
BT.2020, PQ) from 144p to 2160p60, with no 8K rung. **No VP9 profile 2 (vp09.02) was offered** for it or for three other
HDR videos probed on 2026-11-01 (VISIONOS, and MOBILE without the ladder's setup), so with upstream's own client and codec
choice HDR on this phone is AV1; 10-bit VP9's display on a phone stays unverified, because YouTube does not serve it here.

| Rung | Pixel format | Colour attached | EDR headroom, potential / highest during play | Shown / dropped (20 s) | Clock | CPU | Thermal start / max / end | Seek landing |
|---|---|---|---|---|---|---|---|---|
| 2160p60 + AAC 140 | P010 | ITU_R_2020, SMPTE_ST_2084_PQ | 8.0 / 8.0 | 1214 / 0 | 1.00005 | 351 % | nominal / nominal / nominal | -15 ms |
| 1440p60 + AAC 140 | P010 | ITU_R_2020, SMPTE_ST_2084_PQ | 8.0 / 8.0 | 1209 / 0 | 1.00005 | 154 % | nominal / fair / fair | -16 ms |

The screen used its whole extended range (current EDR headroom reached the potential 8.0) while the HDR picture was up.
Highest sustained HDR rung: 2160p60. Upstream keeps 10-bit on a ladder of its own (`AdaptiveLadder.isHighBitDepth`),
and so did the gate. The SDR rungs (VP9 and AV1, 2160p and 1440p) handed the display NV12 tagged BT.709, 0 dropped.

### Ten minutes of VP9 2160p60

VP9 2160p60 with Opus 251, 602.3 s played, measured in 20 windows of 30 s:

- **Sustained the whole time:** 0 frames dropped in every window (1805-1809 shown per window), the clock at real
  time, no gap in the sound.
- Thermal state (the hottest in each window): fair in the first window (the phone came to it warm from the HDR item),
  **serious by 90 s** and to the end, without a dropped frame. In run 1 (started cooler): fair by 60 s, serious by
  300 s, also 0 dropped.
- CPU, whole app as a share of one core: 64-163 % per window (6 cores).
- Battery: 40 % at the start and at the end. iOS reports the level in 5 % steps, so ten minutes cost under 5 %; run 1
  went from 55 % to 50 %.

### The other items

| Item | Result |
|---|---|
| H.264 held at 1080p in AVPlayer (S04's -12318) | A master with only the 1080p rung: 30.0 s at 1920x1080, 119 decoded frames, AVPlayer's error log empty, **no -12318** (the same in run 1, 30.2 s) |
| Picture in Picture by a tap | Tapping "Start Picture in Picture" over the bundled engine's VP9 1080p opened the window (`active`), it stayed up, and 6.05 s played inside it, 363 frames, 0 dropped |
| Condensed font (PARITY's S02 row) | iOS has Avenir Next Condensed; CoreText picks a different face for each of the port's five weights: 400 Regular, 500 Medium, 600 DemiBold, 700 Bold, 800 Heavy. The screenshot's bottom five lines are drawn through the port's own Compose family (`appFontFamily(AppFont.CONDENSED)`) in those weights |
| Song (Opus 251, bundled engine), S04's H.264 video, VP9 and AV1 to 2160p60 | Pass, as in S04b's first gate |

### The user's checks

Asked whether they heard sound on the song and on the videos, and whether the HDR clip looked HDR (bright highlights,
not washed out or dim), the user answered: "both yes for the questions".

## The proxy check on CI (S03's app proxy)

The CI iOS job starts real proxies on the runner, tinyproxy 1.11.3 (HTTP) and microsocks 1.0.5 (SOCKS5), each plain and
with a username and password, and `ProxyRoutingTest` sends HTTPS requests through each with `ProxyAwareClient` on the
Darwin engine (NSURLSession) in the iOS simulator; CI then reads each proxy's own log. Every proxy carried the app to
`example.com` (200) and `www.youtube.com/generate_204` (204), and appears in its log for both hosts (tinyproxy:
`CONNECT example.com:443`, and on the authenticated one each host twice, the 407 then the retry with credentials;
microsocks: `connected to example.com:443`). Moved to a closed port, every proxy made the request fail, so the client
does not go around its proxy. CI runs 41314585663 and 41343143156 (`verify-wip`), and the run on `main` that logs this.
SOCKS5 with credentials works on iOS: not an iOS limit.

Big Buck Bunny is (c) Blender Foundation, www.bigbuckbunny.org, CC BY 3.0.
