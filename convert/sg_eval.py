"""Quality checks for TripoSG speed and size changes, run on the CPU against the unchanged pipeline.

Every change must keep the shape the full pipeline makes from the same picture and noise. Per
picture this writes, for the baseline and each variant: the occupancy grid's surface as .glb, a
rendered preview .png, and in metrics.json the agreement with the baseline:

  iou      intersection over union of the inside (logit > 0) on the final 256^3 grid; 1 = same
  chamfer  mean distance between the two surfaces, in % of the object's size; 0 = same

  guidance   classifier-free guidance on every step vs. only while sigma is inside an interval
             (Kynkaenniemi et al. 2024): skipped steps run only the image branch
  field      the shape-extraction shell of the last level: full (coarse margin) vs. thin (only
             the cells the surface crosses), on the same latents; also counts the query points
  axes       which side of the shape grid faces the camera of the input picture: the silhouette
             seen from +Z, -Z, +X and -X (Y up, not mirrored) against the picture's object mask;
             the app paints TripoSG meshes from the picture along that direction
  int8       DiT parts quantized to int8 weights / 16-bit activations (QDQ ONNX from
             quantize_triposg.py, run with ONNX Runtime on the CPU) vs. the float parts

  python sg_eval.py guidance --weights W --image a.png --isnet isnet.onnx --out out \
      --intervals 0.1-0.8,0.15-1,0-0.85
  python sg_eval.py int8 --weights W --image a.png --isnet isnet.onnx --qdq qdq_dir --out out
"""
import argparse
import json
import os
import sys
import time

import numpy as np
import torch

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import export_triposg as et  # noqa: E402

GUIDANCE = 7.0


def encode(pipe, image, isnet):
    dc = et.dino_config(pipe)
    img = et.preprocess(image, dc["resize_shortest"], dc["crop"], isnet)
    x = (np.asarray(img, np.float32) * dc["rescale"] - dc["mean"]) / dc["std"]
    x = torch.from_numpy(x.transpose(2, 0, 1)[None].astype(np.float32))
    with torch.no_grad():
        return pipe.image_encoder_dinov2(x).last_hidden_state, img


def torch_dit(pipe):
    parts = [et.DitPart(pipe.transformer, lo, hi) for lo, hi in et.DIT_PARTS]
    return lambda lat, temb, cond: et.run_chain(parts, lat, temb, cond)


def onnx_dit(pipe, folder):
    """The DiT as ONNX parts (sg_dit{i}.onnx in folder), fed by name like the app does."""
    import onnxruntime as ort
    parts = [et.DitPart(pipe.transformer, lo, hi) for lo, hi in et.DIT_PARTS]
    so = ort.SessionOptions()
    sessions = [ort.InferenceSession(os.path.join(folder, f"sg_dit{i}.onnx"), so, providers=["CPUExecutionProvider"])
                for i in range(len(parts))]

    def run(lat, temb, cond):
        named, h = {}, None
        for p, s in zip(parts, sessions):
            feeds = {}
            for name in p.input_names():
                v = {"latents": lat, "temb": temb, "cond": cond, "hidden_in": h}.get(name, named.get(name))
                feeds[name] = v.numpy() if torch.is_tensor(v) else v
            out = s.run(p.output_names(), feeds)
            if p.hi == p.n - 1:
                return torch.from_numpy(out[0])
            for name, v in zip(p.output_names(), out):
                if name == "hidden":
                    h = v
                else:
                    named[name] = v
    return run


def sample(pipe, cond, dit, steps, seed, guided):
    """Flow-matching Euler sampler, one branch per call (batch 1, as on the phone).
    guided(sigma) says whether this step runs the unconditional branch for guidance."""
    sig = et.sigmas_for(steps, float(pipe.scheduler.config.get("shift", 1.0)))
    c = pipe.transformer.config
    lat = torch.from_numpy(np.random.default_rng(seed).standard_normal((1, et.N_TOKENS, c.in_channels)).astype(np.float32))
    zeros = torch.zeros_like(cond)
    passes = 0
    t0 = time.time()
    for i in range(steps):
        temb = torch.from_numpy(et.timestep_sin([sig[i] * 1000], c.width))
        with torch.no_grad():
            vc = dit(lat, temb, cond)
            passes += 1
            if guided(sig[i]):
                vu = dit(lat, temb, zeros)
                passes += 1
                v = vu + GUIDANCE * (vc - vu)
            else:
                v = vc
        lat = lat + float(sig[i] - sig[i + 1]) * v
        print(f"  step {i + 1}/{steps} {time.time() - t0:.0f}s", flush=True)
    return lat, passes


