"""Cuts a drone's Blockbench model into the pieces it breaks into when it blows up, one glb each, burnt.

    python tools/debris.py <model.bbmodel> src/main/resources/assets/sfw/models/entity <prefix> [options]
"""
import argparse
import base64
import copy
import io
import json
import math
import re
import uuid as uuidlib

import numpy as np
from PIL import Image

import bb2glb
from bb2glb import face_quad

HEAVY = ("main", "motor", "battery", "engine")
LIGHT = ("eleron", "elevon", "prop", "servo", "stick", "guide", "vtx")


def groups_of(model):
    """Each top-level group's name and the element uuids under it, nested groups included."""
    names = {g["uuid"]: g["name"] for g in model.get("groups", [])}

    def collect(entry):
        uuids = []
        for child in entry.get("children", []):
            uuids += [child] if isinstance(child, str) else collect(child)
        return uuids

    return [(entry.get("name") or names.get(entry["uuid"], ""), collect(entry))
            for entry in model["outliner"] if isinstance(entry, dict)]


def is_mesh(element):
    return element.get("type") == "mesh"


def matrix_of(element):
    return bb2glb.euler_matrix(element.get("rotation", [0, 0, 0]) or [0, 0, 0])


def corners(element):
    origin = np.array(element.get("origin", [0, 0, 0]), float)
    m = matrix_of(element)
    if is_mesh(element):
        return np.array([m @ np.array(v, float) + origin for v in element["vertices"].values()])
    f, t = np.array(element["from"], float), np.array(element["to"], float)
    pts = [[x, y, z] for x in (f[0], t[0]) for y in (f[1], t[1]) for z in (f[2], t[2])]
    return np.array([m @ (np.array(p) - origin) + origin for p in pts])


def single_texture(model):
    """The model with every texture gathered into its largest one, the faces' UVs moved to follow."""
    textures = model["textures"]
    if len(textures) == 1:
        return model
    model = copy.deepcopy(model)
    textures = model["textures"]

    def load(t):
        return Image.open(io.BytesIO(base64.b64decode(t["source"].split(",", 1)[1]))).convert("RGBA")

    images = [load(t) for t in textures]
    main_index = max(range(len(textures)), key=lambda i: images[i].width * images[i].height)
    main = images[main_index]
    density = main.width / float(textures[main_index].get("uv_width") or main.width)
    offsets = {main_index: (0.0, 0.0)}
    for i, (t, image) in enumerate(zip(textures, images)):
        if i == main_index:
            continue
        own = image.width / float(t.get("uv_width") or image.width)
        if abs(own - density) > 1e-6:
            image = image.resize((round(image.width * density / own), round(image.height * density / own)),
                                 Image.NEAREST)
        spot = free_spot(main, image.width, image.height)
        if spot is None:
            grown = Image.new("RGBA", (main.width, main.height + image.height))
            grown.paste(main, (0, 0))
            spot = (0, main.height)
            main = grown
        main.paste(image, spot, image)
        offsets[i] = (spot[0] / density, spot[1] / density)
    for element in model["elements"]:
        for face in element["faces"].values():
            ref = face.get("texture")
            if ref is None:
                continue
            du, dv = offsets[int(ref)]
            uv = face["uv"]
            face["uv"] = ({k: [p[0] + du, p[1] + dv] for k, p in uv.items()} if isinstance(uv, dict)
                          else [uv[0] + du, uv[1] + dv, uv[2] + du, uv[3] + dv])
            face["texture"] = 0
    buffer = io.BytesIO()
    main.save(buffer, "PNG")
    merged = dict(textures[main_index], id="0", width=main.width, height=main.height,
                  uv_width=main.width / density, uv_height=main.height / density,
                  source="data:image/png;base64," + base64.b64encode(buffer.getvalue()).decode())
    model["textures"] = [merged]
    return model


def free_spot(image, w, h, step=8):
    alpha = np.asarray(image)[..., 3]
    for y in range(0, alpha.shape[0] - h + 1, step):
        for x in range(0, alpha.shape[1] - w + 1, step):
            if not alpha[y:y + h, x:x + w].any():
                return x, y
    return None


