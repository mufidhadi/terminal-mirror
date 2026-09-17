import os
import plistlib
import subprocess
from pathlib import Path
from PIL import Image
import pytest

from scripts.asset_pipeline.icon_builder import (
    generate_windows_ico,
    generate_android_mipmaps,
    generate_macos_icns,
)
from scripts.asset_pipeline.mac_bundler import create_mac_app_bundle

SOURCE_ICON = Path("/Users/anb-0826014/Pictures/terminal_remote_icon.png")


def test_source_icon_exists():
    assert SOURCE_ICON.exists(), f"Source icon not found at {SOURCE_ICON}"
    with Image.open(SOURCE_ICON) as img:
        assert img.size[0] > 0
        assert img.size[1] > 0


def test_generate_windows_ico(tmp_path):
    output_ico = tmp_path / "app_icon.ico"
    generate_windows_ico(SOURCE_ICON, output_ico)

    assert output_ico.exists()
    assert output_ico.stat().st_size > 0

    with Image.open(output_ico) as img:
        assert img.format == "ICO"
        # Verify multiple sizes are embedded
        assert hasattr(img, "ico") or img.size in [(16, 16), (256, 256)]


def test_generate_android_mipmaps(tmp_path):
    res_dir = tmp_path / "res"
    generate_android_mipmaps(SOURCE_ICON, res_dir)

    expected_sizes = {
        "mipmap-mdpi": (48, 48),
        "mipmap-hdpi": (72, 72),
        "mipmap-xhdpi": (96, 96),
        "mipmap-xxhdpi": (144, 144),
        "mipmap-xxxhdpi": (192, 192),
    }

    for folder, (w, h) in expected_sizes.items():
        launcher = res_dir / folder / "ic_launcher.png"
        launcher_round = res_dir / folder / "ic_launcher_round.png"
        foreground = res_dir / folder / "ic_launcher_foreground.png"

        assert launcher.exists(), f"{launcher} should exist"
        assert launcher_round.exists(), f"{launcher_round} should exist"
        assert foreground.exists(), f"{foreground} should exist"

        with Image.open(launcher) as img:
            assert img.size == (w, h)
        with Image.open(launcher_round) as img:
            assert img.size == (w, h)

    # Adaptive XMLs
    assert (res_dir / "mipmap-anydpi-v26" / "ic_launcher.xml").exists()
    assert (res_dir / "mipmap-anydpi-v26" / "ic_launcher_round.xml").exists()
    assert (res_dir / "values" / "colors.xml").exists()


def test_generate_macos_icns(tmp_path):
    output_icns = tmp_path / "AppIcon.icns"
    generate_macos_icns(SOURCE_ICON, output_icns)

    assert output_icns.exists()
    assert output_icns.stat().st_size > 0

    # Verify file signature using 'file' command
    result = subprocess.run(["file", str(output_icns)], capture_output=True, text=True)
    assert "Mac OS X icon" in result.stdout or "ICNS" in result.stdout


def test_create_mac_app_bundle(tmp_path):
    output_dir = tmp_path / "Applications"
    dummy_bin = tmp_path / "terminal-mirror-mac"
    dummy_bin.write_text("#!/bin/sh\necho 'running'")
    dummy_bin.chmod(0o755)

    icns_path = tmp_path / "AppIcon.icns"
    generate_macos_icns(SOURCE_ICON, icns_path)

    app_path = create_mac_app_bundle(
        app_name="Terminal Mirror",
        binary_path=dummy_bin,
        icns_path=icns_path,
        output_dir=output_dir,
        bundle_id="com.mufid.terminalmirror",
        version="0.1.0",
    )

    assert app_path.exists()
    assert (app_path / "Contents" / "Info.plist").exists()
    assert (app_path / "Contents" / "MacOS" / "terminal-mirror-mac").exists()
    assert (app_path / "Contents" / "MacOS" / "terminal-mirror-launcher").exists()
    assert (app_path / "Contents" / "Resources" / "AppIcon.icns").exists()

    launcher = app_path / "Contents" / "MacOS" / "terminal-mirror-launcher"
    assert os.access(launcher, os.X_OK)

    # Parse Info.plist
    with open(app_path / "Contents" / "Info.plist", "rb") as f:
        plist_data = plistlib.load(f)
    assert plist_data["CFBundleExecutable"] == "terminal-mirror-launcher"
    assert plist_data["CFBundleIdentifier"] == "com.mufid.terminalmirror"
    assert plist_data["CFBundleIconFile"] == "AppIcon"


def test_create_mac_app_bundle_quotes_env_values(tmp_path):
    output_dir = tmp_path / "Applications"
    dummy_bin = tmp_path / "terminal-mirror-mac"
    dummy_bin.write_text("#!/bin/sh\necho 'running'")
    dummy_bin.chmod(0o755)

    icns_path = tmp_path / "AppIcon.icns"
    generate_macos_icns(SOURCE_ICON, icns_path)

    raw_env = tmp_path / "unquoted.env"
    raw_env.write_text(
        "RELAY_AUTH_TOKEN=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef\n"
        "HOST_NAME=MacBook Pro Mas Mufid\n"
        "# Comment line\n"
        "SIMPLE_VAL=hello\n"
    )

    app_path = create_mac_app_bundle(
        app_name="Terminal Mirror",
        binary_path=dummy_bin,
        icns_path=icns_path,
        output_dir=output_dir,
        bundle_id="com.mufid.terminalmirror",
        version="0.1.0",
        env_file_path=raw_env,
    )

    bundled_env = app_path / "Contents" / "Resources" / ".env"
    assert bundled_env.exists()
    content = bundled_env.read_text()
    assert 'HOST_NAME="MacBook Pro Mas Mufid"' in content

    # Test that zsh/sh can source it without syntax error
    res = subprocess.run(["sh", "-n", str(bundled_env)], capture_output=True, text=True)
    assert res.returncode == 0, f"Shell syntax error: {res.stderr}"
    res_zsh = subprocess.run(["zsh", "-n", str(bundled_env)], capture_output=True, text=True)
    assert res_zsh.returncode == 0, f"Zsh syntax error: {res_zsh.stderr}"


def test_build_assets_integration(tmp_path, monkeypatch):
    import scripts.build_assets as ba
    monkeypatch.setattr(ba, "PROJECT_ROOT", tmp_path)

    mac_icns = ba.build_mac_assets(SOURCE_ICON)
    assert mac_icns.exists()
    assert (tmp_path / "apps" / "mac" / "assets" / "AppIcon.icns").exists()

    android_res = ba.build_android_assets(SOURCE_ICON)
    assert android_res.exists()
    assert (tmp_path / "apps" / "android" / "app" / "src" / "main" / "res" / "mipmap-xxxhdpi" / "ic_launcher.png").exists()

    win_ico = ba.build_windows_assets(SOURCE_ICON)
    assert win_ico.exists()
    assert (tmp_path / "apps" / "windows" / "assets" / "terminal_remote_icon.ico").exists()

