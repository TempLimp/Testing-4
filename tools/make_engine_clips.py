"""Writes the clips the S04b engine tests play (iosApp/iosAppTests/Clips), with an ffmpeg that has libvpx, libaom
and libopus. Run it again only to change a clip; the committed clips are its output.

Each clip is 6 s and shaped like YouTube's adaptive files: video and audio apart, VP9 and Opus in WebM, AV1 and AAC
in fragmented MP4 with a sidx, a keyframe every second. Video is black, then white from exactly 2.000 s (frame 60
at 30 fps); audio is silent, then a 1 kHz tone from exactly 2.000 s. The tests measure A/V drift as the difference
between the two onsets after the engine decoded them.
"""

import pathlib
import shutil
import subprocess
import sys

OUT = pathlib.Path(__file__).resolve().parent.parent / "iosApp" / "iosAppTests" / "Clips"
SECONDS = 6
VIDEO = f"color=c=black:s=320x180:r=30:d={SECONDS},drawbox=enable='gte(t,2)':color=white:t=fill"
AUDIO = f"sine=f=1000:r=48000:samples_per_frame=960:d={SECONDS},volume=enable='lt(t,2)':volume=0,aformat=channel_layouts=stereo"
FRAGMENTED_MP4 = ["-movflags", "+dash+global_sidx", "-f", "mp4"]

CLIPS = {
    "vp9.webm": ["-f", "lavfi", "-i", VIDEO, "-c:v", "libvpx-vp9", "-b:v", "200k", "-g", "30", "-keyint_min", "30", "-deadline", "good", "-cpu-used", "4", "-f", "webm"],
    "vp9-10bit.webm": ["-f", "lavfi", "-i", VIDEO, "-pix_fmt", "yuv420p10le", "-c:v", "libvpx-vp9", "-profile:v", "2", "-b:v", "200k", "-g", "30", "-keyint_min", "30", "-deadline", "good", "-cpu-used", "4", "-f", "webm"],
    "av1.mp4": ["-f", "lavfi", "-i", VIDEO, "-c:v", "libaom-av1", "-cpu-used", "8", "-b:v", "200k", "-g", "30", "-keyint_min", "30", *FRAGMENTED_MP4],
    "opus.webm": ["-f", "lavfi", "-i", AUDIO, "-c:a", "libopus", "-b:a", "96k", "-f", "webm"],
    # Not fragmented: ffmpeg writes the edit list that trims AAC's 1024 priming samples only into a whole-file moov.
    "aac.m4a": ["-f", "lavfi", "-i", AUDIO, "-c:a", "aac", "-b:a", "128k", "-movflags", "+faststart", "-f", "mp4"],
}


def main():
    ffmpeg = shutil.which("ffmpeg") or sys.exit("ffmpeg not found")
    OUT.mkdir(parents=True, exist_ok=True)
    for name, args in CLIPS.items():
        # Bit-exact output across runs is not needed, only the timeline; -fflags +bitexact keeps the version tag out.
        subprocess.run([ffmpeg, "-hide_banner", "-loglevel", "error", "-y", *args, "-fflags", "+bitexact", "-map_metadata", "-1", str(OUT / name)], check=True)
        print(f"{name}: {(OUT / name).stat().st_size} bytes")


if __name__ == "__main__":
    main()
