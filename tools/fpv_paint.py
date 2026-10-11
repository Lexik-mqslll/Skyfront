"""Paints the FPV quad from its Blockbench model's colour key.

    python tools/fpv_paint.py <fpv.bbmodel> <painted .bbmodel> [<texture.png>]
"""
import base64
import io
import json
import math
import sys

import numpy as np
from PIL import Image

from bb2glb import FACE_DIRS, euler_matrix, face_quad

KEY_FRAME = (210, 17, 41)
KEY_FRAME_DARK = (154, 8, 15)
KEY_STANDOFF = (26, 26, 27)
KEY_SMA = (248, 149, 32)
KEY_MOTOR = (255, 0, 148)
KEY_SHAFT = (12, 87, 39)


def rgb(*c):
    return np.array(c, float)


def speckle(p, amount, seed=0):
    q = np.floor(p * 4.0 + 1e-4).astype(np.int64)
    h = (q[..., 0] * 73856093) ^ (q[..., 1] * 19349663) ^ (q[..., 2] * 83492791) ^ (seed * 2654435761)
    h = (h ^ (h >> 13)) * 1274126177
    return ((h & 0xFFFF) / 65535.0 - 0.5) * 2.0 * amount


def lit(col, n):
    """Light from above: tops a step lighter, undersides a step darker, sides as they are."""
    return col * (1.0 + 0.10 * n[1]) if n[1] >= 0 else col * (1.0 + 0.16 * n[1])


def edge_mask(edge, width=0.26):
    return edge < width


def paint_carbon(c, twill_axis=0):
    """Carbon fibre plate: a 2x2 twill in two near-blacks, its cut edges showing a lighter grey."""
    ix, iy = c["ix"], c["iy"]
    a = (ix + iy) // 2 % 2 if twill_axis == 0 else (ix - iy) // 2 % 2
    col = np.where(a[..., None] == 0, rgb(30, 30, 33), rgb(42, 42, 46))
    if abs(c["n"][1]) < 0.5:
        col = np.broadcast_to(rgb(64, 64, 68), col.shape).copy()
    else:
        col[edge_mask(c["edge"], 0.18)] = rgb(58, 58, 63)
    return col + speckle(c["p"], 2.0)[..., None]


def paint_standoff(c):
    col = np.broadcast_to(rgb(26, 26, 29), c["p"].shape).copy()
    col[c["iu"] == 0] = rgb(52, 52, 57)
    return col + speckle(c["p"], 2.0)[..., None]


def paint_motor_bell(c):
    """Black anodised bell: vent slots round the side, a ring of vents in the top, a lit rim."""
    n = c["n"]
    col = np.broadcast_to(rgb(30, 30, 33), c["p"].shape).copy()
    if n[1] > 0.5:
        s, t = c["s"], c["t"]
        r = np.hypot(s - 0.5, t - 0.5)
        col[(r > 0.18) & (r < 0.32)] = rgb(14, 14, 16)
        col[r < 0.12] = rgb(170, 172, 178)
        col[edge_mask(c["edge"], 0.12)] = rgb(62, 62, 68)
    elif abs(n[1]) < 0.5:
        col[c["iv"] == 0] = rgb(64, 64, 70)
        slots = (c["iv"] >= 1) & (c["iu"] % 2 == 1)
        col[slots] = rgb(14, 14, 16)
    else:
        col[:] = rgb(20, 20, 22)
    return col


def paint_motor_part(c):
    col = np.broadcast_to(rgb(64, 64, 70), c["p"].shape).copy()
    col[edge_mask(c["edge"], 0.05)] = rgb(44, 44, 48)
    return col + speckle(c["p"], 3.0)[..., None]


def paint_shaft(c):
    col = np.broadcast_to(rgb(168, 170, 176), c["p"].shape).copy()
    col[c["iu"] == 0] = rgb(210, 212, 218)
    return col


def paint_prop(c):
    """Light grey polycarbonate blade: lighter along one edge, darker along the other."""
    col = np.broadcast_to(rgb(184, 186, 190), c["p"].shape).copy()
    if abs(c["n"][1]) > 0.5:
        col[c["t"] < 0.3] = rgb(212, 214, 218)
        col[c["t"] > 0.7] = rgb(156, 158, 163)
    else:
        col[:] = rgb(146, 148, 153)
    return col + speckle(c["p"], 3.0)[..., None]


