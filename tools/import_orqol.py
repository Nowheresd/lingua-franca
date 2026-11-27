# -*- coding: utf-8 -*-
"""把群友授权使用的 orqol 词典转成我们的 TSV 格式。"""
import json, os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
src = os.path.join(ROOT, "build", "orqol", "assets", "orqol", "lang", "zh_overrides.json")
dst = os.path.join(ROOT, "data", "orqol-zh.tsv")

d = json.load(open(src, encoding="utf-8"))
rows = []
for k, v in d.items():
    k = k.replace("\t", " ").strip()
    v = v.replace("\t", " ").strip()
    if k and v:
        rows.append((k, v))
rows.sort(key=lambda r: r[0].lower())
with open(dst, "w", encoding="utf-8", newline="\n") as f:
    f.write("# 来源：orqol-0.1.0 的内置词典，作者授权使用\n")
    for k, v in rows:
        f.write("%s\t%s\torqol\n" % (k, v))
print("imported", len(rows))
