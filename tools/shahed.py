"""Carries the Shahed's paint over onto a remodelled Shahed, and exports the result as GeckoLib geometry.

    python tools/shahed.py <new.bbmodel> <old shahed.geo.json> <old shahed.png> <assets/sfw> [<textured copy .bbmodel>]
    python tools/shahed.py export <textured .bbmodel> <assets/sfw>     (a model that already has its texture)
"""
import base64
import io
import json
import math
import sys

import numpy as np
from PIL import Image

from bb2glb import FACE_DIRS, euler_matrix, face_quad

SCALE = 2
FALLBACK = (190, 193, 200, 255)


def old_elements(geo):
    """The old geometry turned back into Blockbench elements (the exporter's sums, run backwards)."""
    out = []
    for bone in geo["bones"]:
        if any(abs(a) > 1e-9 for a in bone.get("rotation", [0, 0, 0])):
            sys.exit("bone %s is rotated; not handled" % bone["name"])
        for c in bone.get("cubes", []):
            o, s = c["origin"], c["size"]
            f = [-(o[0] + s[0]), o[1], o[2]]
            t = [f[0] + s[0], f[1] + s[1], f[2] + s[2]]
            r = c.get("rotation", [0, 0, 0])
            p = c.get("pivot", [0, 0, 0])
            faces = {}
            uv = c["uv"]
            if isinstance(uv, list):
                u, v = uv
                sx, sy, sz = (math.floor(a) for a in s)
                faces = {
                    "north": [u + sz, v + sz, u + sz + sx, v + sz + sy],
                    "east": [u, v + sz, u + sz, v + sz + sy],
                    "south": [u + 2 * sz + sx, v + sz, u + 2 * sz + 2 * sx, v + sz + sy],
                    "west": [u + sz + sx, v + sz, u + 2 * sz + sx, v + sz + sy],
                    "up": [u + sz + sx, v + sz, u + sz, v],
                    "down": [u + sz + 2 * sx, v, u + sz + sx, v + sz],
                }
            else:
                for name, face in uv.items():
                    (fu, fv), (su, sv) = face["uv"], face["uv_size"]
                    if name in ("up", "down"):
                        faces[name] = [fu + su, fv + sv, fu, fv]
                    else:
                        faces[name] = [fu, fv, fu + su, fv + sv]
            out.append({"from": f, "to": t, "rotation": [-r[0], -r[1], r[2]], "origin": [-p[0], p[1], p[2]],
                        "faces": {k: {"uv": v} for k, v in faces.items()}})
    return out


def world_faces(element):
    """Each face of an element in world space: corners c0, c1 (down the v edge), c3 (along u), normal, uv."""
    f = np.array(element["from"], float)
    t = np.array(element["to"], float)
    origin = np.array(element.get("origin", [0, 0, 0]), float)
    m = euler_matrix(element.get("rotation", [0, 0, 0]) or [0, 0, 0])
    faces = []
    for name, face in element["faces"].items():
        if face.get("texture", 0) is None:
            continue
        q = [m @ (np.array(c, float) - origin) + origin for c in face_quad(f, t, name)]
        faces.append({"name": name, "c0": q[0], "c1": q[1], "c3": q[3], "n": m @ np.array(FACE_DIRS[name], float),
                      "uv": face["uv"]})
    return faces


def bbox(element):
    pts = []
    for face in world_faces(element):
        pts += [face["c0"], face["c1"], face["c3"], face["c1"] + face["c3"] - face["c0"]]
    pts = np.array(pts)
    return pts.min(0), pts.max(0)


