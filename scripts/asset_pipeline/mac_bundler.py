"""macOS App Bundler module for creating Terminal Mirror.app."""

import os
import plistlib
import shutil
from pathlib import Path
from typing import Optional


def sanitize_env_file(src_path: Path, dst_path: Path) -> None:
    """Read env file and ensure values are properly quoted so shell sourcing works."""
    lines = src_path.read_text(encoding="utf-8").splitlines()
    sanitized_lines = []
    for line in lines:
        stripped = line.strip()
        if not stripped or stripped.startswith("#"):
            sanitized_lines.append(line)
            continue
        if "=" in line:
            key, val = line.split("=", 1)
            key = key.strip()
            val = val.strip()
            if (val.startswith('"') and val.endswith('"')) or (val.startswith("'") and val.endswith("'")):
                sanitized_lines.append(f'{key}={val}')
            else:
                sanitized_lines.append(f'{key}="{val}"')
        else:
            sanitized_lines.append(line)
    dst_path.write_text("\n".join(sanitized_lines) + "\n", encoding="utf-8")


def create_mac_app_bundle(
    app_name: str,
    binary_path: Path,
    icns_path: Path,
    output_dir: Path,
    bundle_id: str = "com.mufid.terminalmirror",
    version: str = "0.1.0",
    env_file_path: Optional[Path] = None,
) -> Path:
    """Create a fully-formed macOS .app bundle."""
    binary_path = Path(binary_path)
    icns_path = Path(icns_path)
    output_dir = Path(output_dir)

    app_bundle = output_dir / f"{app_name}.app"
    contents_dir = app_bundle / "Contents"
    macos_dir = contents_dir / "MacOS"
    resources_dir = contents_dir / "Resources"

    macos_dir.mkdir(parents=True, exist_ok=True)
    resources_dir.mkdir(parents=True, exist_ok=True)

    # 1. Info.plist
    info_plist_data = {
        "CFBundleDevelopmentRegion": "en",
        "CFBundleDisplayName": app_name,
        "CFBundleExecutable": "terminal-mirror-launcher",
        "CFBundleIconFile": "AppIcon",
        "CFBundleIdentifier": bundle_id,
        "CFBundleInfoDictionaryVersion": "6.0",
        "CFBundleName": app_name,
        "CFBundlePackageType": "APPL",
        "CFBundleShortVersionString": version,
        "CFBundleVersion": version,
        "LSMinimumSystemVersion": "12.0",
        "NSHighResolutionCapable": True,
    }

    with open(contents_dir / "Info.plist", "wb") as f:
        plistlib.dump(info_plist_data, f)

    # 2. Copy binary
    target_binary = macos_dir / "terminal-mirror-mac"
    shutil.copy2(binary_path, target_binary)
    target_binary.chmod(0o755)

    # 3. Create launcher script
    launcher_script = macos_dir / "terminal-mirror-launcher"
    launcher_content = """#!/bin/bash
DIR="$(cd "$(dirname "$0")" && pwd)"
RESOURCES_DIR="$DIR/../Resources"

osascript -e 'tell application "Terminal" to activate' \\
          -e "tell application \\"Terminal\\" to do script \\"cd \\\\\\"$HOME\\\\\\" && if [ -f \\\\\\"$RESOURCES_DIR/.env\\\\\\" ]; then set -a; source \\\\\\"$RESOURCES_DIR/.env\\\\\\"; set +a; fi; exec \\\\\\"$DIR/terminal-mirror-mac\\\\\\"\\""
"""
    launcher_script.write_text(launcher_content, encoding="utf-8")
    launcher_script.chmod(0o755)

    # 4. Copy Icon
    shutil.copy2(icns_path, resources_dir / "AppIcon.icns")

    # 5. Optional .env
    if env_file_path and Path(env_file_path).exists():
        sanitize_env_file(Path(env_file_path), resources_dir / ".env")

    return app_bundle