def extract(query, res0=64, levels=2, thin_last=False):
    """Like export_triposg.extract (coarse grid, then refine near the surface), as the app does it;
    thin_last: the last level skips the coarse margin."""
    from scipy.ndimage import binary_dilation, zoom
    r = res0
    g = np.linspace(-et.BOUNDS, et.BOUNDS, r + 1, dtype=np.float32)
    vals = query(np.stack(np.meshgrid(g, g, g, indexing="ij"), -1).reshape(-1, 3)).reshape(r + 1, r + 1, r + 1)
    counts = [vals.size]
    for level in range(levels):
        near = np.abs(vals) < 0.95
        s = vals > 0
        for ax in range(3):
            for d in (1, -1):
                near |= s != np.roll(s, d, ax)
        if not (thin_last and level == levels - 1):
            near = binary_dilation(near, iterations=1)
        fine = zoom(vals, (2 * r + 1) / (r + 1), order=1)
        mask = binary_dilation(zoom(near.astype(np.uint8), (2 * r + 1) / (r + 1), order=0).astype(bool), iterations=1)
        r *= 2
        g = np.linspace(-et.BOUNDS, et.BOUNDS, r + 1, dtype=np.float32)
        idx = np.argwhere(mask)
        fine[mask] = query(g[idx])
        vals = fine
        counts.append(len(idx))
    return vals, r, counts


def decode(pipe, lat, thin_last=False, counts=None):
    with torch.no_grad():
        kv = et.VaeKv(pipe.vae)(lat)
    q = et.VaeQuery(pipe.vae)

    def query(p):
        res = []
        for s in range(0, len(p), et.QUERY_CHUNK):
            e = torch.from_numpy(et.point_embed(p[None, s:s + et.QUERY_CHUNK], pipe.vae.config.embed_frequency))
            with torch.no_grad():
                res.append(q(kv, e)[0, :, 0].numpy())
        return np.concatenate(res)

    vals, r, n = extract(query, thin_last=thin_last)
    if counts is not None:
        counts.extend(n)
    return vals, r


def surface(vals, r):
    from skimage import measure
    if vals.max() <= 0 or vals.min() >= 0:
        return np.zeros((0, 3), np.float32), np.zeros((0, 3), np.int64)
    v, f, _, _ = measure.marching_cubes(vals, 0.0)
    return (v / r * 2 * et.BOUNDS - et.BOUNDS).astype(np.float32), f


def compare(a, b):
    """IoU of the inside, and Chamfer distance between the surfaces in % of the object's size."""
    ia, ib = a[0] > 0, b[0] > 0
    union = np.logical_or(ia, ib).sum()
    iou = float(np.logical_and(ia, ib).sum() / union) if union else 1.0
    va, vb = surface(*a)[0], surface(*b)[0]
    if len(va) == 0 or len(vb) == 0:
        return {"iou": iou, "chamfer_pct": None}
    from scipy.spatial import cKDTree
    rng = np.random.default_rng(0)
    pa = va[rng.choice(len(va), min(len(va), 50000), replace=False)]
    pb = vb[rng.choice(len(vb), min(len(vb), 50000), replace=False)]
    d = (cKDTree(pb).query(pa)[0].mean() + cKDTree(pa).query(pb)[0].mean()) / 2
    size = float(np.linalg.norm(va.max(0) - va.min(0)))
    return {"iou": round(iou, 4), "chamfer_pct": round(float(d / size * 100), 3)}


def save(vals, r, out, name):
    import trimesh
    v, f = surface(vals, r)
    if len(v):
        trimesh.Trimesh(v, f).export(os.path.join(out, f"{name}.glb"))
        render(v, f, os.path.join(out, f"{name}.png"), name)


def render(v, f, path, title):
    """Three views with flat shading, for looking at the shapes side by side."""
    import matplotlib
    matplotlib.use("Agg")
    import matplotlib.pyplot as plt
    from mpl_toolkits.mplot3d.art3d import Poly3DCollection
    if len(f) > 60000:
        f = f[np.random.default_rng(0).choice(len(f), 60000, replace=False)]
    tri = v[f]
    n = np.cross(tri[:, 1] - tri[:, 0], tri[:, 2] - tri[:, 0])
    n /= np.linalg.norm(n, axis=1, keepdims=True) + 1e-9
    light = np.array([0.4, 0.5, 0.75])
    shade = np.clip(n @ (light / np.linalg.norm(light)), 0.15, 1)
    fig = plt.figure(figsize=(12, 4.2))
    for k, (el, az) in enumerate([(15, -60), (15, 30), (60, -120)]):
        ax = fig.add_subplot(1, 3, k + 1, projection="3d")
        ax.add_collection3d(Poly3DCollection(tri[:, :, [0, 2, 1]], facecolors=plt.cm.Greys(0.2 + 0.7 * shade), linewidths=0))
        lo, hi = v.min(), v.max()
        ax.set_xlim(lo, hi); ax.set_ylim(lo, hi); ax.set_zlim(lo, hi)
        ax.view_init(el, az)
        ax.set_axis_off()
    fig.suptitle(title)
    fig.savefig(path, dpi=80, bbox_inches="tight")
    plt.close(fig)


