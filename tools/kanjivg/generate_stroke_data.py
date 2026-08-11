#!/usr/bin/env python3
"""Compiles KanjiVG's per-character stroke SVGs into the app's bundled stroke-data asset.

One-time offline data-prep tool, not part of the Android build. Regenerate only when picking
up a newer KanjiVG release.

Usage:
    curl -L -o /tmp/kanjivg.zip https://github.com/KanjiVG/kanjivg/archive/refs/heads/master.zip
    unzip /tmp/kanjivg.zip -d /tmp/kanjivg
    python3 tools/kanjivg/generate_stroke_data.py /tmp/kanjivg/kanjivg-master

Output: app/src/main/res/raw/stroke_data.json — a JSON object mapping each character to its
ordered list of stroke path-data strings (SVG path "d" attribute syntax, KanjiVG's native
109x109 unit square), read at runtime by StrokeOrderRepository (same res/raw + Context pattern
this app already uses for its bundled pitch-accent dictionary, see PitchAccentBundledSource).
Only stroke geometry is kept — KanjiVG's stroke-number label positions (<text> elements) are
intentionally dropped; the app computes label placement from each stroke's own start point at
render time instead, so the runtime format and this script both stay a single concern:
character -> ordered strokes.

Only "plain" per-character files (name == 5-hex-digit-codepoint + ".svg", e.g. "05b66.svg") are
read: KanjiVG also ships handwriting-style variants ("-Kaisho", "-Insatsu", "-HzFst", ...) for
some characters, which this app has no use for.

See KANJIVG_LICENSE.txt in this directory for the CC BY-SA 3.0 attribution the bundled output
carries.
"""
import json
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

PLAIN_FILENAME = re.compile(r"^([0-9a-f]{5})\.svg$")
STROKE_ID = re.compile(r"-s(\d+)$")


def strokes_for_svg(svg_path: Path) -> list[str]:
    root = ET.parse(svg_path).getroot()
    numbered = []
    for path_el in root.iter("{http://www.w3.org/2000/svg}path"):
        match = STROKE_ID.search(path_el.get("id", ""))
        d = path_el.get("d")
        if match and d:
            numbered.append((int(match.group(1)), d.strip()))
    numbered.sort(key=lambda pair: pair[0])
    return [d for _, d in numbered]


def main() -> None:
    if len(sys.argv) != 2:
        print(f"usage: {sys.argv[0]} <path-to-kanjivg-checkout>", file=sys.stderr)
        raise SystemExit(1)

    kanji_dir = Path(sys.argv[1]) / "kanji"
    if not kanji_dir.is_dir():
        print(f"no kanji/ directory found under {sys.argv[1]}", file=sys.stderr)
        raise SystemExit(1)

    strokes_by_character: dict[str, list[str]] = {}
    for svg_path in sorted(kanji_dir.glob("*.svg")):
        match = PLAIN_FILENAME.match(svg_path.name)
        if not match:
            continue
        character = chr(int(match.group(1), 16))
        strokes = strokes_for_svg(svg_path)
        if strokes:
            strokes_by_character[character] = strokes

    repo_root = Path(__file__).resolve().parent.parent.parent
    out_dir = repo_root / "app" / "src" / "main" / "res" / "raw"
    out_dir.mkdir(parents=True, exist_ok=True)
    out_path = out_dir / "stroke_data.json"
    out_path.write_text(
        json.dumps(strokes_by_character, ensure_ascii=False, separators=(",", ":")),
        encoding="utf-8",
    )
    print(f"wrote {len(strokes_by_character)} characters to {out_path}")


if __name__ == "__main__":
    main()