class OldFace:
    """One face of the old model, ready to be asked what colour it shows at a point."""

    def __init__(self, face, texture):
        self.n = face["n"] / max(1e-9, np.linalg.norm(face["n"]))
        self.c0 = face["c0"]
        self.e1 = face["c3"] - face["c0"]
        self.e2 = face["c1"] - face["c0"]
        self.uv = face["uv"]
        a = np.stack([self.e1, self.e2, self.n], 1)
        self.ok = abs(np.linalg.det(a)) > 1e-9
        self.inv = np.linalg.inv(a) if self.ok else None
        h, w = texture.shape[:2]
        u1, v1, u2, v2 = self.uv
        self.px0, self.px1 = int(math.floor(min(u1, u2) + 1e-6)), int(math.ceil(max(u1, u2) - 1e-6))
        self.py0, self.py1 = int(math.floor(min(v1, v2) + 1e-6)), int(math.ceil(max(v1, v2) - 1e-6))
        self.px0, self.px1 = max(0, self.px0), min(w, max(self.px1, self.px0 + 1))
        self.py0, self.py1 = max(0, self.py0), min(h, max(self.py1, self.py0 + 1))
        rect = texture[self.py0:self.py1, self.px0:self.px1]
        self.rect = rect
        self.filled = fill_transparent(rect)

    def locate(self, p):
        """(s, t, d) for points p: along u, along v, and off the face."""
        return (p - self.c0) @ self.inv.T

    def texel(self, s, t):
        u1, v1, u2, v2 = self.uv
        u = u1 + s * (u2 - u1)
        v = v1 + t * (v2 - v1)
        x = np.clip(np.floor(u).astype(int), self.px0, self.px1 - 1) - self.px0
        y = np.clip(np.floor(v).astype(int), self.py0, self.py1 - 1) - self.py0
        return x, y


def fill_transparent(rect):
    """The rect with every see-through texel taken from its nearest painted one (None if none is painted)."""
    alpha = rect[..., 3] > 0
    if not alpha.any():
        return None
    if alpha.all():
        return rect.copy()
    ys, xs = np.nonzero(alpha)
    gy, gx = np.mgrid[0:rect.shape[0], 0:rect.shape[1]]
    d = (gy[..., None] - ys) ** 2 + (gx[..., None] - xs) ** 2
    nearest = d.argmin(-1)
    out = rect[ys[nearest], xs[nearest]].copy()
    out[alpha] = rect[alpha]
    return out


def transfer(model, old_els, old_tex):
    res = model.get("resolution", {"width": 128, "height": 128})
    tw, th = res["width"], res["height"]
    image = np.zeros((th * SCALE, tw * SCALE, 4), np.uint8)
    old = [(bbox(e), [OldFace(f, old_tex) for f in world_faces(e)]) for e in old_els]
    unresolved = 0
    for element in model["elements"]:
        lo, hi = bbox(element)
        near = [f for (blo, bhi), faces in old if np.all(blo <= hi + 0.6) and np.all(lo <= bhi + 0.6)
                for f in faces if f.ok]
        if not near:
            near = [f for _, faces in old for f in faces if f.ok]
        for face in world_faces(element):
            u1, v1, u2, v2 = face["uv"]
            if abs(u2 - u1) < 1e-6 or abs(v2 - v1) < 1e-6:
                continue
            x0, x1 = sorted((u1 * SCALE, u2 * SCALE))
            y0, y1 = sorted((v1 * SCALE, v2 * SCALE))
            x0, x1 = int(math.floor(x0 + 1e-6)), int(math.ceil(x1 - 1e-6))
            y0, y1 = int(math.floor(y0 + 1e-6)), int(math.ceil(y1 - 1e-6))
            gx, gy = np.meshgrid(np.arange(x0, x1) + 0.5, np.arange(y0, y1) + 0.5)
            s = np.clip((gx / SCALE - u1) / (u2 - u1), 0, 1)
            t = np.clip((gy / SCALE - v1) / (v2 - v1), 0, 1)
            p = face["c0"] + s[..., None] * (face["c3"] - face["c0"]) + t[..., None] * (face["c1"] - face["c0"])
            n = face["n"] / np.linalg.norm(face["n"])

            colour = np.zeros(p.shape[:2] + (4,), np.uint8)
            best = np.full(p.shape[:2], np.inf)
            for of in near:
                if of.n @ n < 0.9:
                    continue
                st = of.locate(p)
                inside = (st[..., 0] > -0.02) & (st[..., 0] < 1.02) & (st[..., 1] > -0.02) & (st[..., 1] < 1.02)
                close = np.abs(st[..., 2]) < 0.35
                tx, ty = of.texel(np.clip(st[..., 0], 0, 1), np.clip(st[..., 1], 0, 1))
                sample = of.rect[ty, tx]
                take = inside & close & (sample[..., 3] > 0) & (np.abs(st[..., 2]) < best)
                colour[take] = sample[take]
                best[take] = np.abs(st[..., 2])[take]
            left = ~np.isfinite(best)
            if left.any():
                far = np.full(p.shape[:2], np.inf)
                for of in near:
                    if of.n @ n < 0.9 or of.filled is None:
                        continue
                    st = of.locate(p)
                    sc, tc = np.clip(st[..., 0], 0, 1), np.clip(st[..., 1], 0, 1)
                    q = of.c0 + sc[..., None] * of.e1 + tc[..., None] * of.e2
                    dist = np.linalg.norm(p - q, axis=-1)
                    tx, ty = of.texel(sc, tc)
                    sample = of.filled[ty, tx]
                    take = left & (dist < far)
                    colour[take] = sample[take]
                    far[take] = dist[take]
                still = left & ~np.isfinite(far)
                colour[still] = FALLBACK
                unresolved += int(still.sum())
            colour[..., 3] = 255
            image[y0:y1, x0:x1] = colour
    return Image.fromarray(image, "RGBA"), unresolved


