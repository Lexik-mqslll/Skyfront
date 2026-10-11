"""Blockbench .bbmodel -> .glb, shaped for this mod's own minimal loader (GlbModel)."""
import base64
import json
import math
import struct
import sys

import numpy as np

EULER_ORDER = "zyx"

UNITS_PER_BLOCK = 16.0

FACE_DIRS = {
    "north": (0, 0, -1),
    "south": (0, 0, 1),
    "west": (-1, 0, 0),
    "east": (1, 0, 0),
    "up": (0, 1, 0),
    "down": (0, -1, 0),
}


def rot(axis, deg):
    a = math.radians(deg)
    c, s = math.cos(a), math.sin(a)
    if axis == "x":
        return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])
    if axis == "y":
        return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])


def euler_matrix(xyz):
    m = np.eye(3)
    for axis in EULER_ORDER:
        m = m @ rot(axis, xyz["xyz".index(axis)])
    return m


def face_quad(f, t, face):
    """The four corners of one box face, in the vanilla order that matches (u1,v1)..(u2,v1)."""
    x0, y0, z0 = f
    x1, y1, z1 = t
    return {
        "down": [(x0, y0, z1), (x0, y0, z0), (x1, y0, z0), (x1, y0, z1)],
        "up": [(x0, y1, z0), (x0, y1, z1), (x1, y1, z1), (x1, y1, z0)],
        "north": [(x1, y1, z0), (x1, y0, z0), (x0, y0, z0), (x0, y1, z0)],
        "south": [(x0, y1, z1), (x0, y0, z1), (x1, y0, z1), (x1, y1, z1)],
        "west": [(x0, y1, z0), (x0, y0, z0), (x0, y0, z1), (x0, y1, z1)],
        "east": [(x1, y1, z1), (x1, y0, z1), (x1, y0, z0), (x1, y1, z0)],
    }[face]


class Builder:
    """Accumulates one mesh's worth of triangles."""

    def __init__(self):
        self.pos = []
        self.nrm = []
        self.uv = []
        self.idx = []

    def add_cube(self, element, tex_w, tex_h, offset):
        """A box element: six faces off its from/to corners, turned by its rotation about its origin."""
        f = np.array(element["from"], float)
        t = np.array(element["to"], float)
        inflate = float(element.get("inflate", 0) or 0)
        if inflate:
            f = f - inflate
            t = t + inflate
        origin = np.array(element.get("origin", [0, 0, 0]), float)
        matrix = euler_matrix(element.get("rotation", [0, 0, 0]) or [0, 0, 0])

        for name, face in (element.get("faces") or {}).items():
            if name not in FACE_DIRS or face is None or "uv" not in face:
                continue
            if face.get("texture") is None:
                continue
            u1, v1, u2, v2 = face["uv"]
            corners = face_quad(f, t, name)
            uvs = [(u1, v1), (u1, v2), (u2, v2), (u2, v1)]
            steps = int(face.get("rotation", 0) or 0) // 90 % 4
            if steps:
                uvs = uvs[steps:] + uvs[:steps]

            normal = matrix @ np.array(FACE_DIRS[name], float)
            n = np.linalg.norm(normal)
            normal = normal / n if n > 1e-9 else np.array([0.0, 1.0, 0.0])

            base = len(self.pos) // 3
            for (px, py, pz), (u, v) in zip(corners, uvs):
                p = matrix @ (np.array([px, py, pz]) - origin) + origin
                p = (p - offset) / UNITS_PER_BLOCK
                self.pos.extend([float(p[0]), float(p[1]), float(p[2])])
                self.nrm.extend([float(normal[0]), float(normal[1]), float(normal[2])])
                self.uv.extend([u / tex_w, v / tex_h])
            self.idx.extend([base, base + 1, base + 2, base, base + 2, base + 3])

    def add_mesh(self, element, tex_w, tex_h, offset):
        """A free-form mesh element: vertices about its origin, turned by its rotation, faces of three or four corners each with their own UVs."""
        origin = np.array(element.get("origin", [0, 0, 0]), float)
        matrix = euler_matrix(element.get("rotation", [0, 0, 0]) or [0, 0, 0])
        verts = {key: matrix @ np.array(v, float) + origin for key, v in element["vertices"].items()}
        centre = np.mean(list(verts.values()), axis=0)

        for face in (element.get("faces") or {}).values():
            if face is None or face.get("texture") is None:
                continue
            keys = [k for k in face.get("vertices", []) if k in verts]
            if len(keys) < 3:
                continue
            points = [verts[k] for k in keys]
            normal = np.cross(points[1] - points[0], points[2] - points[0])
            if np.linalg.norm(normal) < 1e-9 and len(points) == 4:
                normal = np.cross(points[2] - points[0], points[3] - points[0])
            n = np.linalg.norm(normal)
            if n < 1e-9:
                continue
            normal = normal / n
            face_centre = np.mean(points, axis=0)
            if np.dot(normal, face_centre - centre) < 0:
                normal = -normal
            if len(keys) == 4:
                a = points[0] - face_centre
                a = a / (np.linalg.norm(a) or 1.0)
                b = np.cross(normal, a)
                keys = sorted(keys, key=lambda k: math.atan2(float(np.dot(verts[k] - face_centre, b)),
                                                             float(np.dot(verts[k] - face_centre, a))))
            base = len(self.pos) // 3
            for k in keys:
                pt = (verts[k] - offset) / UNITS_PER_BLOCK
                self.pos.extend([float(pt[0]), float(pt[1]), float(pt[2])])
                self.nrm.extend([float(normal[0]), float(normal[1]), float(normal[2])])
                u, v = face["uv"].get(k, [0, 0])
                self.uv.extend([u / tex_w, v / tex_h])
            for i in range(1, len(keys) - 1):
                self.idx.extend([base, base + i, base + i + 1])

    def empty(self):
        return not self.idx


