#!/usr/bin/env python3
"""Окно для сборки .chitalka — без консоли, с превью и списком глав."""

from __future__ import annotations

import os
import re
import sys
import threading
import tkinter as tk
from datetime import date
from pathlib import Path
from tkinter import filedialog, messagebox, ttk

sys.path.insert(0, str(Path(__file__).resolve().parent))
from pack_chitalka import SourceInfo, inspect_source, pack

INK = "#0E0C0A"
SURFACE = "#1A1612"
SURFACE_HIGH = "#2A241E"
PAPER = "#F4EDE1"
MUTED = "#D9CBB8"
ACCENT = "#C45C26"
ACCENT_SOFT = "#E8A87C"


def safe_filename(name: str) -> str:
    cleaned = re.sub(r'[<>:"/\\|?*]', "", name).strip() or "manga"
    return cleaned[:80]


class MakerApp(tk.Tk):
    def __init__(self) -> None:
        super().__init__()
        self.title("Читалка — сборка .chitalka")
        self.geometry("1040x760")
        self.minsize(820, 560)
        self.configure(bg=INK)
        self.source: SourceInfo | None = None
        self.cover_path: Path | None = None
        self.cover_image = None
        self._busy = False

        self._style()
        self._build()

    def _style(self) -> None:
        style = ttk.Style(self)
        style.theme_use("clam")
        style.configure(".", background=INK, foreground=PAPER, fieldbackground=SURFACE, bordercolor=SURFACE_HIGH)
        style.configure("TFrame", background=INK)
        style.configure("Card.TFrame", background=SURFACE)
        style.configure("TLabel", background=INK, foreground=PAPER, font=("Segoe UI", 10))
        style.configure("Muted.TLabel", background=INK, foreground=MUTED)
        style.configure("Title.TLabel", background=INK, foreground=PAPER, font=("Segoe UI Semibold", 18))
        style.configure("TEntry", fieldbackground=SURFACE_HIGH, foreground=PAPER, insertcolor=PAPER)
        style.configure("TButton", background=SURFACE_HIGH, foreground=PAPER, padding=8, font=("Segoe UI", 10))
        style.map("TButton", background=[("active", ACCENT)], foreground=[("active", INK)])
        style.configure("Accent.TButton", background=ACCENT, foreground=INK, padding=12, font=("Segoe UI Semibold", 12))
        style.map("Accent.TButton", background=[("active", ACCENT_SOFT)])
        style.configure("Save.TButton", background=ACCENT, foreground=INK, padding=14, font=("Segoe UI Semibold", 14))
        style.map("Save.TButton", background=[("active", ACCENT_SOFT)])
        style.configure("Treeview", background=SURFACE, foreground=PAPER, fieldbackground=SURFACE, rowheight=28)
        style.configure("Treeview.Heading", background=SURFACE_HIGH, foreground=PAPER, font=("Segoe UI Semibold", 10))
        style.map("Treeview", background=[("selected", ACCENT)], foreground=[("selected", INK)])
        style.configure("Horizontal.TProgressbar", troughcolor=SURFACE_HIGH, background=ACCENT)

    def _build(self) -> None:
        self.title_var = tk.StringVar()
        self.author_var = tk.StringVar()
        self.tags_var = tk.StringVar()
        self.quality_var = tk.StringVar(value="")
        self.date_var = tk.StringVar(value=str(date.today()))
        self.out_var = tk.StringVar()

        footer = tk.Frame(self, bg=SURFACE, highlightbackground=ACCENT, highlightthickness=2)
        footer.pack(side="bottom", fill="x")
        inner = ttk.Frame(footer)
        inner.pack(fill="x", padx=16, pady=12)
        ttk.Label(inner, text="Куда сохранить файл").pack(anchor="w")
        path_row = ttk.Frame(inner)
        path_row.pack(fill="x", pady=(4, 10))
        ttk.Entry(path_row, textvariable=self.out_var).pack(side="left", fill="x", expand=True)
        ttk.Button(path_row, text="Обзор…", command=self.pick_out).pack(side="left", padx=8)
        self.progress = ttk.Progressbar(inner, mode="determinate")
        self.progress.pack(fill="x", pady=(0, 8))
        actions = ttk.Frame(inner)
        actions.pack(fill="x")
        self.status = ttk.Label(actions, text="Сначала выберите папку с главами", style="Muted.TLabel")
        self.status.pack(side="left")
        self.save_button = ttk.Button(
            actions,
            text="💾  Сохранить .chitalka",
            style="Save.TButton",
            command=self.build,
        )
        self.save_button.pack(side="right")
        self.bind("<Control-s>", lambda _e: self.build())

        ttk.Label(self, text="Сборка файла манги", style="Title.TLabel").pack(anchor="w", padx=20, pady=(18, 4))
        ttk.Label(
            self,
            text="1) Папка с главами  →  2) проверьте название  →  3) оранжевая кнопка внизу «Сохранить .chitalka»",
            style="Muted.TLabel",
        ).pack(anchor="w", padx=20)

        top = ttk.Frame(self)
        top.pack(fill="x", padx=20, pady=12)
        ttk.Button(top, text="Папка с мангой…", command=self.pick_folder).pack(side="left")
        ttk.Button(top, text="Обложка…", command=self.pick_cover).pack(side="left", padx=8)
        ttk.Button(top, text="Сохранить .chitalka", style="Accent.TButton", command=self.build).pack(side="right")
        self.folder_label = ttk.Label(top, text="Папка не выбрана", style="Muted.TLabel")
        self.folder_label.pack(side="left", padx=12)

        body = ttk.Frame(self)
        body.pack(fill="both", expand=True, padx=20, pady=(0, 8))

        left = ttk.Frame(body)
        left.pack(side="left", fill="y", padx=(0, 16))
        ttk.Label(left, text="Обложка").pack(anchor="w")
        self.cover_canvas = tk.Canvas(left, width=200, height=280, bg=SURFACE_HIGH, highlightthickness=0)
        self.cover_canvas.pack(pady=8)
        self.cover_canvas.create_text(100, 140, text="нет превью", fill=MUTED, tags="hint")

        form = ttk.Frame(body)
        form.pack(side="left", fill="both", expand=True)

        self._field(form, "Название", self.title_var)
        self._field(form, "Автор", self.author_var)
        self._field(form, "Теги (через запятую)", self.tags_var)
        self._field(form, "JPEG качество 1-100 (пусто = без сжатия)", self.quality_var)
        self._field(form, "Дата публикации", self.date_var)

        ttk.Label(form, text="Описание").pack(anchor="w", pady=(8, 2))
        self.description = tk.Text(
            form, height=3, bg=SURFACE_HIGH, fg=PAPER, insertbackground=PAPER,
            relief="flat", font=("Segoe UI", 10), wrap="word"
        )
        self.description.pack(fill="x")

        ttk.Label(form, text="Главы  —  двойной клик меняет название").pack(anchor="w", pady=(12, 4))
        chapter_bar = ttk.Frame(form)
        chapter_bar.pack(fill="x")
        ttk.Button(chapter_bar, text="Выше", command=lambda: self.move_chapter(-1)).pack(side="left")
        ttk.Button(chapter_bar, text="Ниже", command=lambda: self.move_chapter(1)).pack(side="left", padx=6)

        cols = ("n", "pages", "title")
        self.tree = ttk.Treeview(form, columns=cols, show="headings", selectmode="browse", height=6)
        self.tree.heading("n", text="#")
        self.tree.heading("pages", text="Стр.")
        self.tree.heading("title", text="Название главы")
        self.tree.column("n", width=50, anchor="center")
        self.tree.column("pages", width=70, anchor="center")
        self.tree.column("title", width=360)
        self.tree.pack(fill="both", expand=True, pady=(6, 0))
        self.tree.bind("<Double-1>", self.rename_chapter)

    def _field(self, parent: ttk.Frame, label: str, var: tk.StringVar) -> None:
        ttk.Label(parent, text=label).pack(anchor="w", pady=(8, 2))
        ttk.Entry(parent, textvariable=var).pack(fill="x")

    def pick_folder(self) -> None:
        chosen = filedialog.askdirectory(title="Папка с главами")
        if not chosen:
            return
        try:
            info = inspect_source(Path(chosen))
        except Exception as exc:
            messagebox.showerror("Папка", str(exc))
            return
        if not info.chapters:
            messagebox.showwarning("Папка", "Внутри нет подпапок с картинками глав.")
            return
        self.source = info
        self.cover_path = info.cover
        self.folder_label.config(text=str(info.root))
        if not self.title_var.get():
            self.title_var.set(info.root.name)
        self.refresh_chapters()
        self.show_cover()
        desktop = Path.home() / "Desktop"
        target_dir = desktop if desktop.exists() else info.root.parent
        self.out_var.set(str(target_dir / f"{safe_filename(self.title_var.get())}.chitalka"))
        pages = sum(len(ch.pages) for ch in info.chapters)
        self.status.config(text=f"{len(info.chapters)} глав, {pages} стр.")

    def pick_cover(self) -> None:
        chosen = filedialog.askopenfilename(
            title="Обложка",
            filetypes=[("Картинки", "*.jpg;*.jpeg;*.png;*.webp"), ("Все файлы", "*.*")],
        )
        if not chosen:
            return
        self.cover_path = Path(chosen)
        self.show_cover()

    def pick_out(self) -> None:
        chosen = filedialog.asksaveasfilename(
            title="Сохранить как",
            defaultextension=".chitalka",
            filetypes=[("Читалка", "*.chitalka")],
            initialfile=Path(self.out_var.get()).name if self.out_var.get() else "manga.chitalka",
        )
        if chosen:
            self.out_var.set(chosen)

    def refresh_chapters(self) -> None:
        self.tree.delete(*self.tree.get_children())
        if not self.source:
            return
        for i, chapter in enumerate(self.source.chapters, start=1):
            self.tree.insert("", "end", iid=str(i - 1), values=(i, len(chapter.pages), chapter.title))

    def selected_index(self) -> int | None:
        item = self.tree.focus()
        return int(item) if item else None

    def move_chapter(self, delta: int) -> None:
        if not self.source:
            return
        index = self.selected_index()
        if index is None:
            return
        target = index + delta
        if target < 0 or target >= len(self.source.chapters):
            return
        chapters = self.source.chapters
        chapters[index], chapters[target] = chapters[target], chapters[index]
        self.refresh_chapters()
        self.tree.selection_set(str(target))
        self.tree.focus(str(target))

    def rename_chapter(self, _event=None) -> None:
        if not self.source:
            return
        index = self.selected_index()
        if index is None:
            return
        chapter = self.source.chapters[index]
        popup = tk.Toplevel(self)
        popup.title("Название главы")
        popup.configure(bg=INK)
        popup.geometry("420x140")
        ttk.Label(popup, text="Название главы").pack(anchor="w", padx=16, pady=(16, 4))
        var = tk.StringVar(value=chapter.title)
        entry = ttk.Entry(popup, textvariable=var)
        entry.pack(fill="x", padx=16)
        entry.focus_set()

        def save() -> None:
            chapter.title = var.get().strip() or chapter.title
            self.refresh_chapters()
            popup.destroy()

        ttk.Button(popup, text="Сохранить", style="Accent.TButton", command=save).pack(pady=16)
        popup.bind("<Return>", lambda _e: save())

    def show_cover(self) -> None:
        self.cover_canvas.delete("all")
        path = self.cover_path
        if not path or not path.exists():
            self.cover_canvas.create_text(100, 140, text="нет обложки", fill=MUTED)
            return
        try:
            from PIL import Image, ImageTk
        except ImportError:
            self.cover_canvas.create_text(100, 140, text=path.name, fill=MUTED, width=180)
            return
        image = Image.open(path).convert("RGB")
        image.thumbnail((200, 280))
        self.cover_image = ImageTk.PhotoImage(image)
        self.cover_canvas.create_image(100, 140, image=self.cover_image)

    def build(self) -> None:
        if self._busy:
            return
        if not self.source:
            messagebox.showinfo("Сборка", "Сначала выберите папку с главами.")
            return
        out = Path(self.out_var.get().strip() or "")
        if not out.name:
            messagebox.showinfo("Сборка", "Укажите путь для файла.")
            return
        if out.suffix.lower() != ".chitalka":
            out = out.with_suffix(".chitalka")
            self.out_var.set(str(out))

        self._busy = True
        self.status.config(text="Собираю файл…")
        self.progress["value"] = 0

        title = self.title_var.get()
        author = self.author_var.get()
        published = self.date_var.get()
        description = self.description.get("1.0", "end").strip()
        tag_list = [x.strip() for x in self.tags_var.get().split(",") if x.strip()]
        qraw = self.quality_var.get().strip()
        jpeg_q = int(qraw) if qraw.isdigit() else None
        source = self.source
        cover = self.cover_path

        def work() -> None:
            try:
                def on_progress(current: int, total: int, name: str) -> None:
                    self.after(0, lambda: self._tick(current, total, name))

                result = pack(
                    source, out, title, author, published, description,
                    cover=cover, tags=tag_list, jpeg_quality=jpeg_q, progress=on_progress,
                )
                self.after(0, lambda: self._done(result, None))
            except Exception as exc:
                self.after(0, lambda: self._done(None, exc))

        threading.Thread(target=work, daemon=True).start()

    def _tick(self, current: int, total: int, name: str) -> None:
        self.progress["maximum"] = max(total, 1)
        self.progress["value"] = current
        self.status.config(text=f"{current}/{total}  {name}")

    def _done(self, result: Path | None, error: Exception | None) -> None:
        self._busy = False
        if error:
            self.status.config(text="Ошибка сборки")
            messagebox.showerror("Сборка", str(error))
            return
        self.status.config(text=f"Готово: {result}")
        if messagebox.askyesno("Готово", f"Файл собран:\n{result}\n\nОткрыть папку?"):
            os.startfile(result.parent)  # type: ignore[attr-defined]


def main() -> None:
    app = MakerApp()
    app.mainloop()


if __name__ == "__main__":
    main()