TEMPLATE = [(255, 248, 153), (248, 221, 114), (123, 255, 163), (67, 232, 141), (255, 167, 164),
            (244, 134, 134), (123, 212, 255), (91, 188, 244), (236, 248, 253), (180, 212, 225),
            (110, 120, 140), (83, 97, 116)]
SKIN = np.array((190, 193, 200), float)
EDGE = np.array((176, 179, 186), float)
SEAM = np.array((147, 148, 151), float)
RIVET = np.array((165, 168, 175), float)


def speckle(p, amount):
    """A steady speckle tied to the point on the drone, so it runs on unbroken from face to face."""
    q = np.floor(p * 2.0 + 1e-4).astype(np.int64)
    h = (q[..., 0] * 73856093) ^ (q[..., 1] * 19349663) ^ (q[..., 2] * 83492791)
    h = (h ^ (h >> 13)) * 1274126177
    return ((h & 0xFFFF) / 65535.0 - 0.5) * 2.0 * amount


def near(a, b, width=0.26):
    return np.abs(a - b) < width


def bare_skin(p, n, element):
    """The skin, where nothing was painted: brushed grey, with panel seams and rivet rows on the big panels."""
    x, y, z = p[..., 0], p[..., 1], p[..., 2]
    ax = np.abs(x)
    col = np.broadcast_to(SKIN if abs(n[1]) > 0.5 else EDGE, p.shape).copy() + speckle(p, 3.0)[..., None]
    size = np.array(element["to"], float) - np.array(element["from"], float)
    wing = size[1] >= 3.9 and size[0] >= 9 and abs(element["from"][1] - 1) < 0.01
    seams = np.zeros(x.shape, bool)
    rivets = np.zeros(x.shape, bool)
    if wing and n[1] < -0.7:
        seams |= (near(z, -13.2) & (ax > 7.4)) | (near(ax, 13.6) & (z < -13.2))
        for line in (-13.9, -12.5):
            rivets |= near(z, line, 0.22) & (np.abs(((ax - 8.0) % 1.5) - 0.75) < 0.22) & (ax > 8.0) & (ax < 19.5)
        rivets |= near(ax, 8.2, 0.22) & (np.abs(((z + 23.0) % 1.5) - 0.75) < 0.22)
    elif size[2] > 30 and abs(n[1]) < 0.7:
        for station in (-17.0, -6.0, 6.0):
            seams |= near(z, station)
            rivets |= near(z, station + 0.75, 0.22) & (np.abs(((y + 0.75) % 1.5) - 0.75) < 0.22)
    col[rivets] = RIVET
    col[seams] = SEAM
    return col


