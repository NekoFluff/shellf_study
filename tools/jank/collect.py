#!/usr/bin/env python3
"""Collects Shellf Study's jank harness output into a file you can hand over.

The app's JankStats harness logs per-frame timings and a periodic summary to logcat under the tag
`ShellfStudyJank`. Reading that by eye is not useful — the interesting lines are the SUMMARY ones,
which arrive every ~600 frames, and the SLOW ones, which name individual stalled frames with the
app state that produced them. This script drains them into one timestamped file.

Usage:
    python3 tools/jank/collect.py                 # everything currently in the buffer
    python3 tools/jank/collect.py --clear         # clear logcat first, then wait
    python3 tools/jank/collect.py --wait 60       # clear, then collect for 60 seconds
    python3 tools/jank/collect.py --out /tmp/x.txt

Requires adb on PATH and exactly one device (or set ANDROID_SERIAL).
"""

from __future__ import annotations

import argparse
import datetime
import pathlib
import shutil
import subprocess
import sys
import time

TAG = "ShellfStudyJank"
DEFAULT_OUT_DIR = pathlib.Path("jank-reports")


def adb_path() -> str:
    adb = shutil.which("adb")
    if not adb:
        sys.exit("adb not found on PATH. Install platform-tools, or set ANDROID_SERIAL once it is.")
    return adb


def run(adb: str, *args: str) -> str:
    result = subprocess.run([adb, *args], capture_output=True, text=True)
    if result.returncode != 0:
        sys.exit(f"adb {' '.join(args)} failed:\n{result.stderr.strip()}")
    return result.stdout


def summarise(text: str) -> str:
    """A short digest of the collected window, printed as well as written.

    Deliberately counts rather than averages: the question the harness exists to answer is "how bad
    is the worst of it, and what was on screen", and a mean over a 700 ms stall and a thousand 6 ms
    frames hides exactly the thing being looked for.
    """
    lines = [ln for ln in text.splitlines() if TAG in ln]
    summaries = [ln for ln in lines if "SUMMARY" in ln]
    slow = [ln for ln in lines if "SLOW" in ln]

    out = [f"log lines: {len(lines)}  summaries: {len(summaries)}  slow frames: {len(slow)}"]
    if not lines:
        out.append(
            "Nothing captured. Either the app was not run, or the harness is off — it is enabled only "
            "on debuggable builds. Confirm tracking started with:\n"
            f"    adb logcat -d -s {TAG} | grep 'tracking started'"
        )
        return "\n".join(out)

    if summaries:
        out.append("")
        out.append("summaries (the numbers to compare between builds):")
        out.extend(f"  {ln.split(TAG + ': ', 1)[-1]}" for ln in summaries)

    if slow:
        out.append("")
        out.append("worst 10 slow frames:")
        # Sort by the duration the harness prints, which is the first token of the payload.
        def millis(line: str) -> float:
            payload = line.split("SLOW ", 1)[-1].split("ms", 1)[0]
            try:
                return float(payload)
            except ValueError:
                return 0.0

        for ln in sorted(slow, key=millis, reverse=True)[:10]:
            out.append(f"  {ln.split(TAG + ': ', 1)[-1]}")

    return "\n".join(out)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--clear", action="store_true", help="clear logcat before collecting")
    parser.add_argument(
        "--wait",
        type=int,
        metavar="SECONDS",
        help="after clearing, collect for this long (implies --clear)",
    )
    parser.add_argument("--out", type=pathlib.Path, help="output file (default: jank-reports/<timestamp>.txt)")
    args = parser.parse_args()

    adb = adb_path()

    if args.clear or args.wait:
        run(adb, "logcat", "-c")
        print("cleared logcat", file=sys.stderr)

    if args.wait:
        print(f"collecting for {args.wait}s — use the app now", file=sys.stderr)
        time.sleep(args.wait)

    text = run(adb, "logcat", "-d", "-s", TAG)

    out_path = args.out
    if out_path is None:
        DEFAULT_OUT_DIR.mkdir(exist_ok=True)
        stamp = datetime.datetime.now().strftime("%Y%m%d-%H%M%S")
        out_path = DEFAULT_OUT_DIR / f"jank-{stamp}.txt"

    out_path.write_text(text)

    digest = summarise(text)
    print(digest)
    print(f"\nraw log written to {out_path}", file=sys.stderr)


if __name__ == "__main__":
    main()
