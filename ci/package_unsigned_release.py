"""Package an unsigned release plus non-secret provenance; run only without secrets."""
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess

PACKAGE = "io.github.hebadenys.fitnesshub"


def apk_metadata(badging):
    match = re.search(r"^package: name='([^']+)' versionCode='([0-9]+)' versionName='([^']*)'", badging, re.MULTILINE)
    if not match or match.group(1) != PACKAGE:
        raise ValueError("Unexpected APK package metadata")
    if "application-debuggable" in badging:
        raise ValueError("Release APK must not be debuggable")
    if "android.permission.INTERNET" in badging or "android.permission.health.WRITE_" in badging:
        raise ValueError("This preparation must preserve the offline/read-only candidate")
    if not re.fullmatch(r"[0-9A-Za-z.+-]+", match.group(3)):
        raise ValueError("Unsafe version name")
    if int(match.group(2)) < 1:
        raise ValueError("Invalid version code")
    return {"application_id": match.group(1), "version_code": int(match.group(2)),
            "version_name": match.group(3), "debuggable": False}


def main():
    tools = Path(os.environ["ANDROID_HOME"]) / "build-tools" / "36.0.0"
    for name in ("aapt2", "apksigner", "zipalign"):
        if not (tools / name).is_file():
            raise SystemExit("Required SDK build-tools 36.0.0 missing")
    directory = Path("app/build/outputs/apk/release")
    metadata = json.loads((directory / "output-metadata.json").read_text())
    if metadata.get("applicationId") != PACKAGE or len(metadata.get("elements", [])) != 1:
        raise SystemExit("Expected one release APK")
    element = metadata["elements"][0]
    if element.get("outputFile") != "app-release-unsigned.apk":
        raise SystemExit("Expected the unsigned release output")
    source = directory / element["outputFile"]
    badging = subprocess.check_output([str(tools / "aapt2"), "dump", "badging", str(source)], text=True)
    actual = apk_metadata(badging)
    if actual["version_code"] != element["versionCode"] or actual["version_name"] != element["versionName"]:
        raise SystemExit("APK metadata does not match build metadata")
    if subprocess.run([str(tools / "apksigner"), "verify", str(source)],
                      stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL).returncode == 0:
        raise SystemExit("Release unexpectedly already signed")
    sha = os.environ["GITHUB_SHA"]
    if not re.fullmatch(r"[0-9a-f]{40}", sha):
        raise SystemExit("Invalid source SHA")
    output = Path("unsigned-release")
    output.mkdir(exist_ok=False)
    apk = output / "unsigned.apk"
    shutil.copyfile(source, apk)
    digest = hashlib.sha256(apk.read_bytes()).hexdigest()
    provenance = dict(actual, schema=1, source_sha=sha,
                      repository=os.environ["GITHUB_REPOSITORY"],
                      run_id=os.environ["GITHUB_RUN_ID"],
                      run_attempt=os.environ["GITHUB_RUN_ATTEMPT"],
                      apk_sha256=digest, variant="release")
    (output / "provenance.json").write_text(json.dumps(provenance, indent=2) + "\n")
    if os.environ.get("GITHUB_OUTPUT"):
        with open(os.environ["GITHUB_OUTPUT"], "a") as file:
            file.write("apk_sha256=" + digest + "\n")
    print("Unsigned, non-debuggable offline release verified:", actual["version_name"], digest)


if __name__ == "__main__":
    main()
