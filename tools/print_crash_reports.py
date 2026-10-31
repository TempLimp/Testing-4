"""Prints the crashing thread of each macOS/simulator crash report (.ips) given, so CI logs name the frame.

An .ips file is a JSON header line followed by a JSON body with the exception, the threads (one marked
"triggered") and the images their frames point into.
"""

import json
import sys


def summarize(path):
    with open(path, encoding="utf-8", errors="replace") as f:
        header = json.loads(f.readline())
        body = json.loads(f.read())
    print(f"== {path}: {header.get('app_name') or header.get('name')} {header.get('timestamp', '')}")
    exception = body.get("exception", {})
    print(f"exception: {exception.get('type')} {exception.get('signal')} {exception.get('subtype', '')}")
    print(f"termination: {json.dumps(body.get('termination', {}))[:300]}")
    images = body.get("usedImages", [])
    for thread in body.get("threads", []):
        if not thread.get("triggered"):
            continue
        print(f"crashed thread: {thread.get('name') or thread.get('queue') or thread.get('id')}")
        for frame in thread.get("frames", [])[:45]:
            index = frame.get("imageIndex")
            image = images[index].get("name", "?") if index is not None and index < len(images) else "?"
            print(f"  {image}  {frame.get('symbol', hex(frame.get('imageOffset', 0)))}+{frame.get('symbolLocation', '')}")


if __name__ == "__main__":
    for report in sys.argv[1:]:
        try:
            summarize(report)
        except Exception as error:  # a truncated or older-format report still gets named
            print(f"== {report}: unreadable ({error})")
