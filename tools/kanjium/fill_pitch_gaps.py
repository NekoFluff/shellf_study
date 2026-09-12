#!/usr/bin/env python3
"""Fills gaps in the app's bundled pitch-accent dictionary using Kanjium's accents.txt.

One-time offline data-prep tool, not part of the Android/iOS build. Regenerate only if a
newer accents.txt is fetched or the fill policy changes.

Usage:
    curl -L -o tools/kanjium/accents.txt \\
        https://raw.githubusercontent.com/mifunetoshiro/kanjium/master/data/source_files/raw/accents.txt
    python3 tools/kanjium/fill_pitch_gaps.py

Input:  tools/kanjium/accents.txt — tab-delimited "headword\\treading(hiragana)\\tpitch[,pitch...]"
        rows (one row per reading), and the app's current
        shared/src/commonMain/composeResources/files/pitch_info.json.
Output: overwrites pitch_info.json in place, same shape: a JSON object mapping each WaniKani
        vocab headword to a list of [readingKatakana|null, partOfSpeech|null, pitchNumber] tuples.

Policy (see the plan's Part B, Execution plan step 2): the key set is authoritative and never
changes — only fill in headwords whose current value is the empty list []. A key that already
has any entries (however sourced) is left byte-for-byte untouched, even if accents.txt disagrees
with it. accents.txt has no part-of-speech column, so partOfSpeech is always null for the
entries this script writes, matching the existing file's convention for such rows.

Hiragana-to-katakana conversion mirrors KanaUtils.kt's `toKatakana()` (a plain +0x60 codepoint
shift over the hiragana block) since this script can't import Kotlin.
"""
import json
import re
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent.parent
ACCENTS_PATH = Path(__file__).resolve().parent / "accents.txt"
PITCH_INFO_PATH = (
    REPO_ROOT / "shared" / "src" / "commonMain" / "composeResources" / "files" / "pitch_info.json"
)

HIRAGANA_START = ord("ぁ")
HIRAGANA_END = ord("ゖ")
HIRAGANA_TO_KATAKANA_OFFSET = 0x60

# A pitch-numbers cell is comma-separated tokens: either "N" (inherits the part-of-speech tag most
# recently seen in this cell, left-to-right) or "(TAG)N" (sets the tag from here on). Combined tags
# use ";" between parts in accents.txt; the existing pitch_info.json convention for the same
# combination is " ・" (e.g. "名 ・形動"), so normalize to match.
TAGGED_TOKEN = re.compile(r"^\(([^)]+)\)(\d+)$")
BARE_TOKEN = re.compile(r"^\d+$")


def to_katakana(text: str) -> str:
    return "".join(
        chr(ord(c) + HIRAGANA_TO_KATAKANA_OFFSET) if HIRAGANA_START <= ord(c) <= HIRAGANA_END else c
        for c in text
    )


def parse_pitch_cell(cell: str) -> list[tuple[str | None, int]]:
    """Returns [(partOfSpeech, pitchNumber), ...] for one accents.txt pitch-numbers cell."""
    current_tag: str | None = None
    parsed: list[tuple[str | None, int]] = []
    for token in cell.split(","):
        tagged = TAGGED_TOKEN.match(token)
        if tagged:
            current_tag = tagged.group(1).replace(";", " ・")
            pitch = int(tagged.group(2))
        else:
            assert BARE_TOKEN.match(token), f"unrecognized pitch token: {token!r}"
            pitch = int(token)
        parsed.append((current_tag, pitch))
    return parsed


def parse_accents(path: Path) -> dict[str, list[list]]:
    """headword -> list of [readingKatakana, partOfSpeech|None, pitchNumber], preserving file order."""
    by_headword: dict[str, list[list]] = {}
    with path.open(encoding="utf-8") as f:
        for line in f:
            line = line.rstrip("\n")
            if not line:
                continue
            headword, reading_hiragana, pitch_numbers = line.split("\t")
            reading_katakana = to_katakana(reading_hiragana)
            entries = by_headword.setdefault(headword, [])
            for part_of_speech, pitch in parse_pitch_cell(pitch_numbers):
                entries.append([reading_katakana, part_of_speech, pitch])
    return by_headword


def main() -> None:
    accents = parse_accents(ACCENTS_PATH)

    with PITCH_INFO_PATH.open(encoding="utf-8") as f:
        pitch_info: dict[str, list] = json.load(f)

    filled = 0
    still_empty = 0
    for headword, entries in pitch_info.items():
        if entries:
            continue
        replacement = accents.get(headword)
        if replacement:
            pitch_info[headword] = replacement
            filled += 1
        else:
            still_empty += 1

    PITCH_INFO_PATH.write_text(
        json.dumps(pitch_info, ensure_ascii=False, separators=(",", ":")),
        encoding="utf-8",
    )

    size_bytes = PITCH_INFO_PATH.stat().st_size
    print(f"keys: {len(pitch_info)}")
    print(f"previously-empty keys filled from kanjium: {filled}")
    print(f"still empty (no kanjium data either): {still_empty}")
    print(f"output size: {size_bytes} bytes ({size_bytes / 1_000_000:.2f} MB)")


if __name__ == "__main__":
    main()
