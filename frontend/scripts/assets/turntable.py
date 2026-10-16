"""
Turntable pipeline: GIF / APNG / animated WebP / video  ->  stabilised, transparent,
interpolated WebP image sequence + manifest for the scroll-driven chair rotation.

usage:
  python turntable.py <input> [--out public/scene/chair-turntable] [--poster public/scene/chair.webp]
                      [--target-frames 88] [--sizes 960,540] [--work <tmpdir>]
                      [--esrgan <realesrgan-ncnn-vulkan.exe>] [--rife <rife-ncnn-vulkan.exe>]
                      [--interp rife|minterpolate|none] [--from-work]

Any new input (higher-res video, different chair) works without code changes.
--from-work skips steps 1-6 and only re-exports (step 7) from an earlier run's work dir,
e.g. to try other --sizes / --quality without re-running the ML steps.
Binaries can also come from env vars REALESRGAN_BIN / RIFE_BIN. Video input and the
minterpolate fallback use the ffmpeg bundled with `imageio-ffmpeg`.

Steps
  1. extract all frames losslessly (PIL for GIF/APNG/WebP, ffmpeg for video)
  2. measure each frame on a rembg mask: floor line (lowest row), base-disc centre and width
     (longest run of pixels in the bottom band = the round base, ignores footrest feet)
  3. stabilise: fit those measurements over time with a straight line (robust to per-frame
     noise), then scale by disc width (rotation-invariant) and translate so the disc centre
     and floor line stay fixed. Height is deliberately NOT used: it changes with perspective.
  4. crop all frames to the union box, upscale x4 (Real-ESRGAN)
  5. interpolate to --target-frames with RIFE (fallback: ffmpeg minterpolate, or none)
     — target is rounded to (N-1)*k+1 so the original frames stay exact keyframes
  6. matte each frame (rembg BiRefNet) + the same edge cleanup as matte.py (cutout.py);
     everything below the floor line (floor shadow) is removed
  7. trim every frame to one common bounding box, grade like the still assets,
     export WebP sets (one per --sizes height) + manifest.json, optional poster = frame 0
"""
import argparse
import glob
import json
import os
import shutil
import subprocess
import sys
import tempfile
import time

import cv2
import numpy as np
from PIL import Image, ImageSequence

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from cutout import alpha_bbox, clean_cutout  # noqa: E402

UPSCALE = 4


def log(*a):
    print("[turntable]", *a, flush=True)


# ─────────────────────────────────────────────────────────── 1. extract ──

def extract_frames(src, out_dir):
    os.makedirs(out_dir, exist_ok=True)
    ext = os.path.splitext(src)[1].lower()
    if ext in (".gif", ".png", ".apng", ".webp"):
        im = Image.open(src)
        frames = [f.copy().convert("RGB") for f in ImageSequence.Iterator(im)]
        for i, f in enumerate(frames):
            f.save(os.path.join(out_dir, f"{i:04d}.png"))  # PNG = lossless
    else:
        import imageio_ffmpeg

        ff = imageio_ffmpeg.get_ffmpeg_exe()
        subprocess.run([ff, "-v", "error", "-i", src, "-vsync", "0", os.path.join(out_dir, "%04d.png")], check=True)
        # ffmpeg numbers from 1 -> renumber from 0
        for i, p in enumerate(sorted(glob.glob(os.path.join(out_dir, "*.png")))):
            os.replace(p, os.path.join(out_dir, f"t{i:04d}.png"))
        for p in glob.glob(os.path.join(out_dir, "t*.png")):
            os.replace(p, os.path.join(out_dir, os.path.basename(p)[1:]))
    files = sorted(glob.glob(os.path.join(out_dir, "*.png")))
    log(f"extracted {len(files)} frames from {os.path.basename(src)}")
    return files


# ─────────────────────────────────────────────────────────── 2. measure ──

def longest_run(row):
    best, start = (0, 0, 0), None
    for i, v in enumerate(list(row) + [False]):
        if v and start is None:
            start = i
        if not v and start is not None:
            if i - start > best[0]:
                best = (i - start, start, i - 1)
            start = None
    return best