def cut(element, axis, at):
    """The element cut in two across {@code axis} at {@code at}: (below, above), either None if empty."""
    if is_mesh(element) or any(abs(a) > 1e-9 for a in (element.get("rotation") or [0, 0, 0])):
        return (element, None) if corners(element)[:, axis].mean() < at else (None, element)
    f, t = np.array(element["from"], float), np.array(element["to"], float)
    if not (f[axis] < at < t[axis]):
        return (element, None) if (f + t)[axis] / 2 < at else (None, element)
    halves = []
    for lo, hi in ((f[axis], at), (at, t[axis])):
        nf, nt = f.copy(), t.copy()
        nf[axis], nt[axis] = lo, hi
        part = copy.deepcopy(element)
        part["from"], part["to"] = nf.tolist(), nt.tolist()
        part["uuid"] = str(uuidlib.uuid4())
        for name in part["faces"]:
            part["faces"][name]["uv"] = remap(element, name, nf, nt)
        halves.append(part)
    return halves[0], halves[1]


def remap(element, name, nf, nt):
    """The UV a face of the cut-down cube takes: its corners found on the original face."""
    f, t = np.array(element["from"], float), np.array(element["to"], float)
    u1, v1, u2, v2 = element["faces"][name]["uv"]
    c0, c1, _, c3 = (np.array(c, float) for c in face_quad(f, t, name))
    e1, e2 = c3 - c0, c1 - c0
    n0, n1, _, n3 = (np.array(c, float) for c in face_quad(nf, nt, name))

    def uv(p):
        s = (p - c0) @ e1 / max(1e-9, e1 @ e1)
        r = (p - c0) @ e2 / max(1e-9, e2 @ e2)
        return u1 + s * (u2 - u1), v1 + r * (v2 - v1)

    a, b, c = uv(n0), uv(n3), uv(n1)
    return [round(a[0], 4), round(a[1], 4), round(b[0], 4), round(c[1], 4)]


def piece_model(model, elements):
    pts = np.concatenate([corners(e) for e in elements])
    lo, hi = pts.min(0), pts.max(0)
    base = np.array([(lo[0] + hi[0]) / 2, lo[1], (lo[2] + hi[2]) / 2])
    moved = []
    for e in elements:
        e = copy.deepcopy(e)
        if not is_mesh(e):
            e["from"] = (np.array(e["from"]) - base).tolist()
            e["to"] = (np.array(e["to"]) - base).tolist()
        e["origin"] = (np.array(e.get("origin", [0, 0, 0]), float) - base).tolist()
        moved.append(e)
    sub = {"meta": model.get("meta", {}), "resolution": model.get("resolution"), "textures": model["textures"],
           "elements": moved, "groups": [], "outliner": [e["uuid"] for e in moved]}
    return sub, base, hi - lo


SOOT = np.array((26, 23, 21), float)
SCORCH = np.array((92, 62, 40), float)
TEMPER = np.array((70, 74, 104), float)


def _lattice(q):
    h = (q[..., 0] * 73856093) ^ (q[..., 1] * 19349663) ^ (q[..., 2] * 83492791)
    h = (h ^ (h >> 13)) * 1274126177
    return (h & 0xFFFF) / 65535.0


def value_noise(p):
    """Smooth 3D value noise, 0..1, on a unit lattice."""
    i = np.floor(p).astype(np.int64)
    f = p - i
    f = f * f * (3.0 - 2.0 * f)
    out = 0.0
    for dx in (0, 1):
        for dy in (0, 1):
            for dz in (0, 1):
                w = (f[..., 0] if dx else 1 - f[..., 0]) * (f[..., 1] if dy else 1 - f[..., 1]) \
                    * (f[..., 2] if dz else 1 - f[..., 2])
                out = out + w * _lattice(i + np.array((dx, dy, dz)))
    return out


def burn(colour, p, edge):
    """The paint at points p after the fire: darkened, sooted in patches, scorched brown round them."""
    n = 0.55 * value_noise(p / 7.0) + 0.3 * value_noise(p / 3.0 + 17.0) + 0.15 * value_noise(p / 1.2 + 41.0)
    soot = np.clip((n - 0.43) / 0.28, 0.0, 1.0)
    soot = np.maximum(soot, np.clip(0.9 - edge * 1.8, 0.0, 0.9) * 0.5)
    scorch = np.clip(1.0 - np.abs(n - 0.36) / 0.12, 0.0, 1.0) * (1.0 - soot)
    temper = np.clip(1.0 - np.abs(n - 0.27) / 0.05, 0.0, 1.0) * 0.5 * (1.0 - soot)
    out = colour * 0.72
    out = out * (1 - scorch[..., None] * 0.7) + SCORCH * scorch[..., None] * 0.7
    out = out * (1 - temper[..., None]) + TEMPER * temper[..., None]
    out = out * (1 - soot[..., None] * 0.88) + SOOT * soot[..., None] * 0.88
    grit = (_lattice(np.floor(p * 2.0 + 1e-4).astype(np.int64)) - 0.5) * 14.0
    return np.clip(out + grit[..., None], 0, 255)


