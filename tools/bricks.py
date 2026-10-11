"""Builds the FPV quad's launch stand — the two bricks it sits on — from the Blockbench model of the quad standing on them.

    python tools/bricks.py <fpv_with_bricks.bbmodel> src/main/resources/assets/sfw/models/entity/fpv_stand.glb
"""
import base64
import copy
import io
import json
import sys

import numpy as np
from PIL import Image

import bb2glb
from fpv import corners, members, turn_around

PX = 4
RES = 32


def paint_brick(w, h, seed, face):
    """One face of a fired-clay brick, w x h pixels."""
    rng = np.random.default_rng(seed)
    base = np.array([152, 72, 50], float)
    col = np.broadcast_to(base, (h, w, 3)).copy()
    col += rng.normal(0, 7, (h, w, 1))
    blot = rng.random((max(h // 2, 1), max(w // 2, 1))) > 0.8
    col[np.kron(blot, np.ones((2, 2), bool))[:h, :w]] *= 0.86
    col[rng.random((h, w)) > 0.93] *= 0.72
    col[rng.random((h, w)) > 0.96] = np.array([186, 120, 88])
    edge = np.zeros((h, w), bool)
    edge[0, :] = edge[-1, :] = edge[:, 0] = edge[:, -1] = True
    col[edge] = col[edge] * 1.12 + 6
    nick = edge & (rng.random((h, w)) > 0.8)
    col[nick] = np.array([92, 44, 32])
    if face == "up":
        col = col * 1.06 + 4
    elif face == "down":
        col = col * 0.62
    else:
        rows = np.linspace(0, 1, h)[:, None, None]
        col = col * (1 - 0.3 * np.clip((rows - 0.65) / 0.35, 0, 1))
    return np.clip(col, 0, 255)


def main(src, dst):
    model = json.load(open(src, encoding="utf-8"))
    groups = {g["uuid"]: g for g in model.get("groups", [])}
    elements = {e["uuid"]: e for e in model["elements"]}
    entry = next(e for e in model["outliner"]
                 if isinstance(e, dict) and groups[e["uuid"]]["name"].lower() == "bricks")
    brick_ids = set(members(entry))

    quad = np.concatenate([corners(e) for u, e in elements.items() if u not in brick_ids])
    middle = (quad.min(0) + quad.max(0)) / 2

    stand = copy.deepcopy(model)
    stand["elements"] = [copy.deepcopy(elements[u]) for u in elements if u in brick_ids]
    stand["groups"] = [copy.deepcopy(groups[entry["uuid"]])]
    stand["outliner"] = [copy.deepcopy(entry)]
    turn_around(stand, float(middle[0]), float(middle[2]))
    feet = min(float(min(e["from"][1], e["to"][1])) for e in stand["elements"])
    shift = np.array([middle[0], feet, middle[2]])
    for e in stand["elements"]:
        e["from"] = (np.array(e["from"], float) - shift).tolist()
        e["to"] = (np.array(e["to"], float) - shift).tolist()
        e["origin"] = (np.array(e.get("origin", [0, 0, 0]), float) - shift).tolist()
    stand["groups"][0]["origin"] = (np.array(stand["groups"][0].get("origin", [0, 0, 0]), float) - shift).tolist()

    tex = np.zeros((RES * PX, RES * PX, 4), np.uint8)
    for b, e in enumerate(stand["elements"]):
        f, t = np.array(e["from"]), np.array(e["to"])
        sx, sy, sz = np.abs(t - f)
        row = b * 12.0
        layout = {
            "east": (0, row, sz, sy), "west": (0, row + 3, sz, sy),
            "up": (10, row, sx, sz), "down": (13, row, sx, sz),
            "north": (16, row, sx, sy), "south": (19, row, sx, sy),
        }
        for k, (face, (u, v, fw, fh)) in enumerate(layout.items()):
            pw, ph = max(int(round(fw * PX)), 1), max(int(round(fh * PX)), 1)
            x0, y0 = int(round(u * PX)), int(round(v * PX))
            tex[y0:y0 + ph, x0:x0 + pw, :3] = paint_brick(pw, ph, seed=100 * b + k, face=face)
            tex[y0:y0 + ph, x0:x0 + pw, 3] = 255
            e["faces"][face] = {"uv": [u, v, u + pw / PX, v + ph / PX], "texture": 0}
    png = io.BytesIO()
    Image.fromarray(tex, "RGBA").save(png, "PNG")
    stand["resolution"] = {"width": RES, "height": RES}
    stand["textures"] = [{"name": "bricks", "width": RES * PX, "height": RES * PX,
                          "uv_width": RES, "uv_height": RES,
                          "source": "data:image/png;base64," + base64.b64encode(png.getvalue()).decode()}]
    every = np.concatenate([corners(e) for e in stand["elements"]])
    print("stand bounds (units):", every.min(0).round(3), every.max(0).round(3))
    bb2glb.convert(stand, dst)


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