def paint_prop_hub(c):
    col = np.broadcast_to(rgb(168, 170, 175), c["p"].shape).copy()
    if c["n"][1] > 0.5:
        r = np.hypot(c["s"] - 0.5, c["t"] - 0.5)
        col[r < 0.32] = rgb(34, 34, 38)
        col[r < 0.12] = rgb(150, 152, 158)
    return col


def paint_camera(c, front):
    """Black camera body; on its front, the lens: a grey ring, dark glass with a cold glint."""
    col = np.broadcast_to(rgb(28, 28, 31), c["p"].shape).copy()
    col[edge_mask(c["edge"], 0.06)] = rgb(48, 48, 53)
    if front:
        r = np.hypot(c["s"] - 0.5, c["t"] - 0.5)
        col[r < 0.42] = rgb(84, 84, 90)
        col[r < 0.30] = rgb(10, 12, 22)
        glint = (c["s"] - 0.5 < -0.04) & (c["t"] - 0.5 < -0.04) & (r < 0.24)
        col[glint] = rgb(70, 96, 150)
    return col


def paint_pcb(c, shield=False, seed=0):
    """A black PCB: gold pads at its corners, a scatter of tiny parts, an LED; a can on the VTX."""
    col = np.broadcast_to(rgb(24, 27, 25), c["p"].shape).copy()
    if c["n"][1] > 0.5:
        s, t = c["s"], c["t"]
        dots = speckle(c["p"], 1.0, seed)
        col[dots > 0.55] = rgb(110, 112, 118)
        col[dots < -0.7] = rgb(196, 160, 70)
        corner = ((s < 0.18) | (s > 0.82)) & ((t < 0.18) | (t > 0.82))
        col[corner] = rgb(206, 168, 72)
        if shield:
            can = (s > 0.2) & (s < 0.8) & (t > 0.25) & (t < 0.75)
            col[can] = rgb(160, 162, 168)
            col[can & ((c["iu"] + c["iv"]) % 3 == 0)] = rgb(186, 188, 194)
        led = (np.abs(s - 0.86) < 0.08) & (np.abs(t - 0.5) < 0.1)
        col[led] = rgb(230, 40, 40) if shield else rgb(60, 120, 255)
    elif abs(c["n"][1]) < 0.5:
        col[:] = rgb(40, 58, 44)
    return col


def paint_tpu(c):
    """3D-printed TPU: swamp olive green, its layer lines running across it."""
    col = np.broadcast_to(rgb(92, 98, 58), c["p"].shape).copy()
    layers = np.floor(c["p"][..., 1] * 8.0 + 1e-4).astype(np.int64) % 2 == 0
    col[layers] = rgb(78, 84, 48)
    return lit(col, c["n"])


def paint_sma(c):
    col = np.broadcast_to(rgb(204, 164, 64), c["p"].shape).copy()
    col[c["iu"] % 2 == 0] = rgb(232, 196, 96)
    return col


def paint_antenna_lead(c):
    col = np.broadcast_to(rgb(30, 30, 33), c["p"].shape).copy()
    col[c["iu"] == 0] = rgb(58, 58, 64)
    return col


def paint_antenna_cap(c):
    col = np.broadcast_to(rgb(34, 34, 38), c["p"].shape).copy()
    col[edge_mask(c["edge"], 0.08)] = rgb(60, 60, 66)
    if c["n"][1] > 0.5:
        col[np.hypot(c["s"] - 0.5, c["t"] - 0.5) < 0.2] = rgb(200, 40, 40)
    return col


def paint_rx_lead(c):
    col = np.broadcast_to(rgb(36, 36, 40), c["p"].shape).copy()
    col[c["iv"] == 0] = rgb(64, 64, 70)
    return col


def paint_rx_tip(c):
    return np.broadcast_to(rgb(222, 222, 216), c["p"].shape).copy() + speckle(c["p"], 3.0)[..., None]


def paint_strap(c):
    """Battery strap: black webbing, a woven diagonal and a grey rubber grip."""
    col = np.broadcast_to(rgb(26, 26, 29), c["p"].shape).copy()
    weave = (c["ix"] + c["iy"] + c["iz"]) % 3 == 0
    col[weave] = rgb(42, 42, 47)
    col[edge_mask(c["edge"], 0.1)] = rgb(150, 34, 34)
    return col


