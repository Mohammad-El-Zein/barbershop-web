#!/usr/bin/env bash
# Extract an evenly spaced WebP frame sequence from a video for the canvas scrubber.
#
#   ./scripts/extract-frames.sh <input-video> [frame-count=150] [width=1920]
#
# Output: public/frames/frame_0001.webp … frame_NNNN.webp
# Requires ffmpeg (built with libwebp) on PATH.
set -euo pipefail

INPUT="${1:?usage: extract-frames.sh <input-video> [count] [width]}"
COUNT="${2:-150}"
WIDTH="${3:-1920}"
OUT_DIR="$(cd "$(dirname "$0")/.." && pwd)/public/frames"

command -v ffmpeg >/dev/null || { echo "ffmpeg not found on PATH" >&2; exit 1; }
command -v ffprobe >/dev/null || { echo "ffprobe not found on PATH" >&2; exit 1; }

# Duration in seconds → fps that yields exactly COUNT frames over the clip.
DURATION="$(ffprobe -v error -show_entries format=duration -of csv=p=0 "$INPUT")"
FPS="$(awk -v c="$COUNT" -v d="$DURATION" 'BEGIN { printf "%.6f", c / d }')"

mkdir -p "$OUT_DIR"
rm -f "$OUT_DIR"/frame_*.webp

ffmpeg -hide_banner -loglevel error -i "$INPUT" \
  -vf "fps=${FPS},scale=${WIDTH}:-2:flags=lanczos" \
  -frames:v "$COUNT" \
  -c:v libwebp -quality 60 -compression_level 6 -preset photo \
  "$OUT_DIR/frame_%04d.webp"

N="$(ls "$OUT_DIR"/frame_*.webp | wc -l | tr -d ' ')"
echo "Wrote $N frames to $OUT_DIR"
echo "Set scene.frames.enabled = true and scene.frames.count = $N in src/config/site.config.ts"