def measure(mask):
    """floor line, base-disc centre x and width from a binary mask"""
    ys, xs = np.where(mask)
    y0, y1 = ys.min(), ys.max()
    h = y1 - y0 + 1
    centres, widths = [], []
    for y in range(y1 - max(3, int(h * 0.045)), y1 - 1):
        length, s0, s1 = longest_run(mask[y])
        centres.append((s0 + s1) / 2)
        widths.append(length)
    return {"bottom": float(y1), "cx": float(np.median(centres)), "disc": float(max(widths)), "top": float(y0)}


def robust_line(values):
    """least-squares line over frame index, then one pass rejecting outliers (>2.5 sigma)"""
    x = np.arange(len(values), dtype=np.float64)
    v = np.asarray(values, dtype=np.float64)
    p = np.polyfit(x, v, 1)
    r = v - np.polyval(p, x)
    keep = np.abs(r) <= 2.5 * (r.std() + 1e-6)
    p = np.polyfit(x[keep], v[keep], 1)
    return np.polyval(p, x)


# ──────────────────────────────────────────────────── binaries / helpers ──

def run_dir_tool(cmd, what):
    t = time.time()
    res = subprocess.run(cmd, capture_output=True, text=True)
    if res.returncode != 0:
        raise RuntimeError(f"{what} failed:\n{res.stderr[-2000:]}")
    log(f"{what} done in {time.time() - t:.0f}s")


def grade(rgb):
    """same look as export-webp.mjs: a touch darker, -10% saturation, warm"""
    rgb = rgb * 0.95
    lum = (0.2126 * rgb[..., 0] + 0.7152 * rgb[..., 1] + 0.0722 * rgb[..., 2])[..., None]
    rgb = lum + (rgb - lum) * 0.9
    return np.clip(rgb * np.array([1.03, 1.0, 0.92]), 0, 255)


# ─────────────────────────────────────────────────────────────── main ──

