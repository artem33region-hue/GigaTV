"""Generate the updater manifest from the signed APK, never from guessed versions."""
import argparse
import hashlib
import json
import re
import subprocess
from pathlib import Path


def main():
    parser = argparse.ArgumentParser()
    for option in ("apk", "aapt", "repository", "tag", "output"):
        parser.add_argument(f"--{option}", required=True)
    args = parser.parse_args()
    if not re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", args.repository):
        parser.error("repository must be owner/repository")
    apk = Path(args.apk)
    badging = subprocess.check_output([args.aapt, "dump", "badging", str(apk)], text=True)
    package = re.search(r"^package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", badging)
    if not package or package[1] != "com.artem.gigatv":
        parser.error("APK is not GigaTV")
    if args.tag != f"v{package[3]}":
        parser.error("tag must match APK versionName")
    info = {
        "versionCode": int(package[2]),
        "versionName": package[3],
        "url": f"https://github.com/{args.repository}/releases/download/{args.tag}/GigaTV.apk",
        "notes": f"GigaTV {package[3]}",
        "sha256": hashlib.sha256(apk.read_bytes()).hexdigest(),
    }
    Path(args.output).write_text(json.dumps(info, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
