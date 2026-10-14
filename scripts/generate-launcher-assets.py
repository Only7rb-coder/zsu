#!/usr/bin/env python3
"""Resize the source ZSU emblem into Android adaptive and legacy launcher assets.

Requires Pillow. Run from any directory; pass a new transparent PNG to regenerate:
    python3 scripts/generate-launcher-assets.py assets/zsu-emblem.png
"""
from pathlib import Path
import argparse
from PIL import Image, ImageOps

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "manager/app/src/main/res"
DENSITIES = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}


def resized(image: Image.Image, size: int) -> Image.Image:
    return ImageOps.contain(image, (size, size), Image.Resampling.LANCZOS)


def centered(image: Image.Image, size: int, content_size: int) -> Image.Image:
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    content = resized(image, content_size)
    canvas.alpha_composite(content, ((size - content.width) // 2, (size - content.height) // 2))
    return canvas


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path)
    args = parser.parse_args()
    emblem = Image.open(args.source).convert("RGBA")
    # Android masks the adaptive foreground; preserve generous safe-zone margins.
    foreground = centered(emblem, 432, 384)
    monochrome = Image.new("RGBA", foreground.size, (255, 255, 255, 255))
    monochrome.putalpha(foreground.getchannel("A"))
    foreground.save(RES / "drawable/ic_zsu.png", optimize=True)
    monochrome.save(RES / "drawable/ic_zsu_monochrome.png", optimize=True)
    for density, scale in DENSITIES.items():
        target = RES / f"mipmap-{density}"
        size = round(108 * scale)
        resized(foreground, size).save(target / "ic_launcher_foreground.png", optimize=True)
        resized(monochrome, size).save(target / "ic_launcher_monochrome.png", optimize=True)
        legacy_size = round(48 * scale)
        legacy = Image.new("RGBA", (legacy_size, legacy_size), "#09090B")
        legacy.alpha_composite(centered(emblem, legacy_size, legacy_size))
        legacy.convert("RGB").save(target / "ic_launcher.png", optimize=True)
    print(f"Generated adaptive, monochrome, and legacy icons in {RES}")


if __name__ == "__main__":
    main()
