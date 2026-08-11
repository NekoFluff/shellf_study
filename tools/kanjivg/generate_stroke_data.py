#!/usr/bin/env python3
"""Compiles KanjiVG's per-character stroke SVGs into the app's bundled stroke-data asset.

One-time offline data-prep tool, not part of the Android build. Regenerate only when picking
up a newer KanjiVG release.

Usage:
    curl -L -o /tmp/kanjivg.zip https://github.com/KanjiVG/kanjivg/archive/refs/heads/master.zip
    unzip /tmp/kanjivg.zip -d /tmp/kanjivg
    python3 tools/kanjivg/generate_stroke_data.py /tmp/kanjivg/kanjivg-master

Output: app/src/main/res/raw/stroke_data.json — a JSON object mapping each character to its
ordered list of strokes, read at runtime by StrokeOrderRepository (same res/raw + Context
pattern this app already uses for its bundled pitch-accent dictionary, see
PitchAccentBundledSource). Each stroke is `{"d": ..., "x": ..., "y": ...}`: "d" is the SVG path
"d" attribute (KanjiVG's native 109x109 unit square), and "x"/"y" is where KanjiVG's own
maintainers hand-placed that stroke's number label (from the sibling "StrokeNumbers" <text>
group) — reusing their placement instead of computing one avoids numbers overlapping each
other or the ink, which is exactly what they were curated to avoid. Falls back to the stroke's
own start point when a label is unexpectedly missing (should not happen for well-formed KanjiVG
data, but keeps a single malformed file from failing the whole run).

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

SVG_NS = "{http://www.w3.org/2000/svg}"
PLAIN_FILENAME = re.compile(r"^([0-9a-f]{5})\.svg$")
STROKE_ID = re.compile(r"-s(\d+)$")
LABEL_TRANSFORM = re.compile(r"matrix\(1 0 0 1 (-?[\d.]+) (-?[\d.]+)\)")
FIRST_MOVE = re.compile(r"^[Mm]\s*(-?[\d.]+)[,\s]+(-?[\d.]+)")


def label_positions(root: ET.Element) -> dict[int, tuple[float, float]]:
    """Maps stroke number -> (x, y) from KanjiVG's own "StrokeNumbers_<code>" <text> group."""
    positions: dict[int, tuple[float, float]] = {}
    for g in root.iter(f"{SVG_NS}g"):
        if not g.get("id", "").startswith("kvg:StrokeNumbers_"):
            continue
        for text_el in g.findall(f"{SVG_NS}text"):
            number_text = (text_el.text or "").strip()
            match = LABEL_TRANSFORM.search(text_el.get("transform", ""))
            if match and number_text.isdigit():
                positions[int(number_text)] = (float(match.group(1)), float(match.group(2)))
    return positions


def strokes_for_svg(svg_path: Path) -> list[dict]:
    root = ET.parse(svg_path).getroot()
    numbered = []
    for path_el in root.iter(f"{SVG_NS}path"):
        match = STROKE_ID.search(path_el.get("id", ""))
        d = path_el.get("d")
        if match and d:
            numbered.append((int(match.group(1)), d.strip()))
    numbered.sort(key=lambda pair: pair[0])

    labels = label_positions(root)
    strokes = []
    for number, d in numbered:
        if number in labels:
            x, y = labels[number]
        else:
            move_match = FIRST_MOVE.match(d)
            x, y = (float(move_match.group(1)), float(move_match.group(2))) if move_match else (0.0, 0.0)
        strokes.append({"d": d, "x": x, "y": y})
    return strokes


def main() -> None:
    if len(sys.argv) != 2:
        print(f"usage: {sys.argv[0]} <path-to-kanjivg-checkout>", file=sys.stderr)
        raise SystemExit(1)

    kanji_dir = Path(sys.argv[1]) / "kanji"
    if not kanji_dir.is_dir():
        print(f"no kanji/ directory found under {sys.argv[1]}", file=sys.stderr)
        raise SystemExit(1)

    strokes_by_character: dict[str, list[dict]] = {}
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