def paint_battery(c):
    """LiPo pack in plain blue shrink: lighter on top, its edges a darker blue."""
    col = np.broadcast_to(rgb(38, 86, 188), c["p"].shape).copy() + speckle(c["p"], 3.0)[..., None]
    col = lit(col, c["n"])
    col[edge_mask(c["edge"], 0.18)] = rgb(24, 58, 140)
    return col


def paint_wire(c, red):
    base = rgb(186, 30, 30) if red else rgb(30, 30, 33)
    col = np.broadcast_to(base, c["p"].shape).copy()
    col[c["iu"] == 0] = base * 1.4 if red else rgb(64, 64, 70)
    return np.clip(col, 0, 255)


def paint_xt60(c):
    """XT60 as it really is: yellow nylon, a muted amber rather than a bright yellow."""
    col = np.broadcast_to(rgb(204, 168, 58), c["p"].shape).copy()
    col[edge_mask(c["edge"], 0.05)] = rgb(156, 122, 34)
    return lit(col, c["n"])


def part_of(group, key):
    g = group.lower()
    if g.startswith("arm"):
        return "arm"
    if g == "frame":
        return "standoff" if key == KEY_STANDOFF else "carbon"
    if g == "cam":
        return "camera"
    if g in ("esc", "stack"):
        return "pcb"
    if g == "vtx":
        return "vtx"
    if g == "ant_mount":
        return "sma" if key == KEY_SMA else "tpu"
    if g == "ant_vtx":
        return "vtx_antenna"
    if g == "ant_rx":
        return "rx_antenna"
    if g.startswith("motor"):
        return {KEY_SHAFT: "shaft", KEY_MOTOR: "motor"}.get(key, "motor_part")
    if g.startswith("prop"):
        return "prop"
    if g == "strops":
        return "strap"
    if g == "battery":
        return "battery"
    if g == "wires":
        return "wire"
    if g == "xt60":
        return "xt60"
    return "carbon"


def key_colour(element, key, sx, sy):
    from collections import Counter
    counts = Counter()
    for face in element["faces"].values():
        u1, v1, u2, v2 = face["uv"]
        x0, x1 = sorted((u1 * sx, u2 * sx))
        y0, y1 = sorted((v1 * sy, v2 * sy))
        region = key[int(y0):int(math.ceil(y1)), int(x0):int(math.ceil(x1))].reshape(-1, 4)
        for p in map(tuple, region):
            if p[3] > 0:
                counts[p[:3]] += 1
    for colour, _ in counts.most_common():
        return tuple(int(v) for v in colour)
    return None


def groups_of(model):
    names = {g["uuid"]: g["name"] for g in model.get("groups", [])}
    owner = {}

    def walk(entries, name):
        for entry in entries:
            if isinstance(entry, dict):
                walk(entry.get("children", []), entry.get("name") or names.get(entry["uuid"], name))
            else:
                owner[entry] = name

    walk(model["outliner"], "")
    return owner