def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("input")
    ap.add_argument("--out", default="public/scene/chair-turntable")
    ap.add_argument("--poster", default=None, help="also write frame 0 here (e.g. public/scene/chair.webp)")
    ap.add_argument("--target-frames", type=int, default=88)
    ap.add_argument("--sizes", default="960,540", help="output heights, comma separated (largest first)")
    ap.add_argument("--quality", type=int, default=80)
    ap.add_argument("--work", default=os.path.join(tempfile.gettempdir(), "turntable-work"))
    ap.add_argument("--esrgan", default=os.environ.get("REALESRGAN_BIN"))
    ap.add_argument("--rife", default=os.environ.get("RIFE_BIN"))
    ap.add_argument("--rife-model", default="rife-v4.6")
    ap.add_argument("--interp", choices=["rife", "minterpolate", "none"], default="rife")
    ap.add_argument("--model", default="birefnet-general-lite", help="rembg model")
    ap.add_argument("--from-work", action="store_true", help="only re-export from an existing --work dir")
    args = ap.parse_args()

    work = args.work
    d = {k: os.path.join(work, k) for k in ("src", "stab", "up", "interp", "rgba")}
    if args.from_work:
        meta_path = os.path.join(work, "meta.json")
        if os.path.exists(meta_path):
            with open(meta_path) as fh:
                meta = json.load(fh)
        else:  # runs from before meta.json existed: infer
            n_src = len(glob.glob(os.path.join(d["src"], "*.png")))
            n_out = len(glob.glob(os.path.join(d["rgba"], "*.png")))
            W, H = Image.open(sorted(glob.glob(os.path.join(d["src"], "*.png")))[0]).size
            meta = {"frames": n_src, "size": [W, H], "interp": args.interp if n_out > n_src else "none", "crossfade": n_out < args.target_frames and n_out == n_src}
        log(f"re-exporting from {work} ({meta})")
        export(args, d, meta)
        return

    from rembg import new_session, remove

    if os.path.exists(work):
        shutil.rmtree(work)
    for p in d.values():
        os.makedirs(p, exist_ok=True)
    session = new_session(args.model)

    # 1. extract
    files = extract_frames(args.input, d["src"])
    frames = [np.asarray(Image.open(f).convert("RGB")) for f in files]
    n = len(frames)
    H, W = frames[0].shape[:2]

    # 2. measure on low-res masks
    masks = [np.asarray(remove(Image.fromarray(f), session=session, only_mask=True)) > 128 for f in frames]
    m = [measure(mk) for mk in masks]
    for k in ("bottom", "cx", "disc"):
        raw = np.array([x[k] for x in m])
        log(f"{k:6s} raw min/max {raw.min():.1f}/{raw.max():.1f}  spread {raw.max() - raw.min():.1f}px")

    # 3. stabilise (fit trends, then map every frame onto the reference)
    fb, fc, fw = robust_line([x["bottom"] for x in m]), robust_line([x["cx"] for x in m]), robust_line([x["disc"] for x in m])
    ref_b, ref_c, ref_w = float(np.median(fb)), float(np.median(fc)), float(np.median(fw))
    mats = []
    for i in range(n):
        s = ref_w / fw[i]
        mats.append(np.array([[s, 0, ref_c - s * fc[i]], [0, s, ref_b - s * fb[i]]], np.float32))
    log(f"stabilise: scale range {min(ref_w / w for w in fw):.4f}..{max(ref_w / w for w in fw):.4f}, "
        f"shift x {min(ref_c - c for c in fc):+.1f}..{max(ref_c - c for c in fc):+.1f}px, "
        f"y {min(ref_b - b for b in fb):+.1f}..{max(ref_b - b for b in fb):+.1f}px")

    # union box of the stabilised masks (+ margin) -> crop region
    union = np.zeros((H, W), bool)
    for mk, M in zip(masks, mats):
        union |= cv2.warpAffine(mk.astype(np.uint8), M, (W, H), flags=cv2.INTER_NEAREST) > 0
    ux0, uy0, ux1, uy1 = alpha_bbox(union.astype(np.uint8) * 255, 0)
    mg = 12
    cx0, cy0, cx1, cy1 = max(0, ux0 - mg), max(0, uy0 - mg), min(W, ux1 + mg + 1), min(H, uy1 + mg + 1)
    for i, (f, M) in enumerate(zip(frames, mats)):
        st = cv2.warpAffine(f, M, (W, H), flags=cv2.INTER_LANCZOS4, borderMode=cv2.BORDER_REPLICATE)
        Image.fromarray(st[cy0:cy1, cx0:cx1]).save(os.path.join(d["stab"], f"{i:04d}.png"))
    floor_y = (ref_b - cy0 + 1) * UPSCALE  # floor line in upscaled crop coordinates

    # 4. upscale x4
    if not args.esrgan:
        raise SystemExit("Real-ESRGAN binary missing: pass --esrgan or set REALESRGAN_BIN")
    run_dir_tool([args.esrgan, "-i", d["stab"], "-o", d["up"], "-n", "realesrgan-x4plus", "-f", "png"], f"Real-ESRGAN x{UPSCALE} ({n} frames)")

    # 5. interpolate
    seq_dir, crossfade = d["up"], False
    if n < args.target_frames and args.interp != "none":
        k = max(1, round((args.target_frames - 1) / (n - 1)))
        target = (n - 1) * k + 1  # keeps the original frames as exact keyframes
        if args.interp == "rife":
            if not args.rife:
                raise SystemExit("RIFE binary missing: pass --rife, set RIFE_BIN, or use --interp minterpolate/none")
            model = os.path.join(os.path.dirname(args.rife), args.rife_model)
            run_dir_tool([args.rife, "-i", d["up"], "-o", d["interp"], "-n", str(target), "-m", model, "-f", "%04d.png"], f"RIFE {n}->{target}")
        else:
            import imageio_ffmpeg

            ff = imageio_ffmpeg.get_ffmpeg_exe()
            run_dir_tool([ff, "-v", "error", "-framerate", "10", "-i", os.path.join(d["up"], "%04d.png"),
                          "-vf", f"minterpolate=fps={10 * k}:mi_mode=mci:mc_mode=aobmc:vsbmc=1", os.path.join(d["interp"], "%04d.png")],
                         f"ffmpeg minterpolate {n}->~{target}")
        seq_dir = d["interp"]
    elif n < args.target_frames:
        crossfade = True  # runtime blends the two nearest frames instead
    seq = sorted(glob.glob(os.path.join(seq_dir, "*.png")))
    log(f"sequence: {len(seq)} frames ({'crossfade at runtime' if crossfade else 'no runtime crossfade needed'})")
    meta = {"frames": n, "size": [W, H], "interp": args.interp if not crossfade else "none", "crossfade": crossfade}
    with open(os.path.join(work, "meta.json"), "w") as fh:
        json.dump(meta, fh)

    # 6. matte every frame
    t = time.time()
    for i, p in enumerate(seq):
        rgb = np.asarray(Image.open(p).convert("RGB")).astype(np.float32)
        a0 = np.asarray(remove(Image.fromarray(rgb.astype(np.uint8)), session=session, only_mask=True)).astype(np.float32) / 255
        rgba, _ = clean_cutout(rgb, a0, cut_below=(floor_y, floor_y + 6))
        Image.fromarray(rgba, "RGBA").save(os.path.join(d["rgba"], f"{i:04d}.png"))
        if i % 10 == 0:
            log(f"matte {i + 1}/{len(seq)}  ({time.time() - t:.0f}s)")

    export(args, d, meta)