def parse_intervals(s):
    out = []
    for item in s.split(","):
        lo, hi = (float(x) for x in item.split("-"))
        out.append((lo, hi))
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("mode", choices=["guidance", "int8", "field", "axes"])
    ap.add_argument("--weights", required=True)
    ap.add_argument("--image", required=True)
    ap.add_argument("--isnet")
    ap.add_argument("--out", required=True)
    ap.add_argument("--steps", type=int, default=25)
    ap.add_argument("--seed", type=int, default=0)
    ap.add_argument("--intervals", default="0.1-0.8,0.15-1,0-0.85")
    ap.add_argument("--qdq", help="folder with the quantized sg_dit{i}.onnx parts")
    a = ap.parse_args()
    os.makedirs(a.out, exist_ok=True)
    torch.manual_seed(0)
    pipe = et.load_pipeline(a.weights)
    cond, img = encode(pipe, a.image, a.isnet)
    img.save(os.path.join(a.out, "input.png"))
    results = {"image": os.path.basename(a.image), "steps": a.steps, "seed": a.seed, "variants": {}}

    def run(name, dit, guided):
        t0 = time.time()
        print(f"{name}:", flush=True)
        lat, passes = sample(pipe, cond, dit, a.steps, a.seed, guided)
        vals, r = decode(pipe, lat)
        save(vals, r, a.out, name)
        return (vals, r), {"dit_passes": passes, "seconds": round(time.time() - t0)}

    if a.mode == "axes":
        lat, _ = sample(pipe, cond, torch_dit(pipe), a.steps, a.seed, lambda s: True)
        vals, r = decode(pipe, lat)
        inside = vals < 0 if vals[0, 0, 0] > 0 else vals > 0  # the grid corner is outside
        pic = np.asarray(img.convert("RGB"), np.float32)
        mask = (pic < 240).any(-1)  # object on the white background of the prepared picture
        results["inside_pct"] = round(float(inside.mean() * 100), 2)
        results["views"] = {}
        # Columns run along the camera's right vector, rows along -Y (pictures are top-down).
        views = {"+Z": (inside.any(2), False), "-Z": (inside.any(2), True),
                 "+X": (inside.any(0).T, True), "-X": (inside.any(0).T, False)}
        from PIL import Image
        for name, (m2, flip) in views.items():  # m2[col, y]
            sil = m2.T[::-1]  # rows: y from top
            if flip:
                sil = sil[:, ::-1]
            ys, xs = np.nonzero(sil); my, mx = np.nonzero(mask)
            crop = sil[ys.min():ys.max() + 1, xs.min():xs.max() + 1]
            box = (mx.min(), my.min(), mx.max() + 1, my.max() + 1)
            fit = np.asarray(Image.fromarray(crop.astype(np.uint8) * 255).resize((box[2] - box[0], box[3] - box[1]))) > 127
            full = np.zeros_like(mask); full[box[1]:box[3], box[0]:box[2]] = fit
            iou = float((full & mask).sum() / max(1, (full | mask).sum()))
            results["views"][name] = round(iou, 4)
            Image.fromarray((np.stack([mask, full, full & mask], -1) * 255).astype(np.uint8)).save(os.path.join(a.out, f"view_{name}.png"))
        results["best"] = max(results["views"], key=results["views"].get)
        with open(os.path.join(a.out, "metrics.json"), "w") as f:
            json.dump(results, f, indent=1)
        print(json.dumps(results, indent=1))
        return
    if a.mode == "field":
        # Same latents, two ways of extracting the surface; the phone's 10 steps.
        lat, _ = sample(pipe, cond, torch_dit(pipe), a.steps, a.seed, lambda s: True)
        shapes = {}
        for name, thin in (("baseline", False), ("thin", True)):
            t0, n = time.time(), []
            vals, r = decode(pipe, lat, thin, n)
            save(vals, r, a.out, name)
            shapes[name] = (vals, r)
            results["variants"][name] = {"dit_passes": 0, "seconds": round(time.time() - t0), "query_points": n,
                                         "query_total": int(sum(n))}
        results["variants"]["thin"].update(compare(shapes["baseline"], shapes["thin"]))
        with open(os.path.join(a.out, "metrics.json"), "w") as f:
            json.dump(results, f, indent=1)
        print(json.dumps(results, indent=1))
        return
    base, info = run("baseline", torch_dit(pipe), lambda s: True)
    results["variants"]["baseline"] = info
    if a.mode == "guidance":
        for lo, hi in parse_intervals(a.intervals):
            name = f"guidance_{lo:g}-{hi:g}"
            got, info = run(name, torch_dit(pipe), lambda s, lo=lo, hi=hi: lo <= s <= hi)
            info.update(compare(base, got))
            results["variants"][name] = info
            print(name, info, flush=True)
    else:
        got, info = run("int8", onnx_dit(pipe, a.qdq), lambda s: True)
        info.update(compare(base, got))
        results["variants"]["int8"] = info
        print("int8", info, flush=True)
    with open(os.path.join(a.out, "metrics.json"), "w") as f:
        json.dump(results, f, indent=1)
    print(json.dumps(results, indent=1))


if __name__ == "__main__":
    main()
