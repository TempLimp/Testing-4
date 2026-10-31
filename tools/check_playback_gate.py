"""Reads the playback gate's result (Documents/playback-gate.json from the app) and fails unless it passed.

Prints one line per layer, so the CI log shows which one failed: cipher (JavaScriptCore), PoToken (WKWebView), the
YouTube Music song (in the engine its format goes to), the video in AVPlayer (S04), and the video's VP9 and AV1 rungs
in the bundled engine (S04b), one line per rung with its measurements. BLOCKED means no client offered this runner a
stream it can play before SABR (S05): a warning, as for the live smoke test. A fault in a layer the port owns
(cipher, PoToken mint, HLS proxy, either engine once it had a stream), a crash or a missing verdict fails.
"""

import json
import sys

LAYERS = ("cipher", "poToken", "music", "video", "vp9", "av1")
RUNG_FIELDS = (
    "verdict", "sustained", "videoSize", "videoDecoder", "audioDecoder", "realtime", "framesPerSecondShown", "droppedPercent",
    "maxVideoLateMs", "audioUnderruns", "cpuPercentOfOneCore", "thermalStart", "thermalMax", "seek", "pictureInPicture", "error",
)


def main(path):
    with open(path, encoding="utf-8") as f:
        gate = json.load(f)
    print(f"device: {gate.get('device')} {json.dumps(gate.get('hardware'))}")
    print(f"codecs: {json.dumps(gate.get('codecs'))}")
    for layer in LAYERS:
        result = gate.get(layer) or {}
        # The bundled engine's items list each measured rung; the AVPlayer video names its ladder in a string.
        rungs = result.get("rungs") if isinstance(result.get("rungs"), list) else []
        details = {k: v for k, v in result.items() if k not in ("verdict", "uri") and not (k == "rungs" and rungs)}
        print(f"{layer}: {result.get('verdict')} {json.dumps(details)}")
        for rung in rungs:
            measured = {k: rung.get(k) for k in RUNG_FIELDS if k in rung}
            print(f"  {rung.get('height')}p{rung.get('fps')} {rung.get('codec')}: {json.dumps(measured)}")
    verdict = gate.get("verdict")
    if verdict == "RUNNING":
        # The app died or hung before the gate finished; it names the layer it was in.
        verdict = f"FAIL (the app stopped in '{gate.get('running')}')"
    faults = [layer for layer in LAYERS if (gate.get(layer) or {}).get("verdict") == "FAIL"]
    if faults and verdict in ("PASS", "BLOCKED"):
        # The app's rule too: a fault in a layer the port owns fails, whatever else was blocked.
        verdict = f"FAIL ({', '.join(faults)})"
    print(f"PLAYBACK GATE: {verdict}")
    if verdict == "BLOCKED":
        # YouTube turned this runner away, as LiveInnerTubeSmokeTest reports it: a warning, not a failure.
        blocked = ("music", "video", "vp9", "av1")
        reasons = "; ".join(f"{layer}: {(gate.get(layer) or {}).get('block')}" for layer in blocked if (gate.get(layer) or {}).get("verdict") == "BLOCKED")
        print(f"::warning::Playback gate BLOCKED, no playable stream offered to this runner ({reasons})")
        return 0
    if verdict != "PASS":
        for line in gate.get("diagnostics", [])[-40:]:
            print(f"  {line}")
        print(f"::error::Playback gate {verdict}")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1]))
