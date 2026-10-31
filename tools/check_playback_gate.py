"""Reads the S04 playback gate's result (Documents/playback-gate.json from the app) and fails unless it passed.

Prints one line per layer, so the CI log shows which one failed: cipher (JavaScriptCore), PoToken
(WKWebView), the YouTube Music song and the video (AVPlayer). BLOCKED means YouTube refused the runner:
a warning, as for the live smoke test. A failure, or a gate that never finished, fails the job.
"""

import json
import sys


def main(path):
    with open(path, encoding="utf-8") as f:
        gate = json.load(f)
    print(f"device: {gate.get('device')}")
    print(f"codecs: {json.dumps(gate.get('codecs'))}")
    for layer in ("cipher", "poToken", "music", "video"):
        result = gate.get(layer) or {}
        details = {k: v for k, v in result.items() if k not in ("verdict", "uri")}
        print(f"{layer}: {result.get('verdict')} {json.dumps(details)}")
    verdict = gate.get("verdict")
    if verdict == "RUNNING":
        # The app died or hung before the gate finished; it names the layer it was in.
        verdict = f"FAIL (the app stopped in '{gate.get('running')}')"
    print(f"PLAYBACK GATE: {verdict}")
    if verdict == "BLOCKED":
        # YouTube turned this runner away, as LiveInnerTubeSmokeTest reports it: a warning, not a failure.
        print("::warning::Playback gate BLOCKED: YouTube refused this runner at the player response")
        return 0
    if verdict != "PASS":
        for line in gate.get("diagnostics", [])[-40:]:
            print(f"  {line}")
        print(f"::error::Playback gate {verdict}")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1]))
