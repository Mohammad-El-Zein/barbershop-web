"""
Shared cut-out edge cleanup used by matte.py (still objects) and turntable.py (image sequences).

clean_cutout(rgb, a0) takes an RGB image shot on a plain light background plus a soft alpha
mask (e.g. from rembg) and returns a clean RGBA image:
  1. colour-key: soft pixels that still look like the background lose alpha
     (grey left inside holes, e.g. scissor rings); fully opaque pixels are never touched
  2. choke + 1px erosion: removes the thin light rim (background blend + upscaler ringing)
  3. decontaminate: un-mix the background from semi-transparent pixels,
     F = (I - (1 - a) * B) / a, so edges don't carry grey into a dark scene
  4. optional: cut everything below a row (cords, floor shadow) with a short feather
  5. drop loose specks not connected to the main object
"""
import cv2
import numpy as np


def smoothstep(e0, e1, x):
    t = np.clip((x - e0) / (e1 - e0), 0, 1)
    return t * t * (3 - 2 * t)


def border_color(rgb, width=4):
    """Median colour of the outer border = the studio background."""
    b = np.concatenate([rgb[:width].reshape(-1, 3), rgb[-width:].reshape(-1, 3), rgb[:, :width].reshape(-1, 3), rgb[:, -width:].reshape(-1, 3)])
    return np.median(b, axis=0)


def clean_cutout(rgb, a0, bg=None, cut_below=None, erode=1):
    """
    rgb: HxWx3 float32 0..255, a0: HxW float32 0..1 (soft mask)
    bg: background colour (default: border median)
    cut_below: (y_start, y_end) — alpha fades to 0 between these rows and stays 0 below
    returns HxWx4 uint8
    """
    B = border_color(rgb) if bg is None else np.asarray(bg, np.float32)

    # 1) colour-key soft pixels that look like background
    dist = np.abs(rgb - B).max(axis=2)
    key = smoothstep(12, 45, dist)
    a1 = np.where(a0 < 0.98, a0 * (0.3 + 0.7 * key), a0)

    # 2) choke + erosion + light AA blur
    a2 = np.clip((a1 - 0.15) / 0.75, 0, 1)
    if erode:
        a2 = cv2.erode(a2, np.ones((3, 3), np.uint8), iterations=erode)
    a2 = cv2.GaussianBlur(a2, (0, 0), 0.7)

    # 3) decontaminate edge colours using the original soft alpha
    a_safe = np.clip(a0, 0.05, 1)[..., None]
    F = np.clip((rgb - (1 - a_safe) * B) / a_safe, 0, 255)
    out_rgb = np.where((a0 < 0.98)[..., None], F, rgb)

    # 4) cut below a row
    if cut_below is not None:
        y0, y1 = (int(v) for v in cut_below)
        ramp = np.ones(a2.shape[0], np.float32)
        y0, y1 = max(0, y0), min(a2.shape[0], max(y1, y0 + 1))
        ramp[y0:y1] = np.linspace(1, 0, y1 - y0)
        ramp[y1:] = 0
        a2 = a2 * ramp[:, None]

    # 5) keep only components that are a meaningful part of the main object
    n, labels, stats, _ = cv2.connectedComponentsWithStats((a2 > 0.05).astype(np.uint8), 8)
    if n > 2:
        keep = 1 + np.argmax(stats[1:, cv2.CC_STAT_AREA])
        big = stats[:, cv2.CC_STAT_AREA] > 0.002 * stats[keep, cv2.CC_STAT_AREA]
        big[0] = False
        a2 = a2 * big[labels]

    return np.dstack([out_rgb, a2 * 255]).astype(np.uint8), B


def alpha_bbox(alpha, threshold=0.02 * 255):
    """(x0, y0, x1, y1) of pixels above threshold, as plain ints (JSON-safe)."""
    ys, xs = np.where(alpha > threshold)
    return int(xs.min()), int(ys.min()), int(xs.max()), int(ys.max())