def export(args, d, meta):
    """7. common trim + grade + export WebP sets + manifest (+ poster)"""
    rgbas = [np.asarray(Image.open(p)) for p in sorted(glob.glob(os.path.join(d["rgba"], "*.png")))]
    box = [10 ** 9, 10 ** 9, -1, -1]
    for r in rgbas:
        x0, y0, x1, y1 = alpha_bbox(r[..., 3])
        box = [min(box[0], x0), min(box[1], y0), max(box[2], x1), max(box[3], y1)]
    pad = 8
    bx0, by0 = max(0, box[0] - pad), max(0, box[1] - pad)
    bx1, by1 = min(rgbas[0].shape[1], box[2] + pad + 1), min(rgbas[0].shape[0], box[3] + pad + 1)
    mw, mh = int(bx1 - bx0), int(by1 - by0)

    if os.path.exists(args.out):
        shutil.rmtree(args.out)
    sizes = sorted((int(s) for s in args.sizes.split(",")), reverse=True)
    sets, total = [], 0
    for h in sizes:
        h = min(h, mh)
        w = round(mw * h / mh)
        sd = os.path.join(args.out, str(h))
        os.makedirs(sd, exist_ok=True)
        bytes_ = 0
        for i, r in enumerate(rgbas):
            c = r[by0:by1, bx0:bx1].astype(np.float32)
            c[..., :3] = grade(c[..., :3])
            img = Image.fromarray(c.astype(np.uint8), "RGBA").resize((w, h), Image.LANCZOS)
            fp = os.path.join(sd, f"{i:03d}.webp")
            img.save(fp, "WEBP", quality=args.quality, alpha_quality=90, method=6)
            bytes_ += os.path.getsize(fp)
            if i == 0 and args.poster and h == sizes[0]:
                img.save(args.poster, "WEBP", quality=85, alpha_quality=90, method=6)
        sets.append({"height": int(h), "width": int(w), "dir": str(h), "bytes": int(bytes_)})
        total += bytes_
        log(f"set {w}x{h}: {bytes_ / 1024 / 1024:.2f} MB")

    manifest = {
        "frames": len(rgbas),
        "width": mw,
        "height": mh,
        "pattern": "{dir}/{index:03d}.webp",
        "crossfade": bool(meta["crossfade"]),
        "sets": sets,
        "source": {"file": os.path.basename(args.input), "frames": int(meta["frames"]), "size": [int(v) for v in meta["size"]], "interp": meta["interp"]},
        "generated": time.strftime("%Y-%m-%d %H:%M"),
    }
    with open(os.path.join(args.out, "manifest.json"), "w") as fh:
        json.dump(manifest, fh, indent=2)
    log(f"manifest: {manifest['frames']} frames, master {mw}x{mh}, total {total / 1024 / 1024:.2f} MB")
    if args.poster:
        log(f"poster (frame 0) -> {args.poster}")


if __name__ == "__main__":
    main()
