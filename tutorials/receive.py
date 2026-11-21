"""Read a single info2ai snapshot or follow new events using Python's standard library."""

import argparse
import json
from pathlib import Path
import sys
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode
from urllib.request import ProxyHandler, Request, build_opener


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("game_dir", type=Path, help="Directory of the running game instance")
    parser.add_argument("--port", type=int, default=27948, help="Port shown in the game log")
    parser.add_argument("--once", action="store_true", help="Print one snapshot and exit")
    args = parser.parse_args()

    base = f"http://127.0.0.1:{args.port}"
    opener = build_opener(ProxyHandler({}))

    def read_json(path, params=None, token=None, timeout=5):
        url = base + path
        if params:
            url += "?" + urlencode(params)
        headers = {"Authorization": f"Bearer {token}"} if token else {}
        with opener.open(Request(url, headers=headers), timeout=timeout) as response:
            return json.load(response)

    try:
        health = read_json("/health")
        if not health["in_world"]:
            parser.exit(1, "Enter a single-player world before reading game data.\n")
        token = (args.game_dir / "config" / "info2ai.token").read_text(encoding="utf-8").strip()

        if args.once:
            state = read_json("/state", {"detail": "normal", "sections": "player,inventory,world"}, token)
            print(json.dumps(state, ensure_ascii=False, indent=2))
            return

        cursor = health["seq"]
        while True:
            page = read_json("/events", {
                "since": cursor, "instance_id": health["instance_id"], "wait_ms": 20000,
            }, token, timeout=30)
            if page["lost"]:
                print("Some live events expired. Check /events/history for retained events.", file=sys.stderr)
            for event in page["events"]:
                print(json.dumps(event, ensure_ascii=False), flush=True)
            cursor = page["next_since"]
            if not page["in_world"]:
                return
    except HTTPError as error:
        reason = error.read().decode("utf-8", errors="replace")
        parser.exit(1, f"info2ai replied with HTTP {error.code}: {reason}\n")
    except (URLError, OSError) as error:
        parser.exit(1, f"Could not read from info2ai or its token file: {error}\n")


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        pass
