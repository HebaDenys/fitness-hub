"""Expose the KSP-generated schema as a Git blob for reviewed adoption, never move a ref.

The uploaded JSON describes table definitions only, not a database or user records.
The caller must be a successful main push. Normal build/PR tests need no write token.
"""
import base64
import json
import os
from pathlib import Path
import re
import subprocess


def main():
    if os.environ.get("GITHUB_EVENT_NAME") != "push" or os.environ.get("GITHUB_REF") != "refs/heads/main":
        raise SystemExit("Schema publication is only allowed after a main push")
    repo = os.environ["GITHUB_REPOSITORY"]
    if not re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", repo):
        raise SystemExit("Invalid repository identifier")
    directory = Path("app/schemas/io.github.hebadenys.fitnesshub.core.database.HealthDatabase")
    paths = sorted(directory.glob("[0-9]*.json"), key=lambda path: int(path.stem))
    if not paths:
        raise SystemExit("No exported Room schema found")
    path = paths[-1]
    contents = path.read_bytes()
    data = json.loads(contents)
    if data["database"]["version"] != int(path.stem) or not data["database"].get("entities"):
        raise SystemExit("Invalid generated schema")
    payload = json.dumps({"encoding": "base64", "content": base64.b64encode(contents).decode("ascii")})
    result = subprocess.run(
        ["gh", "api", "--method", "POST", f"repos/{repo}/git/blobs", "--input", "-"],
        input=payload, text=True, capture_output=True, check=True,
    )
    sha = json.loads(result.stdout)["sha"]
    if not re.fullmatch(r"[0-9a-f]{40}", sha):
        raise SystemExit("Invalid generated blob identifier")
    print(f"ROOM_SCHEMA_BLOB={sha} PATH={path.as_posix()} VERSION={path.stem}")
    with open(os.environ["GITHUB_STEP_SUMMARY"], "a", encoding="utf-8") as output:
        output.write(f"\nGenerated Room schema `{path}`: blob `{sha}`. Requires reviewed commit; no branch was modified.\n")


if __name__ == "__main__":
    main()
