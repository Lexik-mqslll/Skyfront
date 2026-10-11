"""Builds the Molniya's glb from its Blockbench model.

    python tools/molniya.py <model.bbmodel> src/main/resources/assets/sfw/models/entity/molniya.glb
"""
import json
import sys

import bb2glb

BLADES = [
    "f0b73efb-e05d-24be-54b4-e035434f5715",
    "a6a0fa02-9fd7-cda3-8d08-d7b2776d2b3d",
]
HUB = [0.0, 0.5, -25.55]
PROP_GROUP = "c0ffee00-0000-4000-8000-00000000a7a1"


def main(src, dst):
    model = json.load(open(src, encoding="utf-8"))
    already = any(g.get("name", "").lower().startswith("prop") for g in model.get("groups", []))
    if not already:
        missing = [uuid for uuid in BLADES if uuid not in {e["uuid"] for e in model["elements"]}]
        if missing:
            sys.exit("the propeller blades were not found in this model: %s" % missing)
        model.setdefault("groups", []).append({
            "name": "prop1",
            "uuid": PROP_GROUP,
            "origin": HUB,
            "rotation": [0, 0, 0],
        })
        model["outliner"] = [entry for entry in model["outliner"] if entry not in BLADES]
        model["outliner"].append({"uuid": PROP_GROUP, "children": list(BLADES)})
    bb2glb.convert(model, dst)


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
