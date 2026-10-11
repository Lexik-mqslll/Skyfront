"""Builds the FPV quad's glb from its Blockbench model.

    python tools/fpv.py <fpv.bbmodel> src/main/resources/assets/sfw/models/entity/fpv.glb
"""
import json
import sys

import numpy as np

import bb2glb


def corners(element):
    f = np.array(element["from"], float)
    t = np.array(element["to"], float)
    origin = np.array(element.get("origin", [0, 0, 0]), float)
    m = bb2glb.euler_matrix(element.get("rotation", [0, 0, 0]) or [0, 0, 0])
    return np.array([m @ (np.array([x, y, z]) - origin) + origin
                     for x in (f[0], t[0]) for y in (f[1], t[1]) for z in (f[2], t[2])])


def members(entry):
    for child in entry.get("children", []):
        if isinstance(child, dict):
            yield from members(child)
        else:
            yield child


SWAP = {"north": "south", "south": "north", "east": "west", "west": "east", "up": "up", "down": "down"}


def turn_around(model, cx, cz):
    """The whole model turned half a turn about the upright through (cx, cz)."""
    for e in model["elements"]:
        f, t = e["from"], e["to"]
        e["from"] = [2 * cx - t[0], f[1], 2 * cz - t[2]]
        e["to"] = [2 * cx - f[0], t[1], 2 * cz - f[2]]
        o = e.get("origin", [0, 0, 0])
        e["origin"] = [2 * cx - o[0], o[1], 2 * cz - o[2]]
        r = e.get("rotation") or [0, 0, 0]
        e["rotation"] = [-r[0], r[1], -r[2]]
        faces = {}
        for name, face in e["faces"].items():
            face = dict(face)
            if name in ("up", "down"):
                u1, v1, u2, v2 = face["uv"]
                face["uv"] = [u2, v2, u1, v1]
            faces[SWAP[name]] = face
        e["faces"] = faces
    for g in model.get("groups", []):
        o = g.get("origin", [0, 0, 0])
        g["origin"] = [2 * cx - o[0], o[1], 2 * cz - o[2]]


def centre_on_origin(model, middle):
    """Moves the whole model so its middle is the origin — the point the airframe tilts about."""
    shift = np.array(middle, float)
    for e in model["elements"]:
        e["from"] = (np.array(e["from"], float) - shift).tolist()
        e["to"] = (np.array(e["to"], float) - shift).tolist()
        e["origin"] = (np.array(e.get("origin", [0, 0, 0]), float) - shift).tolist()
    for g in model.get("groups", []):
        g["origin"] = (np.array(g.get("origin", [0, 0, 0]), float) - shift).tolist()


def main(src, dst):
    model = json.load(open(src, encoding="utf-8"))
    every = np.concatenate([corners(e) for e in model["elements"]])
    middle = (every.min(0) + every.max(0)) / 2
    turn_around(model, float(middle[0]), float(middle[2]))
    centre_on_origin(model, middle)
    elements = {e["uuid"]: e for e in model["elements"]}
    groups = {g["uuid"]: g for g in model.get("groups", [])}

    def centre(entry):
        pts = np.concatenate([corners(elements[u]) for u in members(entry) if u in elements])
        return (pts.min(0) + pts.max(0)) / 2

    motors = [centre(e) for e in model["outliner"]
              if isinstance(e, dict) and groups[e["uuid"]]["name"].lower().startswith("motor")]
    if not motors:
        sys.exit("no motor groups to find the shafts by")
    for entry in model["outliner"]:
        if not isinstance(entry, dict):
            continue
        group = groups[entry["uuid"]]
        name = group["name"]
        if name.lower().startswith("prop") and name[4:].isdigit():
            here = centre(entry)
            shaft = min(motors, key=lambda m: (m[0] - here[0]) ** 2 + (m[2] - here[2]) ** 2)
            hub = [round(float(shaft[0]), 4), group["origin"][1], round(float(shaft[2]), 4)]
            print("  %s: hub %s (was %s)" % (name, hub, group["origin"]))
            group["origin"] = hub
    bb2glb.convert(model, dst)


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
