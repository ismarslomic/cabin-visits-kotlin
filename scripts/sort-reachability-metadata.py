#!/usr/bin/env python3
"""Sorts reachability-metadata.json like the GraalVM Tracing Agent, so a diff only shows real changes.

Usage: sort-reachability-metadata.py [path]   (default: META-INF/native-image/main/reachability-metadata.json)
"""
import json
import sys

path = sys.argv[1] if len(sys.argv) > 1 else "META-INF/native-image/main/reachability-metadata.json"

with open(path, encoding="utf-8") as f:
    data = json.load(f)


def reflection_key(entry):
    # Proxy entries have a list as type and come first
    t = entry["type"]
    return t if isinstance(t, str) else json.dumps(t)


for entry in data["reflection"]:
    if "fields" in entry:
        entry["fields"].sort(key=lambda x: x["name"])
    if "methods" in entry:
        entry["methods"].sort(key=lambda m: (m["name"], m.get("parameterTypes", [])))

data["reflection"].sort(key=reflection_key)
data["resources"].sort(key=lambda e: (e.get("module", ""), e["glob"]))

with open(path, "w", encoding="utf-8") as f:
    f.write(json.dumps(data, indent=2, ensure_ascii=False))
