#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Validate and import an upstream UAD-ng JSON file, preserving its exact bytes.

Usage: python3 scripts/update_uad_list.py /path/to/uad_lists.json
Source: https://raw.githubusercontent.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation/refs/heads/main/resources/assets/uad_lists.json
"""
import argparse
import json
from pathlib import Path


def validate(data):
    if not isinstance(data, dict) or not data:
        raise ValueError("Expected a non-empty package-keyed UAD list")
    for package, entry in data.items():
        if not isinstance(package, str) or not package or not isinstance(entry, dict):
            raise ValueError("Invalid package entry")
        if entry.get("removal") not in {"Recommended", "Advanced", "Expert", "Unsafe"}:
            raise ValueError(f"Unknown removal rating: {package}")
        if entry.get("list") not in {"Aosp", "Oem", "Carrier", "Google", "Misc"}:
            raise ValueError(f"Unknown list: {package}")
        if not isinstance(entry.get("description"), str):
            raise ValueError(f"Missing description: {package}")
        for key in ("dependencies", "neededBy", "labels"):
            value = entry.get(key)
            if not isinstance(value, list) or any(not isinstance(item, str) for item in value):
                raise ValueError(f"Invalid {key}: {package}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path)
    args = parser.parse_args()
    content = args.source.read_bytes()
    data = json.loads(content)
    validate(data)
    target = Path(__file__).resolve().parents[1] / "app/src/main/assets/uad_lists.json"
    temporary = target.with_suffix(".json.tmp")
    temporary.write_bytes(content)
    temporary.replace(target)
    print(f"Imported {len(data)} UAD entries")


if __name__ == "__main__":
    main()
