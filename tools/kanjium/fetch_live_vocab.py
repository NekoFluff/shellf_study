#!/usr/bin/env python3
"""Fetches the live WaniKani vocabulary headword list for regenerate_key_set.py.

Part of the monthly refresh workflow (.github/workflows/refresh-pitch-accent-data.yml). Run this
before regenerate_key_set.py so its live_vocab_headwords.json snapshot reflects current WaniKani
content rather than whatever it was last committed as.

Usage:
    WANIKANI_REFRESH_TOKEN=<token> python3 tools/kanjium/fetch_live_vocab.py

Output: overwrites tools/kanjium/live_vocab_headwords.json with a JSON array of every vocabulary
subject's `characters`, in API page order.
"""
import json
import os
import subprocess
import sys
import time
from pathlib import Path

API_BASE_URL = "https://api.wanikani.com/v2/subjects?types=vocabulary"
WANIKANI_REVISION = "20170710"
PAGE_DELAY_SECONDS = 1.2

OUTPUT_PATH = Path(__file__).resolve().parent / "live_vocab_headwords.json"


def fetch_page(url: str, token: str) -> dict:
    result = subprocess.run(
        [
            "curl", "-sf",
            "-H", f"Authorization: Bearer {token}",
            "-H", f"Wanikani-Revision: {WANIKANI_REVISION}",
            url,
        ],
        capture_output=True,
    )
    if result.returncode != 0:
        raise RuntimeError(f"curl failed for {url}: {result.stderr.decode('utf-8', errors='replace')}")
    return json.loads(result.stdout.decode("utf-8"))


def main() -> None:
    token = os.environ.get("WANIKANI_REFRESH_TOKEN")
    if not token:
        print("WANIKANI_REFRESH_TOKEN is not set", file=sys.stderr)
        sys.exit(1)

    characters: list[str] = []
    url: str | None = API_BASE_URL
    pages = 0
    while url:
        data = fetch_page(url, token)
        for item in data["data"]:
            chars = item["data"].get("characters")
            if chars:
                characters.append(chars)
        pages += 1
        url = data["pages"]["next_url"]
        if url:
            time.sleep(PAGE_DELAY_SECONDS)

    OUTPUT_PATH.write_text(json.dumps(characters, ensure_ascii=False), encoding="utf-8")
    print(f"fetched {len(characters)} vocabulary headwords across {pages} pages")


if __name__ == "__main__":
    main()
