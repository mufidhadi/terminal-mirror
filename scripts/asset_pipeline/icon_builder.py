"""Icon builder module for generating macOS ICNS, Android Mipmaps, and Windows ICO."""

import os
import shutil
import subprocess
import tempfile
from pathlib import Path
from PIL import Image, ImageDraw


def generate_windows_ico(source_png_path: Path, output_ico_path: Path) -> None:
    """Generate a multi-resolution Windows ICO file from a source PNG."""
    source_png_path = Path(source_png_path)
    output_ico_path = Path(output_ico_path)
    output_ico_path.parent.mkdir(parents=True, exist_ok=True)

    with Image.open(source_png_path) as img:
        img_rgba = img.convert("RGBA")
        sizes = [(16, 16), (24, 24), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)]
        img_rgba.save(output_ico_path, format="ICO", sizes=sizes)


def generate_android_mipmaps(source_png_path: Path, res_dir: Path) -> None:
    """Generate Android launcher mipmaps (standard, round, and adaptive) from a source PNG."""
    source_png_path = Path(source_png_path)
    res_dir = Path(res_dir)

    legacy_sizes = {
        "mipmap-mdpi": (48, 48),
        "mipmap-hdpi": (72, 72),
        "mipmap-xhdpi": (96, 96),
        "mipmap-xxhdpi": (144, 144),
        "mipmap-xxxhdpi": (192, 192),
    }

    adaptive_sizes = {
        "mipmap-mdpi": (108, 108),
        "mipmap-hdpi": (162, 162),
        "mipmap-xhdpi": (216, 216),
        "mipmap-xxhdpi": (324, 324),
        "mipmap-xxxhdpi": (432, 432),
    }

    with Image.open(source_png_path) as img:
        src = img.convert("RGBA")

        for folder, (w, h) in legacy_sizes.items():
            folder_dir = res_dir / folder
            folder_dir.mkdir(parents=True, exist_ok=True)

            # 1. Standard ic_launcher
            launcher = src.resize((w, h), Image.Resampling.LANCZOS)
            launcher.save(folder_dir / "ic_launcher.png", format="PNG")

            # 2. Round ic_launcher_round
            mask = Image.new("L", (w, h), 0)
            draw = ImageDraw.Draw(mask)
            draw.ellipse((0, 0, w - 1, h - 1), fill=255)

            round_img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
            round_img.paste(launcher, (0, 0))
            round_img.putalpha(mask)
            round_img.save(folder_dir / "ic_launcher_round.png", format="PNG")

            # 3. Adaptive foreground ic_launcher_foreground
            aw, ah = adaptive_sizes[folder]
            foreground_canvas = Image.new("RGBA", (aw, ah), (0, 0, 0, 0))
            # Safe zone is central 72%
            content_size = int(aw * 0.72)
            scaled_content = src.resize((content_size, content_size), Image.Resampling.LANCZOS)
            offset = ((aw - content_size) // 2, (ah - content_size) // 2)
            foreground_canvas.paste(scaled_content, offset, scaled_content)
            foreground_canvas.save(folder_dir / "ic_launcher_foreground.png", format="PNG")

    # 4. Adaptive XML files
    anydpi_dir = res_dir / "mipmap-anydpi-v26"
    anydpi_dir.mkdir(parents=True, exist_ok=True)

    adaptive_xml_content = """<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background" />
    <foreground android:drawable="@mipmap/ic_launcher_foreground" />
</adaptive-icon>
"""
    (anydpi_dir / "ic_launcher.xml").write_text(adaptive_xml_content, encoding="utf-8")
    (anydpi_dir / "ic_launcher_round.xml").write_text(adaptive_xml_content, encoding="utf-8")

    # 5. Colors XML
    values_dir = res_dir / "values"
    values_dir.mkdir(parents=True, exist_ok=True)
    colors_xml = """<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="ic_launcher_background">#121212</color>
</resources>
"""
    (values_dir / "colors.xml").write_text(colors_xml, encoding="utf-8")


def generate_macos_icns(source_png_path: Path, output_icns_path: Path) -> None:
    """Generate macOS ICNS using iconutil."""
    source_png_path = Path(source_png_path)
    output_icns_path = Path(output_icns_path)
    output_icns_path.parent.mkdir(parents=True, exist_ok=True)

    icon_specs = [
        ("icon_16x16.png", (16, 16)),
        ("icon_16x16@2x.png", (32, 32)),
        ("icon_32x32.png", (32, 32)),
        ("icon_32x32@2x.png", (64, 64)),
        ("icon_128x128.png", (128, 128)),
        ("icon_128x128@2x.png", (256, 256)),
        ("icon_256x256.png", (256, 256)),
        ("icon_256x256@2x.png", (512, 512)),
        ("icon_512x512.png", (512, 512)),
        ("icon_512x512@2x.png", (1024, 1024)),
    ]

    with tempfile.TemporaryDirectory() as temp_dir:
        iconset_dir = Path(temp_dir) / "AppIcon.iconset"
        iconset_dir.mkdir(parents=True, exist_ok=True)

        with Image.open(source_png_path) as img:
            src = img.convert("RGBA")
            for filename, size in icon_specs:
                resized = src.resize(size, Image.Resampling.LANCZOS)
                resized.save(iconset_dir / filename, format="PNG")

        # Compile iconset to icns with iconutil
        cmd = ["iconutil", "-c", "icns", str(iconset_dir), "-o", str(output_icns_path)]
        subprocess.run(cmd, check=True, capture_output=True)
