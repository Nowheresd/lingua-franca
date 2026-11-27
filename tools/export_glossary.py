# -*- coding: utf-8 -*-
"""把本地翻译缓存导出成可分享的词表包。

用法：
    python tools/export_glossary.py --game "<游戏目录>" --scope "play.example.com" --out dist/lingua-franca-glossary.tsv

游戏目录通常是：
    %APPDATA%\.minecraft\versions\<版本>\game
"""
import argparse
import io
import os


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--game", default="", help="游戏运行目录（包含 config/cache 的那一层）")
    parser.add_argument("--scope", default="", help="服务器 scope，例如 play.originrealms.com")
    parser.add_argument("--out", default=os.path.join("dist", "lingua-franca-glossary.tsv"))
    args = parser.parse_args()

    game = args.game or os.path.join(
        os.environ.get("APPDATA", ""), ".minecraft",
        "versions", "fabric-loader-0.20.1-26.3", "game")
    cache_dir = os.path.join(game, "config", "cache")
    if not os.path.isdir(cache_dir):
        print("找不到缓存目录：", cache_dir)
        return 1

    files = sorted(f for f in os.listdir(cache_dir) if f.endswith(".tsv"))
    if args.scope:
        files = [f for f in files if args.scope in f]
    if not files:
        print("没有匹配的缓存文件")
        return 1

    rows = []
    seen = set()
    for name in files:
        for line in io.open(os.path.join(cache_dir, name), encoding="utf-8", errors="replace"):
            parts = line.rstrip("\n").split("\t")
            if len(parts) < 2:
                continue
            en, zh = parts[0].strip(), parts[1].strip()
            if not en or not zh or en.startswith("out:"):
                continue
            if len(en) < 2 or len(en) > 120 or "\t" in en or "\t" in zh:
                continue
            key = en.lower()
            if key in seen:
                continue
            seen.add(key)
            rows.append((en, zh))

    rows.sort(key=lambda r: r[0].lower())
    os.makedirs(os.path.dirname(args.out) or ".", exist_ok=True)
    with io.open(args.out, "w", encoding="utf-8", newline="\n") as f:
        f.write("# Lingua Franca 词表包：来自本机缓存的服务器文本，可直接分享\n")
        for en, zh in rows:
            f.write("%s\t%s\tserver\n" % (en, zh))
    print("已导出", len(rows), "条 ->", args.out)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