def refinish(image, model):
    """Repaints every bare texel — template colour or flat skin grey — and leaves all the detail alone."""
    tex = np.array(image).astype(float)
    res = model.get("resolution", {"width": 128, "height": 128})
    template = np.array(TEMPLATE, float)
    done = 0
    for element in model["elements"]:
        for face in world_faces(element):
            u1, v1, u2, v2 = face["uv"]
            if abs(u2 - u1) < 1e-6 or abs(v2 - v1) < 1e-6:
                continue
            x0, x1 = sorted((u1 * SCALE, u2 * SCALE))
            y0, y1 = sorted((v1 * SCALE, v2 * SCALE))
            x0, x1 = int(math.floor(x0 + 1e-6)), int(math.ceil(x1 - 1e-6))
            y0, y1 = int(math.floor(y0 + 1e-6)), int(math.ceil(y1 - 1e-6))
            region = tex[y0:y1, x0:x1, :3]
            bare = np.all(region == SKIN, axis=-1)
            bare |= (np.abs(region[..., None, :] - template).sum(-1) == 0).any(-1)
            if not bare.any():
                continue
            gx, gy = np.meshgrid(np.arange(x0, x1) + 0.5, np.arange(y0, y1) + 0.5)
            s = np.clip((gx / SCALE - u1) / (u2 - u1), 0, 1)
            t = np.clip((gy / SCALE - v1) / (v2 - v1), 0, 1)
            p = face["c0"] + s[..., None] * (face["c3"] - face["c0"]) + t[..., None] * (face["c1"] - face["c0"])
            n = face["n"] / np.linalg.norm(face["n"])
            col = bare_skin(p, n, element)
            region[bare] = np.clip(col[bare], 0, 255)
            done += int(bare.sum())
    print("bare texels refinished:", done)
    return Image.fromarray(tex.round().astype(np.uint8), "RGBA")


def r4(v):
    return [round(float(a), 4) + 0.0 for a in v]


def geo_cube(element):
    f = np.array(element["from"], float)
    t = np.array(element["to"], float)
    cube = {"origin": r4([-t[0], f[1], f[2]]), "size": r4(t - f)}
    rotation = element.get("rotation") or [0, 0, 0]
    if any(abs(a) > 1e-9 for a in rotation):
        o = element.get("origin", [0, 0, 0])
        cube["pivot"] = r4([-o[0], o[1], o[2]])
        cube["rotation"] = r4([-rotation[0], -rotation[1], rotation[2]])
    uv = {}
    for name in ("north", "east", "south", "west", "up", "down"):
        face = element["faces"].get(name)
        if not face or face.get("texture", 0) is None:
            continue
        u1, v1, u2, v2 = face["uv"]
        if name in ("up", "down"):
            uv[name] = {"uv": r4([u2, v2]), "uv_size": r4([u1 - u2, v1 - v2])}
        else:
            uv[name] = {"uv": r4([u1, v1]), "uv_size": r4([u2 - u1, v2 - v1])}
    cube["uv"] = uv
    return cube