def paint(model):
    texture = model["textures"][0]
    key = np.array(Image.open(io.BytesIO(base64.b64decode(texture["source"].split(",", 1)[1]))).convert("RGBA"))
    h, w = key.shape[:2]
    sx, sy = w / float(texture.get("uv_width") or w), h / float(texture.get("uv_height") or h)
    out = np.zeros((h, w, 4), float)
    owner = groups_of(model)

    wires = [e for e in model["elements"] if owner.get(e["uuid"], "").lower() == "wires"]
    wires.sort(key=lambda e: (e["from"][0] + e["to"][0]) / 2)
    red_wire = {wires[-1]["uuid"]} if wires else set()

    for element in model["elements"]:
        group = owner.get(element["uuid"], "")
        part = part_of(group, key_colour(element, key, sx, sy))
        size = np.array(element["to"], float) - np.array(element["from"], float)
        if part == "prop" and size[0] < 1.5:
            part = "prop_hub"
        if part == "rx_antenna" and size[0] < 1.0:
            part = "rx_tip"
        if part == "vtx_antenna" and size[1] < 1.0:
            part = "vtx_cap"
        f = np.array(element["from"], float)
        t = np.array(element["to"], float)
        origin = np.array(element.get("origin", [0, 0, 0]), float)
        m = euler_matrix(element.get("rotation", [0, 0, 0]) or [0, 0, 0])
        for name, face in element["faces"].items():
            if face.get("texture") is None:
                continue
            u1, v1, u2, v2 = face["uv"]
            if abs(u2 - u1) < 1e-6 or abs(v2 - v1) < 1e-6:
                continue
            c0, c1, _, c3 = (np.array(c, float) for c in face_quad(f, t, name))
            x0, x1 = sorted((u1 * sx, u2 * sx))
            y0, y1 = sorted((v1 * sy, v2 * sy))
            x0, x1 = int(math.floor(x0 + 1e-6)), max(int(math.floor(x0 + 1e-6)) + 1, int(math.ceil(x1 - 1e-6)))
            y0, y1 = int(math.floor(y0 + 1e-6)), max(int(math.floor(y0 + 1e-6)) + 1, int(math.ceil(y1 - 1e-6)))
            gx, gy = np.meshgrid(np.arange(x0, x1) + 0.5, np.arange(y0, y1) + 0.5)
            s = np.clip((gx / sx - min(u1, u2)) / abs(u2 - u1), 0, 1)
            r = np.clip((gy / sy - min(v1, v2)) / abs(v2 - v1), 0, 1)
            s_face = s if u2 > u1 else 1 - s
            r_face = r if v2 > v1 else 1 - r
            local = c0 + s_face[..., None] * (c3 - c0) + r_face[..., None] * (c1 - c0)
            world = (local - origin) @ m.T + origin
            normal = m @ np.array(FACE_DIRS[name], float)
            wu, wv = np.linalg.norm(c3 - c0), np.linalg.norm(c1 - c0)
            edge = np.minimum(np.minimum(s_face * wu, (1 - s_face) * wu), np.minimum(r_face * wv, (1 - r_face) * wv))
            iu = (gx - x0 - 0.5).astype(int)
            iv = (gy - y0 - 0.5).astype(int)
            grid = np.floor(world * 4.0 + 1e-4).astype(int)
            c = {"p": world, "n": normal, "s": s_face, "t": r_face, "edge": edge, "iu": iu, "iv": iv,
                 "ix": grid[..., 0], "iy": grid[..., 2] if abs(normal[1]) > 0.5 else grid[..., 1],
                 "iz": grid[..., 2]}
            if part == "carbon":
                col = paint_carbon(c)
            elif part == "arm":
                col = paint_carbon(c, twill_axis=1)
            elif part == "standoff":
                col = paint_standoff(c)
            elif part == "motor":
                col = paint_motor_bell(c)
            elif part == "motor_part":
                col = paint_motor_part(c)
            elif part == "shaft":
                col = paint_shaft(c)
            elif part == "prop":
                col = paint_prop(c)
            elif part == "prop_hub":
                col = paint_prop_hub(c)
            elif part == "camera":
                col = paint_camera(c, front=name == "south")
            elif part == "pcb":
                col = paint_pcb(c, seed=hash(element["uuid"]) & 0xFF)
            elif part == "vtx":
                col = paint_pcb(c, shield=True, seed=7)
            elif part == "tpu":
                col = paint_tpu(c)
            elif part == "sma":
                col = paint_sma(c)
            elif part == "vtx_antenna":
                col = paint_antenna_lead(c)
            elif part == "vtx_cap":
                col = paint_antenna_cap(c)
            elif part == "rx_antenna":
                col = paint_rx_lead(c)
            elif part == "rx_tip":
                col = paint_rx_tip(c)
            elif part == "strap":
                col = paint_strap(c)
            elif part == "battery":
                col = paint_battery(c)
            elif part == "wire":
                col = paint_wire(c, element["uuid"] in red_wire)
            elif part == "xt60":
                col = paint_xt60(c)
            else:
                col = paint_carbon(c)
            out[y0:y1, x0:x1, :3] = np.clip(col, 0, 255)
            out[y0:y1, x0:x1, 3] = 255
    return Image.fromarray(out.round().astype(np.uint8), "RGBA")


def main(src, dst, png=None):
    model = json.load(open(src, encoding="utf-8"))
    image = paint(model)
    buffer = io.BytesIO()
    image.save(buffer, "PNG")
    model["textures"][0]["source"] = "data:image/png;base64," + base64.b64encode(buffer.getvalue()).decode()
    with open(dst, "w", encoding="utf-8") as out:
        json.dump(model, out)
    if png:
        image.save(png)


if __name__ == "__main__":
    main(*sys.argv[1:])
