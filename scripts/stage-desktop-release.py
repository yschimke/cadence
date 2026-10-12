"""Give native installers unique OS/architecture names before release upload."""

import os
from pathlib import Path
import shutil

platform = os.environ["PLATFORM"]
format = os.environ["FORMAT"]
tag = os.environ.get("RELEASE_TAG", "")
packages = list(Path("desktopApp/build/compose/binaries/main").glob(f"{format}/*.{format}"))
if len(packages) != 1:
    raise SystemExit(f"Expected one {format} installer, found {packages}")
version = tag.removeprefix("v") if tag else "ci"
staging = Path("_desktop-release")
staging.mkdir(exist_ok=True)
target = staging / f"cadence-{version}-{platform}.{format}"
shutil.copy2(packages[0], target)
print(target)