def face_triangles(element):
    """Every face as triangles of (uv, world point) corners, with whether the edges are real ones."""
    origin = np.array(element.get("origin", [0, 0, 0]), float)
    m = matrix_of(element)
    if not is_mesh(element):
        f, t = np.array(element["from"], float), np.array(element["to"], float)
        for name, face in element["faces"].items():
            if face.get("texture") is None:
                continue
            u1, v1, u2, v2 = face["uv"]
            q = [m @ (np.array(c, float) - origin) + origin for c in face_quad(f, t, name)]
            yield "quad", ((u1, v1), q[0]), ((u1, v2), q[1]), ((u2, v1), q[3])
        return
    verts = {k: m @ np.array(v, float) + origin for k, v in element["vertices"].items()}
    for face in element["faces"].values():
        if face.get("texture") is None:
            continue
        keys = [k for k in face.get("vertices", []) if k in verts and k in face["uv"]]
        if len(keys) < 3:
            continue
        if len(keys) == 4:
            centre = np.mean([face["uv"][k] for k in keys], axis=0)
            keys.sort(key=lambda k: math.atan2(face["uv"][k][1] - centre[1], face["uv"][k][0] - centre[0]))
        for i in range(1, len(keys) - 1):
            yield "tri", *((tuple(face["uv"][k]), verts[k]) for k in (keys[0], keys[i], keys[i + 1]))


def burnt_texture(model):
    texture = model["textures"][0]
    uw, uh = int(round(texture.get("uv_width") or 16)), int(round(texture.get("uv_height") or 16))
    image = Image.open(io.BytesIO(base64.b64decode(texture["source"].split(",", 1)[1]))).convert("RGBA")
    if image.size != (uw, uh):
        image = image.resize((uw, uh), Image.BOX)
    image = np.array(image).astype(float)
    done = np.zeros(image.shape[:2], bool)
    for element in model["elements"]:
        for kind, (uv0, p0), (uv1, p1), (uv2, p2) in face_triangles(element):
            if kind == "quad":
                (u1, v1), (_, v2), (u2, _) = uv0, uv1, uv2
                if abs(u2 - u1) < 1e-6 or abs(v2 - v1) < 1e-6:
                    continue
                x0, x1 = sorted((u1, u2))
                y0, y1 = sorted((v1, v2))
                x0, x1 = int(math.floor(x0 + 1e-6)), int(math.ceil(x1 - 1e-6))
                y0, y1 = int(math.floor(y0 + 1e-6)), int(math.ceil(y1 - 1e-6))
                gx, gy = np.meshgrid(np.arange(x0, x1) + 0.5, np.arange(y0, y1) + 0.5)
                s = np.clip((gx - u1) / (u2 - u1), 0, 1)
                r = np.clip((gy - v1) / (v2 - v1), 0, 1)
                world = p0 + s[..., None] * (p2 - p0) + r[..., None] * (p1 - p0)
                wu, wv = np.linalg.norm(p2 - p0), np.linalg.norm(p1 - p0)
                edge = np.minimum(np.minimum(s * wu, (1 - s) * wu), np.minimum(r * wv, (1 - r) * wv))
                mask = np.ones(s.shape, bool)
            else:
                (a, b, c) = (np.array(uv0, float), np.array(uv1, float), np.array(uv2, float))
                x0, y0 = np.floor(np.minimum(np.minimum(a, b), c)).astype(int)
                x1, y1 = np.ceil(np.maximum(np.maximum(a, b), c)).astype(int)
                gx, gy = np.meshgrid(np.arange(x0, x1) + 0.5, np.arange(y0, y1) + 0.5)
                den = (b[1] - c[1]) * (a[0] - c[0]) + (c[0] - b[0]) * (a[1] - c[1])
                if abs(den) < 1e-9:
                    continue
                w0 = ((b[1] - c[1]) * (gx - c[0]) + (c[0] - b[0]) * (gy - c[1])) / den
                w1 = ((c[1] - a[1]) * (gx - c[0]) + (a[0] - c[0]) * (gy - c[1])) / den
                w2 = 1 - w0 - w1
                mask = (w0 > -0.02) & (w1 > -0.02) & (w2 > -0.02)
                world = w0[..., None] * p0 + w1[..., None] * p1 + w2[..., None] * p2
                edge = np.full(w0.shape, 9.0)
            x0c, y0c = max(0, x0), max(0, y0)
            x1c, y1c = min(image.shape[1], x0 + mask.shape[1]), min(image.shape[0], y0 + mask.shape[0])
            if x1c <= x0c or y1c <= y0c:
                continue
            sl = (slice(y0c - y0, y1c - y0), slice(x0c - x0, x1c - x0))
            take = mask[sl] & ~done[y0c:y1c, x0c:x1c]
            region = image[y0c:y1c, x0c:x1c]
            burnt = burn(region[..., :3], world[sl], edge[sl])
            region[..., :3][take] = burnt[take]
            done[y0c:y1c, x0c:x1c] |= take
    out = Image.fromarray(image.round().astype(np.uint8), "RGBA")
    buffer = io.BytesIO()
    out.save(buffer, "PNG")
    return out, "data:image/png;base64," + base64.b64encode(buffer.getvalue()).decode()