def geometry(model, description):
    elements = {e["uuid"]: e for e in model["elements"]}
    groups = {g["uuid"]: g for g in model.get("groups", [])}
    bones, loose = [], []

    def walk(entry, parent):
        group = groups.get(entry["uuid"], entry)
        bone = {"name": group["name"], "pivot": r4([-group["origin"][0], group["origin"][1], group["origin"][2]])}
        if parent:
            bone["parent"] = parent
        rotation = group.get("rotation") or [0, 0, 0]
        if any(abs(a) > 1e-9 for a in rotation):
            bone["rotation"] = r4([-rotation[0], -rotation[1], rotation[2]])
        bones.append(bone)
        cubes = []
        for child in entry.get("children", []):
            if isinstance(child, str):
                if elements[child].get("export", True):
                    cubes.append(geo_cube(elements[child]))
            else:
                walk(child, group["name"])
        bone["cubes"] = cubes

    for entry in model["outliner"]:
        if isinstance(entry, str):
            loose.append(entry)
        else:
            walk(entry, None)
    if loose:
        main = next((b for b in bones if b["name"] in ("bb_main", "main")), None)
        if main is None:
            main = {"name": "bb_main", "pivot": [0, 0, 0], "cubes": []}
            bones.insert(0, main)
        main["cubes"] += [geo_cube(elements[u]) for u in loose if elements[u].get("export", True)]
    return {"format_version": "1.12.0", "minecraft:geometry": [{"description": description, "bones": bones}]}


def main(src, old_geo_path, old_png, assets, textured_copy=None):
    model = json.load(open(src, encoding="utf-8"))
    old_geo = json.load(open(old_geo_path, encoding="utf-8"))["minecraft:geometry"][0]
    old_tex = np.array(Image.open(old_png).convert("RGBA"))
    if old_tex.shape[1] != old_geo["description"]["texture_width"]:
        sys.exit("the old texture is expected at one texel per UV unit")
    image, unresolved = transfer(model, old_elements(old_geo), old_tex)
    print("texels with no old surface to take from:", unresolved)
    image = refinish(image, model)

    description = dict(old_geo["description"])
    res = model.get("resolution", {"width": 128, "height": 128})
    description["texture_width"], description["texture_height"] = res["width"], res["height"]
    geo = geometry(model, description)
    image.save(assets + "/textures/entity/shahed.png")
    with open(assets + "/geo/shahed.geo.json", "w", encoding="utf-8") as out:
        json.dump(geo, out, indent=1)

    if textured_copy:
        buffer = io.BytesIO()
        image.save(buffer, "PNG")
        h, w = image.size[1], image.size[0]
        model["textures"] = [{
            "name": "shahed.png", "folder": "", "namespace": "", "id": "0", "width": w, "height": h,
            "uv_width": res["width"], "uv_height": res["height"], "particle": False, "render_mode": "default",
            "render_sides": "auto", "frame_time": 1, "frame_order_type": "loop", "frame_order": "",
            "frame_interpolate": False, "visible": True, "internal": True, "saved": True,
            "uuid": "5a7f3c2e-1b2d-4e5f-8a9b-0c1d2e3f4a5b",
            "source": "data:image/png;base64," + base64.b64encode(buffer.getvalue()).decode(),
        }]
        for element in model["elements"]:
            for face in element["faces"].values():
                face["texture"] = 0
        with open(textured_copy, "w", encoding="utf-8") as out:
            json.dump(model, out)


def export(src, assets):
    """No transfer: the model already carries its texture."""
    model = json.load(open(src, encoding="utf-8"))
    source = model["textures"][0]["source"]
    image = Image.open(io.BytesIO(base64.b64decode(source.split(",", 1)[1]))).convert("RGBA")
    res = model.get("resolution", {"width": 128, "height": 128})
    description = {"identifier": "geometry.shahed", "texture_width": res["width"], "texture_height": res["height"],
                   "visible_bounds_width": 5, "visible_bounds_height": 2.5, "visible_bounds_offset": [0, 0.75, 0]}
    image.save(assets + "/textures/entity/shahed.png")
    with open(assets + "/geo/shahed.geo.json", "w", encoding="utf-8") as out:
        json.dump(geometry(model, description), out, indent=1)


if __name__ == "__main__":
    if sys.argv[1] == "export":
        export(*sys.argv[2:])
    else:
        main(*sys.argv[1:])
