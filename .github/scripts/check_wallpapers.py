"""Validates wallpapers/index.json and the files it lists (run by the Wallpapers check workflow)."""
import json
import os
import re
import sys

from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), "..", "..", "wallpapers")
RULES = {"image": ((1920, 1080), 600), "thumb": ((384, 216), 60)}  # size in pixels, max KB
EXTENSIONS = (".png", ".jpg", ".jpeg", ".webp")

errors = []


def check_file(entry, field):
    path = entry.get(field)
    if not isinstance(path, str) or not path:
        errors.append(f"{entry.get('id')}: missing '{field}'")
        return None
    folder = "images/" if field == "image" else "thumbs/"
    if not path.startswith(folder) or not path.lower().endswith(EXTENSIONS):
        errors.append(f"{entry['id']}: '{field}' must be {folder}<id>.png, .jpg or .webp")
    full = os.path.join(ROOT, path)
    if not os.path.isfile(full):
        errors.append(f"{entry['id']}: {path} does not exist")
        return None
    (width, height), max_kb = RULES[field]
    kb = os.path.getsize(full) / 1024
    if kb > max_kb:
        errors.append(f"{entry['id']}: {path} is {kb:.0f} KB; the limit is {max_kb} KB")
    with Image.open(full) as im:
        if im.size != (width, height):
            errors.append(f"{entry['id']}: {path} is {im.size[0]}x{im.size[1]}; it must be {width}x{height}")
    return path


with open(os.path.join(ROOT, "index.json"), encoding="utf-8") as f:
    index = json.load(f)

if index.get("version") != 1 or not isinstance(index.get("wallpapers"), list):
    sys.exit("index.json must have \"version\": 1 and a \"wallpapers\" list")

seen, used = set(), set()
for entry in index["wallpapers"]:
    wid = entry.get("id", "")
    if not re.fullmatch(r"[a-z0-9-]{2,40}", wid):
        errors.append(f"'{wid}': id must be 2-40 lowercase letters, digits or '-'")
    if wid in seen:
        errors.append(f"{wid}: duplicate id")
    seen.add(wid)
    for field in ("name", "author"):
        if not isinstance(entry.get(field), str) or not entry[field].strip():
            errors.append(f"{wid}: missing '{field}'")
    if not isinstance(entry.get("names", {}), dict):
        errors.append(f"{wid}: 'names' must map language codes to names")
    for field in RULES:
        used.add(check_file(entry, field))

for folder in ("images", "thumbs"):
    for name in os.listdir(os.path.join(ROOT, folder)):
        if f"{folder}/{name}" not in used:
            errors.append(f"{folder}/{name} is not listed in index.json")

if errors:
    print("\n".join(f"::error::{e}" for e in errors))
    sys.exit(1)
print(f"{len(index['wallpapers'])} wallpapers OK")
