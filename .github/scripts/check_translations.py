"""Checks that every language the app offers is fully translated (run by the PR check workflow).

For each language in res/xml/locales_config.xml, its values folder must contain every translatable string, plural
and array from the English values folder, with arrays of the same length, the same placeholders, and valid
apostrophe escapes. The Settings language list must match locales_config.xml.
"""
import os
import re
import sys
import xml.etree.ElementTree as ET

ROOT = os.path.join(os.path.dirname(__file__), "..", "..", "app", "src", "main")
RES = os.path.join(ROOT, "res")
ANDROID = "{http://schemas.android.com/apk/res/android}"
# Android resource folders for language codes that differ from the code itself
FOLDERS = {"zh-Hans": "b+zh+Hans", "zh-Hant": "b+zh+Hant", "fil": "b+fil"}
PLACEHOLDER = re.compile(r"%(?:\d+\$)?[sd]")
UNESCAPED_APOSTROPHE = re.compile(r"(?<!\\)'")

errors = []


def load(folder):
    """@return {(tag, name): text or [items] or {quantity: text}} for translatable entries."""
    entries = {}
    for file in ("strings.xml", "arrays.xml"):
        path = os.path.join(RES, folder, file)
        if not os.path.isfile(path):
            errors.append(f"{folder}/{file} is missing")
            continue
        try:
            root = ET.parse(path).getroot()
        except ET.ParseError as e:
            errors.append(f"{folder}/{file}: {e}")
            continue
        for e in root:
            if e.get("translatable") == "false":
                continue
            key = (e.tag, e.get("name"))
            if e.tag == "plurals":
                entries[key] = {i.get("quantity"): i.text or "" for i in e}
            elif e.tag.endswith("array"):
                entries[key] = [i.text or "" for i in e]
            else:
                entries[key] = e.text or ""
    return entries


def texts(value):
    if isinstance(value, dict):
        return list(value.values())
    return value if isinstance(value, list) else [value]


def placeholders(text):
    return sorted(PLACEHOLDER.findall(text))


config = ET.parse(os.path.join(RES, "xml", "locales_config.xml")).getroot()
languages = [e.get(ANDROID + "name") for e in config]

settings = open(os.path.join(ROOT, "java", "np", "exendahal", "summitlauncher", "SettingsFragment.java"),
                encoding="utf-8").read()
codes = re.search(r"LANG_CODES = \{([^}]*)\}", settings)
if codes and re.findall(r'"([^"]+)"', codes.group(1)) != languages:
    errors.append("SettingsFragment.LANG_CODES must list the same languages, in the same order, as locales_config.xml")

english = load("values")
for language in languages:
    if language == "en":
        continue
    folder = "values-" + FOLDERS.get(language, language)
    translated = load(folder)
    for key, source in english.items():
        name = key[1]
        if key not in translated:
            errors.append(f"{folder}: missing {key[0]} '{name}'")
            continue
        value = translated[key]
        if isinstance(source, list) and len(source) != len(value):
            errors.append(f"{folder}: array '{name}' has {len(value)} items; English has {len(source)}")
        if isinstance(source, dict):
            if "other" not in value:
                errors.append(f"{folder}: plurals '{name}' needs an 'other' item")
            want = placeholders(source["other"])
            for quantity, text in value.items():
                if placeholders(text) != want and not (quantity in ("one", "zero", "two") and not placeholders(text)):
                    errors.append(f"{folder}: plurals '{name}' ({quantity}) placeholders differ from English")
        elif isinstance(source, str) and placeholders(source) != placeholders(value):
            errors.append(f"{folder}: '{name}' placeholders {placeholders(value)} differ from English {placeholders(source)}")
        for text in texts(value):
            if UNESCAPED_APOSTROPHE.search(text) and not (text.startswith('"') and text.endswith('"')):
                errors.append(f"{folder}: '{name}' has an unescaped apostrophe; write \\'")
    for key in translated:
        if key not in english:
            errors.append(f"{folder}: '{key[1]}' is not in the English strings")

if errors:
    print("\n".join(f"::error::{e}" for e in errors))
    sys.exit(1)
print(f"{len(languages)} languages, {len(english)} entries each: OK")
