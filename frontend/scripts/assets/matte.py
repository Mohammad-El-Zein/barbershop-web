"""
Cut-out post-processing for the collage objects (step 2 of the still-asset pipeline).

Input:  <workdir>/up/<name>.png    — Real-ESRGAN x4 upscale of assets-raw/crops/<name>.png
        <workdir>/mask/<name>.png  — soft alpha from rembg (BiRefNet-general-lite)
Output: <workdir>/final/<name>.png — RGBA, edges cleaned (cutout.py), trimmed to the silhouette

usage: python matte.py <workdir>
"""
import os
import sys

import numpy as np
from PIL import Image

from cutout import alpha_bbox, clean_cutout

WORK = sys.argv[1] if len(sys.argv) > 1 else "."
NAMES = ["chair", "clipper", "comb", "scissors", "bottle"]
PAD = 8

# Rows (in upscaled pixels) below which an object is cut, with a short feather.
# Clipper: the body tapers to y≈724; at y≈730 the mask splits into hanger loop + cord boot.
CUT_BELOW = {"clipper": (718, 728)}


def process(name):
    rgb = np.asarray(Image.open(os.path.join(WORK, "up", f"{name}.png")).convert("RGB")).astype(np.float32)
    a0 = np.asarray(Image.open(os.path.join(WORK, "mask", f"{name}.png")).convert("L")).astype(np.float32) / 255
    rgba, bg = clean_cutout(rgb, a0, cut_below=CUT_BELOW.get(name))

    x0, y0, x1, y1 = alpha_bbox(rgba[..., 3])
    y0, y1 = max(y0 - PAD, 0), min(y1 + PAD + 1, rgba.shape[0])
    x0, x1 = max(x0 - PAD, 0), min(x1 + PAD + 1, rgba.shape[1])
    os.makedirs(os.path.join(WORK, "final"), exist_ok=True)
    Image.fromarray(rgba[y0:y1, x0:x1], "RGBA").save(os.path.join(WORK, "final", f"{name}.png"))
    print(f"{name:9s} bg={bg.astype(int)} -> {x1 - x0}x{y1 - y0}")


if __name__ == "__main__":
    for n in NAMES:
        process(n)
