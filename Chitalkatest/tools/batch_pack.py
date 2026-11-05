#!/usr/bin/env python3
"""Пакетная сборка: каждая подпапка source_root -> отдельный .chitalka"""
from __future__ import annotations
import argparse
from pathlib import Path
from pack_chitalka import inspect_source, pack

def main() -> None:
    p = argparse.ArgumentParser()
    p.add_argument("source_root", type=Path, help="Папка с несколькими мангами")
    p.add_argument("--out-dir", type=Path, default=None)
    p.add_argument("--tags", default="")
    p.add_argument("--jpeg-quality", type=int, default=None)
    args = p.parse_args()
    root = args.source_root.resolve()
    out_dir = (args.out_dir or root).resolve()
    out_dir.mkdir(parents=True, exist_ok=True)
    tags = [t.strip() for t in args.tags.split(",") if t.strip()]
    for child in sorted(root.iterdir()):
        if not child.is_dir():
            continue
        try:
            info = inspect_source(child)
            if not info.chapters:
                print(f"skip {child.name}: нет глав")
                continue
            out = out_dir / f"{child.name}.chitalka"
            pack(info, out, child.name, "Неизвестен", "", "", tags=tags, jpeg_quality=args.jpeg_quality)
            print(f"OK {out}")
        except Exception as e:
            print(f"FAIL {child.name}: {e}")

if __name__ == "__main__":
    main()