def java_name(name):
    return re.sub(r"[^a-z0-9]+", "_", name.lower()).strip("_")


def reach_of(name):
    if any(word in name for word in HEAVY):
        return 0.5
    if any(word in name for word in LIGHT):
        return 1.0
    return 0.8


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("model")
    parser.add_argument("out")
    parser.add_argument("prefix")
    parser.add_argument("--split", action="append", default=[])
    parser.add_argument("--sides", action="append", default=[])
    parser.add_argument("--each", action="append", default=[])
    parser.add_argument("--drop", action="append", default=[])
    args = parser.parse_args()

    model = single_texture(json.load(open(args.model, encoding="utf-8")))
    burnt, source = burnt_texture(model)
    model["textures"] = [dict(model["textures"][0], source=source, width=burnt.size[0], height=burnt.size[1])]

    elements = {e["uuid"]: e for e in model["elements"]}
    every = np.concatenate([corners(e) for e in model["elements"] if e.get("export", True)])
    lo, hi = every.min(0), every.max(0)
    print("model bounds: centre x %.3f z %.3f, min y %.3f (sixteenths)" % ((lo[0] + hi[0]) / 2, (lo[2] + hi[2]) / 2, lo[1]))

    pieces = {}
    for name, uuids in groups_of(model):
        key = java_name(name)
        members = [elements[u] for u in uuids if elements[u].get("export", True)]
        if name in args.drop or key in args.drop:
            continue
        if name in args.split or key in args.split:
            left, right = [], []
            for e in members:
                below, above = cut(e, 0, 0.0)
                (left.append(below) if below is not None else None)
                (right.append(above) if above is not None else None)
            pieces[key + "_left"], pieces[key + "_right"] = left, right
        elif name in args.each or key in args.each:
            ordered = sorted(members, key=lambda e: (round(corners(e)[:, 0].mean(), 2), round(corners(e)[:, 2].mean(), 2)))
            for i, e in enumerate(ordered, 1):
                pieces["%s_%d" % (key, i)] = [e]
        elif name in args.sides or key in args.sides:
            for e in members:
                x = corners(e)[:, 0].mean()
                side = "left" if x < -1.0 else "right" if x > 1.0 else "middle"
                pieces.setdefault(key + "_" + side, []).append(e)
        else:
            pieces[key] = members

    for name, members in pieces.items():
        if not members:
            continue
        sub, base, size = piece_model(model, members)
        bb2glb.convert(sub, "%s/%s_debris_%s.glb" % (args.out, args.prefix, name))
        thin = min(size[0], size[2])
        lie = "NONE" if size[1] <= 2.5 * thin else ("X" if size[2] <= size[0] else "Z")
        print('        %s_%s(Airframe.%s, "%s_debris_%s", %.2fF, %.2fF, %.2fF, %.2fF, %.2fF, %.2fF, Lie.%s, %.2fF),' % (
            args.prefix.upper(), name.upper(), args.prefix.upper(), args.prefix, name,
            base[0], base[1], base[2], size[0], size[1], size[2], lie, reach_of(name)))


if __name__ == "__main__":
    main()