def spin_rotation(builder):
    """The node rotation that lays a propeller's spin axis along the node's own +Y."""
    pos = np.array(builder.pos, float).reshape(-1, 3)
    extent = pos.max(axis=0) - pos.min(axis=0)
    axis = int(np.argmin(extent))
    if axis == 1:
        return np.eye(3), [0.0, 0.0, 0.0, 1.0]
    half = math.sqrt(0.5)
    if axis == 2:
        return rot("x", 90), [half, 0.0, 0.0, half]
    return rot("z", -90), [0.0, 0.0, -half, half]


def reframe(builder, matrix):
    """Re-express a builder's vertices in a frame turned by matrix (i.e."""
    inverse = matrix.T
    pos = np.array(builder.pos, float).reshape(-1, 3) @ inverse.T
    nrm = np.array(builder.nrm, float).reshape(-1, 3) @ inverse.T
    builder.pos = [float(v) for v in pos.reshape(-1)]
    builder.nrm = [float(v) for v in nrm.reshape(-1)]


def main(src, dst):
    convert(json.load(open(src, encoding="utf-8")), dst)


def convert(model, dst):
    elements = {e["uuid"]: e for e in model["elements"]}
    groups = {g["uuid"]: g for g in model.get("groups", [])}

    if len(model.get("textures", [])) != 1:
        sys.exit("the model must carry exactly one texture, it has %d" % len(model.get("textures", [])))
    res = model.get("resolution", {})
    texture = model["textures"][0]
    tex_w = float(texture.get("uv_width") or res.get("width", 16))
    tex_h = float(texture.get("uv_height") or res.get("height", 16))

    static = Builder()
    props = {}

    def walk(entry, prop_group):
        if isinstance(entry, str):
            element = elements.get(entry)
            if element is None or not element.get("export", True):
                return
            kind = element.get("type", "cube")
            if kind not in ("cube", "mesh"):
                return
            if prop_group is not None:
                builder = props[prop_group]["builder"]
                offset = np.array(groups[prop_group]["origin"], float)
            else:
                builder = static
                offset = np.zeros(3)
            if kind == "cube":
                builder.add_cube(element, tex_w, tex_h, offset)
            else:
                builder.add_mesh(element, tex_w, tex_h, offset)
            return

        uuid = entry["uuid"]
        group = groups.get(uuid)
        name = group["name"] if group else ""
        here = prop_group
        if group is not None and name.lower().startswith("prop") and name[4:].isdigit():
            here = uuid
            props[uuid] = {"name": name, "builder": Builder(),
                           "hub": np.array(group["origin"], float)}
        for child in entry.get("children", []):
            walk(child, here)

    for entry in model["outliner"]:
        walk(entry, None)

    print("static triangles:", len(static.idx) // 3)
    for info in props.values():
        print("  %s: %d triangles, hub %s" % (
            info["name"], len(info["builder"].idx) // 3, info["hub"]))

    bin_parts = []
    offset = 0
    buffer_views = []
    accessors = []

    def add_view(raw, target=None):
        nonlocal offset
        pad = (-len(raw)) % 4
        view = {"buffer": 0, "byteOffset": offset, "byteLength": len(raw)}
        if target:
            view["target"] = target
        buffer_views.append(view)
        bin_parts.append(raw + b"\0" * pad)
        offset += len(raw) + pad
        return len(buffer_views) - 1

    def add_float_accessor(values, components, target=34962):
        raw = struct.pack("<%df" % len(values), *values)
        view = add_view(raw, target)
        count = len(values) // components
        data = np.array(values, dtype=np.float32).reshape(count, components)
        accessors.append({
            "bufferView": view,
            "componentType": 5126,
            "count": count,
            "type": {1: "SCALAR", 2: "VEC2", 3: "VEC3", 4: "VEC4"}[components],
            "min": [float(x) for x in data.min(axis=0)],
            "max": [float(x) for x in data.max(axis=0)],
        })
        return len(accessors) - 1

    def add_index_accessor(values):
        raw = struct.pack("<%dI" % len(values), *values)
        view = add_view(raw, 34963)
        accessors.append({
            "bufferView": view,
            "componentType": 5125,
            "count": len(values),
            "type": "SCALAR",
        })
        return len(accessors) - 1

    meshes = []

    def add_mesh(builder, name):
        primitive = {
            "attributes": {
                "POSITION": add_float_accessor(builder.pos, 3),
                "NORMAL": add_float_accessor(builder.nrm, 3),
                "TEXCOORD_0": add_float_accessor(builder.uv, 2),
            },
            "indices": add_index_accessor(builder.idx),
            "material": 0,
            "mode": 4,
        }
        meshes.append({"name": name, "primitives": [primitive]})
        return len(meshes) - 1

    nodes = []
    roots = []

    if not static.empty():
        nodes.append({"name": "airframe", "mesh": add_mesh(static, "airframe")})
        roots.append(len(nodes) - 1)

    for info in sorted(props.values(), key=lambda i: i["name"]):
        hub = info["hub"] / UNITS_PER_BLOCK
        matrix, quaternion = spin_rotation(info["builder"])
        reframe(info["builder"], matrix)
        node = {
            "name": info["name"],
            "mesh": add_mesh(info["builder"], info["name"]),
            "translation": [float(hub[0]), float(hub[1]), float(hub[2])],
        }
        if quaternion != [0.0, 0.0, 0.0, 1.0]:
            node["rotation"] = quaternion
        print("  %s spins about %s" % (info["name"],
                                       "Y" if "rotation" not in node else ("Z" if quaternion[0] else "X")))
        nodes.append(node)
        roots.append(len(nodes) - 1)

    texture = model["textures"][0]
    png = base64.b64decode(texture["source"].split(",", 1)[1])
    image_view = add_view(png)

    binary = b"".join(bin_parts)
    gltf = {
        "asset": {"version": "2.0", "generator": "skyfront bb2glb"},
        "scene": 0,
        "scenes": [{"nodes": roots}],
        "nodes": nodes,
        "meshes": meshes,
        "accessors": accessors,
        "bufferViews": buffer_views,
        "buffers": [{"byteLength": len(binary)}],
        "materials": [{
            "name": "texture",
            "pbrMetallicRoughness": {
                "baseColorTexture": {"index": 0},
                "metallicFactor": 0.0,
                "roughnessFactor": 1.0,
            },
            "alphaMode": "MASK",
            "alphaCutoff": 0.5,
            "doubleSided": True,
        }],
        "textures": [{"sampler": 0, "source": 0}],
        "images": [{"bufferView": image_view, "mimeType": "image/png", "name": "texture"}],
        "samplers": [{"magFilter": 9728, "minFilter": 9728, "wrapS": 33071, "wrapT": 33071}],
    }

    json_chunk = json.dumps(gltf, separators=(",", ":")).encode("utf-8")
    json_chunk += b" " * ((-len(json_chunk)) % 4)
    binary += b"\0" * ((-len(binary)) % 4)

    total = 12 + 8 + len(json_chunk) + 8 + len(binary)
    with open(dst, "wb") as out:
        out.write(struct.pack("<III", 0x46546C67, 2, total))
        out.write(struct.pack("<II", len(json_chunk), 0x4E4F534A))
        out.write(json_chunk)
        out.write(struct.pack("<II", len(binary), 0x004E4942))
        out.write(binary)
    print("wrote %s (%d bytes, %d nodes)" % (dst, total, len(nodes)))


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
