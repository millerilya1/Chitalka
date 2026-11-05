#!/usr/bin/env python3
"""Сборка .chitalka. И консоль, и окно на ПК вызывают pack() отсюда."""

from __future__ import annotations

import argparse
import json
import uuid
import zipfile
from dataclasses import dataclass, field
from pathlib import Path

IMAGE_EXT = {".jpg", ".jpeg", ".png", ".webp"}


@dataclass
class ChapterInfo:
    folder: Path
    title: str
    pages: list[Path]


@dataclass
class SourceInfo:
    root: Path
    cover: Path | None
    chapters: list[ChapterInfo] = field(default_factory=list)


def list_pages(chapter: Path) -> list[Path]:
    return sorted(
        (p for p in chapter.iterdir() if p.is_file() and p.suffix.lower() in IMAGE_EXT),
        key=lambda p: p.name,
    )


def find_cover(root: Path) -> Path | None:
    for name in ("cover.jpg", "cover.jpeg", "cover.png", "cover.webp"):
        candidate = root / name
        if candidate.exists():
            return candidate
    images = [
        p for p in root.iterdir()
        if p.is_file() and p.suffix.lower() in IMAGE_EXT and not p.name.startswith(".")
    ]
    return sorted(images, key=lambda p: p.name)[0] if images else None


def inspect_source(root: Path) -> SourceInfo:
    root = root.resolve()
    if not root.is_dir():
        raise FileNotFoundError(f"Нет папки: {root}")
    chapters_root = root / "chapters" if (root / "chapters").is_dir() else root
    dirs = sorted((p for p in chapters_root.iterdir() if p.is_dir()), key=lambda p: p.name)
    chapters = [
        ChapterInfo(folder=folder, title=folder.name, pages=list_pages(folder))
        for folder in dirs
        if list_pages(folder)
    ]
    return SourceInfo(root=root, cover=find_cover(root), chapters=chapters)


def pack(
    source: SourceInfo,
    out: Path,
    title: str,
    author: str,
    date: str,
    description: str,
    cover: Path | None = None,
    tags: list[str] | None = None,
    categories: list[str] | None = None,
    jpeg_quality: int | None = None,
    progress=None,
) -> Path:
    if not source.chapters:
        raise ValueError("Не найдены папки глав с картинками")

    cover = cover if cover and cover.exists() else source.cover
    out = Path(out)
    out.parent.mkdir(parents=True, exist_ok=True)

    clean_tags = [t.strip() for t in (tags or []) if t and t.strip()]
    clean_cats = [t.strip() for t in (categories or []) if t and t.strip()]

    meta = {
        "id": str(uuid.uuid4()),
        "title": title.strip() or source.root.name,
        "author": author.strip() or "Неизвестен",
        "publishedAt": date.strip(),
        "description": description.strip(),
        "cover": "cover.jpg",
        "tags": clean_tags,
        "categories": clean_cats,
        "chapters": [],
    }

    files: list[tuple[Path, str]] = []
    if cover:
        cover_name = "cover.jpg" if cover.suffix.lower() in {".jpg", ".jpeg"} else "cover.png"
        meta["cover"] = cover_name
        files.append((cover, cover_name))

    for index, chapter in enumerate(source.chapters, start=1):
        folder = f"chapters/{index:03d}"
        meta["chapters"].append(
            {
                "id": f"ch-{index:03d}",
                "index": index,
                "title": chapter.title.strip() or f"Глава {index}",
                "path": folder,
            }
        )
        for page_i, page in enumerate(chapter.pages, start=1):
            files.append((page, f"{folder}/{page_i:03d}{page.suffix.lower()}"))

    total = len(files) + 1
    with zipfile.ZipFile(out, "w", compression=zipfile.ZIP_STORED) as zf:
        for i, (path, name) in enumerate(files, start=1):
            if jpeg_quality is not None and path.suffix.lower() in {".jpg", ".jpeg", ".png", ".webp"}:
                try:
                    from PIL import Image
                    import io
                    im = Image.open(path).convert("RGB")
                    buf = io.BytesIO()
                    im.save(buf, format="JPEG", quality=int(jpeg_quality), optimize=True)
                    zf.writestr(name.rsplit(".", 1)[0] + ".jpg", buf.getvalue())
                except Exception:
                    zf.write(path, name)
            else:
                zf.write(path, name)
            if progress:
                progress(i, total, name)
        zf.writestr("meta.json", json.dumps(meta, ensure_ascii=False, indent=2))
        if progress:
            progress(total, total, "meta.json")
    return out


def main() -> None:
    parser = argparse.ArgumentParser(description="Собрать .chitalka из папки с главами")
    parser.add_argument("source", type=Path)
    parser.add_argument("--title", required=True)
    parser.add_argument("--author", default="Неизвестен")
    parser.add_argument("--date", default="")
    parser.add_argument("--description", default="")
    parser.add_argument("--tags", default="", help="Теги через запятую: сёнэн,магия")
    parser.add_argument("--jpeg-quality", type=int, default=None, help="Сжать картинки JPEG 1-100")
    parser.add_argument("--categories", default="", help="Категории через запятую")
    parser.add_argument("--out", type=Path)
    args = parser.parse_args()
    source = inspect_source(args.source)
    out = args.out or args.source.resolve().with_suffix(".chitalka")
    tag_list = [t.strip() for t in args.tags.split(",") if t.strip()]
    cat_list = [t.strip() for t in args.categories.split(",") if t.strip()]
    pack(
        source, out, args.title, args.author, args.date, args.description,
        tags=tag_list, categories=cat_list, jpeg_quality=args.jpeg_quality,
    )
    print(f"Готово: {out}")


if __name__ == "__main__":
    main()
