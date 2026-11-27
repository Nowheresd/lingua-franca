# -*- coding: utf-8 -*-
"""Prepare vanilla 26.3 language files under build/tmp/lang/.

en_us.json: extracted from the official client jar (authentic for this version).
zh_cn.json: downloaded from the per-version asset mirror.
"""
import hashlib, json, os, urllib.request, zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TMP = os.path.join(ROOT, "build", "tmp")
LANG = os.path.join(TMP, "lang")
os.makedirs(LANG, exist_ok=True)

MC = os.path.join(os.environ["APPDATA"], ".minecraft")
meta = json.load(open(os.path.join(MC, "versions", "26.3", "26.3.json"), encoding="utf-8"))
client = meta["downloads"]["client"]
jar = os.path.join(TMP, "client-26.3.jar")


def sha1(path):
    h = hashlib.sha1()
    with open(path, "rb") as fh:
        for chunk in iter(lambda: fh.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


if not os.path.exists(jar) or sha1(jar) != client["sha1"]:
    print("downloading client jar", client["size"])
    urllib.request.urlretrieve(client["url"], jar + ".part")
    os.replace(jar + ".part", jar)

with zipfile.ZipFile(jar) as z:
    with open(os.path.join(LANG, "en_us.json"), "wb") as fh:
        fh.write(z.read("assets/minecraft/lang/en_us.json"))

url = "https://raw.githubusercontent.com/InventivetalentDev/minecraft-assets/26.3/assets/minecraft/lang/zh_cn.json"
dst = os.path.join(LANG, "zh_cn.json")
if not os.path.exists(dst):
    print("downloading zh_cn")
    urllib.request.urlretrieve(url, dst + ".part")
    os.replace(dst + ".part", dst)

en = json.load(open(os.path.join(LANG, "en_us.json"), encoding="utf-8"))
zh = json.load(open(dst, encoding="utf-8"))
print("en_us keys:", len(en), "zh_cn keys:", len(zh), "paired:", len(set(en) & set(zh)))
