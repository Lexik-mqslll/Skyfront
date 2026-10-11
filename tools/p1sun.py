"""Builds the P1-SUN's glb from its Blockbench model.

    python tools/p1sun.py <p1sun.bbmodel> src/main/resources/assets/sfw/models/entity/p1sun.glb
"""
import json
import sys

import bb2glb


def blade_uuids(entry):
    for child in entry.get("children", []):
        if isinstance(child, dict):
            yield from blade_uuids(child)
        else:
            yield child


def main(src, dst):
    model = json.load(open(src, encoding="utf-8"))
    groups = {g["uuid"]: g for g in model.get("groups", [])}
    elements = {e["uuid"]: e for e in model["elements"]}

    def visit(entries):
        for entry in entries:
            if not isinstance(entry, dict):
                continue
            group = groups.get(entry["uuid"])
            name = group["name"] if group else ""
            if name.lower().startswith("prop") and name[4:].isdigit():
                pivots = {tuple(round(v, 4) for v in elements[u].get("origin", (0, 0, 0)))
                          for u in blade_uuids(entry) if u in elements}
                if len(pivots) != 1:
                    sys.exit("%s: its blades do not share one pivot (%s), so the hub is ambiguous"
                             % (name, sorted(pivots)))
                hub = list(next(iter(pivots)))
                if hub != group["origin"]:
                    print("  %s: hub %s (was the group origin %s)" % (name, hub, group["origin"]))
                group["origin"] = hub
            visit(entry.get("children", []))

    visit(model["outliner"])
    bb2glb.convert(model, dst)


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
