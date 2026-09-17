import argparse
import os
import sys
import subprocess
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parent.parent
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))

from scripts.asset_pipeline.icon_builder import (
    generate_windows_ico,
    generate_android_mipmaps,
    generate_macos_icns,
)
from scripts.asset_pipeline.mac_bundler import create_mac_app_bundle

DEFAULT_SOURCE_ICON = Path("/Users/anb-0826014/Pictures/terminal_remote_icon.png")



def build_mac_assets(source_icon: Path) -> Path:
    mac_assets_dir = PROJECT_ROOT / "apps" / "mac" / "assets"
    mac_assets_dir.mkdir(parents=True, exist_ok=True)
    icns_path = mac_assets_dir / "AppIcon.icns"
    print(f"[Mac] Generating ICNS: {icns_path}")
    generate_macos_icns(source_icon, icns_path)
    return icns_path


def build_android_assets(source_icon: Path) -> Path:
    res_dir = PROJECT_ROOT / "apps" / "android" / "app" / "src" / "main" / "res"
    print(f"[Android] Generating mipmaps in: {res_dir}")
    generate_android_mipmaps(source_icon, res_dir)
    return res_dir


def build_windows_assets(source_icon: Path) -> Path:
    win_assets_dir = PROJECT_ROOT / "apps" / "windows" / "assets"
    win_assets_dir.mkdir(parents=True, exist_ok=True)
    ico_path = win_assets_dir / "terminal_remote_icon.ico"
    print(f"[Windows] Generating ICO: {ico_path}")
    generate_windows_ico(source_icon, ico_path)
    return ico_path


def bundle_and_install_mac_app(icns_path: Path, destination_dir: Path = Path("/Applications")) -> Path:
    print("[Mac] Compiling terminal-mirror-mac release binary...")
    subprocess.run(
        ["cargo", "build", "--release", "-p", "terminal-mirror-mac"],
        cwd=PROJECT_ROOT,
        check=True,
    )

    binary_path = PROJECT_ROOT / "target" / "release" / "terminal-mirror-mac"
    env_file = PROJECT_ROOT / ".env"

    print(f"[Mac] Creating app bundle in: {destination_dir}...")
    app_bundle = create_mac_app_bundle(
        app_name="Terminal Mirror",
        binary_path=binary_path,
        icns_path=icns_path,
        output_dir=destination_dir,
        bundle_id="com.mufid.terminalmirror",
        version="0.1.0",
        env_file_path=env_file if env_file.exists() else None,
    )

    # Touch bundle to refresh Finder & Launchpad cache
    subprocess.run(["touch", str(app_bundle)], check=False)
    print(f"[Mac] Successfully installed bundle: {app_bundle}")
    return app_bundle


def main():
    parser = argparse.ArgumentParser(description="Cross-platform asset & app builder for Terminal Mirror")
    parser.add_argument(
        "--source-icon",
        type=Path,
        default=DEFAULT_SOURCE_ICON,
        help="Path to source icon PNG",
    )
    parser.add_argument("--skip-bundle", action="store_true", help="Skip creating macOS .app bundle")
    parser.add_argument("--dest-dir", type=Path, default=Path("/Applications"), help="Target directory for .app bundle")

    args = parser.parse_args()

    if not args.source_icon.exists():
        raise FileNotFoundError(f"Source icon not found: {args.source_icon}")

    print(f"Using source icon: {args.source_icon}")
    icns_path = build_mac_assets(args.source_icon)
    build_android_assets(args.source_icon)
    build_windows_assets(args.source_icon)

    if not args.skip_bundle:
        bundle_and_install_mac_app(icns_path, destination_dir=args.dest_dir)


if __name__ == "__main__":
    main()
