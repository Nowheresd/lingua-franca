# -*- coding: utf-8 -*-
"""Build data/vanilla-zh.tsv from vanilla 26.3 en_us / zh_cn language files.

Only "term-like" entries are kept: they are what appears inside item names,
lore, GUI labels and stat lines on a server.
"""
import json, os, re

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LANG = os.path.join(ROOT, "build", "tmp", "lang")
DATA = os.path.join(ROOT, "data")
os.makedirs(DATA, exist_ok=True)

en = json.load(open(os.path.join(LANG, "en_us.json"), encoding="utf-8"))
zh = json.load(open(os.path.join(LANG, "zh_cn.json"), encoding="utf-8"))

PREFIXES = (
    "item.minecraft.", "block.minecraft.", "entity.minecraft.",
    "enchantment.minecraft.", "effect.minecraft.", "potion.minecraft.",
    "attribute.name.", "stat.minecraft.", "container.", "gui.",
    "merchant.", "itemGroup.", "options.", "death.", "multiplayer.",
)
SKIP = re.compile(r"(debug|telemetry|narration|credits|resourcePack|pack\.|language\.|chat\.|controls\.|key\.)")
FMT = re.compile(r"(%[0-9$]*[sd]|\n|§)")
CJK = re.compile(r"[\u4e00-\u9fff]")

best = {}  # lowercased english -> (key, english, chinese)
for key, ev in en.items():
    zv = zh.get(key)
    if not isinstance(ev, str) or not isinstance(zv, str):
        continue
    ev, zv = ev.strip(), zv.strip()
    if not ev or not zv or ev == zv:
        continue
    if len(ev) < 2 or len(ev) > 48 or FMT.search(ev) or not CJK.search(zv):
        continue
    if not re.search(r"[A-Za-z]", ev) or not key.startswith(PREFIXES) or SKIP.search(key):
        continue
    low = ev.lower()
    prev = best.get(low)
    if prev is None or len(key) < len(prev[0]):
        best[low] = (key, ev, zv)

rows = sorted(best.values(), key=lambda r: r[0])
with open(os.path.join(DATA, "vanilla-zh.tsv"), "w", encoding="utf-8", newline="\n") as f:
    for key, ev, zv in rows:
        f.write("%s\t%s\t%s\n" % (ev.replace("\t", " "), zv.replace("\t", " "), key))

print("pairs:", len(rows))
idx = {ev.lower(): (zv, key) for key, ev, zv in rows}
for probe in ("Attack Damage", "Attack Speed", "Mending", "Bane of Arthropods", "Critical Hit",
              "Health", "Fire Aspect", "Sword", "Chestplate", "Enchantment", "Experience"):
    print("  %-20s -> %s" % (probe, idx.get(probe.lower())))
