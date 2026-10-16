"""
Backdrop treatment for the collage interior (step 5 of the asset pipeline).

The collage interior contains barber chairs, which compete with the foreground
chair of the scene. Until a chair-free, high-res backdrop is supplied:
  - crop to 16:9 (source rows 30..478 of the 797x542 crop, keeps shelves + floor)
  - left ~55%: strong blur + darken (the large foreground chair becomes a soft dark
    shape behind the text column, which is darkened by the page gradient anyway)
  - far chairs (middle): moderate blur + darken, reading as out-of-focus depth
  - right side (lit shelves): untouched
Coordinates are fractions of the 16:9 frame so they survive the x4 upscale.

Input:  up/backdrop.png (Real-ESRGAN x4 of assets-raw/crops/backdrop.png, 3188x2168)
Output: final/backdrop.png (3188x1793, 16:9)
usage: python backdrop.py <workdir>
"""
import os
import sys

import cv2
import numpy as np

WORK = sys.argv[1] if len(sys.argv) > 1 else "."
img = cv2.imread(os.path.join(WORK, "up", "backdrop.png")).astype(np.float32)

# 16:9 crop: source y 30..478 (x4 = 120..1912)
H_SRC, W = img.shape[:2]
top = 120
h = round(W * 9 / 16)
img = img[top : top + h]
H = img.shape[0]
yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
fx, fy = xx / W, yy / H


def smoothstep(e0, e1, x):
    t = np.clip((x - e0) / (e1 - e0), 0, 1)
    return t * t * (3 - 2 * t)


# Mask 1: left part (foreground chair), full strength until 0.50, fades out by 0.64.
left = 1 - smoothstep(0.50, 0.64, fx)
# Mask 2: soft ellipse over the two far chairs (x≈0.42–0.78, y≈0.31–0.80 of the frame).
cx, cy, rx, ry = 0.60, 0.56, 0.20, 0.26
far = 1 - smoothstep(0.75, 1.0, np.sqrt(((fx - cx) / rx) ** 2 + ((fy - cy) / ry) ** 2))

strong = cv2.GaussianBlur(img, (0, 0), 22) * 0.32
medium = cv2.GaussianBlur(img, (0, 0), 14) * 0.48

out = img.copy()
m_far = (far * (1 - left))[..., None]
out = out * (1 - m_far) + medium * m_far
m_left = left[..., None]
out = out * (1 - m_left) + strong * m_left

os.makedirs(os.path.join(WORK, "final"), exist_ok=True)
cv2.imwrite(os.path.join(WORK, "final", "backdrop.png"), np.clip(out, 0, 255).astype(np.uint8))
print("backdrop", W, "x", H)
