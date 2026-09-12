#!/usr/bin/env python3
"""Resyncs pitch_info.json's key set to the app's *live* WaniKani vocabulary list.

fill_pitch_gaps.py only ever filled gaps in the existing key set; it never added keys for
vocabulary WaniKani has added since pitch_info.json's original (Smouldering-Durtles-derived)
snapshot was taken, and never dropped keys for vocabulary WaniKani has since renamed/retired.
This script fixes the key set itself, then defers to fill_pitch_gaps.py's parsing to source data
for whatever's new.

Usage:
    python3 tools/kanjium/regenerate_key_set.py

Input:  tools/kanjium/live_vocab_headwords.json — a JSON array of every vocabulary subject's
        `characters`, fetched live from GET /v2/subjects?types=vocabulary (see the plan/session
        history for how this snapshot was produced; refresh it by re-fetching from the API before
        re-running this script if it goes stale again).
        tools/kanjium/accents.txt — same Kanjium source fill_pitch_gaps.py reads.
        The app's current pitch_info.json.
Output: overwrites pitch_info.json in place. New key set = exactly the live vocab list.
        - A live headword that already had non-empty data keeps it, untouched.
        - A live headword that's new or was empty gets Kanjium's entry if one exists, else [].
        - A headword no longer in the live list is dropped (dead weight from renamed/retired
          WaniKani content).
"""
import json
from pathlib import Path

from fill_pitch_gaps import ACCENTS_PATH, PITCH_INFO_PATH, parse_accents

LIVE_VOCAB_PATH = Path(__file__).resolve().parent / "live_vocab_headwords.json"


def main() -> None:
    accents = parse_accents(ACCENTS_PATH)
    live_vocab: list[str] = json.loads(LIVE_VOCAB_PATH.read_text(encoding="utf-8"))
    current: dict[str, list] = json.loads(PITCH_INFO_PATH.read_text(encoding="utf-8"))

    added_new = 0
    filled_from_kanjium = 0
    kept_existing = 0
    still_empty = 0
    regenerated: dict[str, list] = {}

    for headword in live_vocab:
        existing = current.get(headword)
        if existing:
            regenerated[headword] = existing
            kept_existing += 1
            continue
        if existing is None:
            added_new += 1
        replacement = accents.get(headword)
        if replacement:
            regenerated[headword] = replacement
            filled_from_kanjium += 1
        else:
            regenerated[headword] = []
            still_empty += 1

    dropped = set(current) - set(live_vocab)

    PITCH_INFO_PATH.write_text(
        json.dumps(regenerated, ensure_ascii=False, separators=(",", ":")),
        encoding="utf-8",
    )

    size_bytes = PITCH_INFO_PATH.stat().st_size
    print(f"live vocab keys: {len(live_vocab)}")
    print(f"kept existing (already had data): {kept_existing}")
    print(f"new headwords added: {added_new}")
    print(f"filled from kanjium (new + previously-empty): {filled_from_kanjium}")
    print(f"still empty (no kanjium data either): {still_empty}")
    print(f"stale headwords dropped (no longer live vocab): {len(dropped)}")
    print(f"output size: {size_bytes} bytes ({size_bytes / 1_000_000:.2f} MB)")


if __name__ == "__main__":
    main()
