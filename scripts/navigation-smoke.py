#!/usr/bin/env python3
"""Exercise the release APK's four tabs with ADB; save UI trees and screenshots.

Usage: python3 scripts/navigation-smoke.py --output navigation-smoke
Run with a booted emulator and the normal release APK already installed.
"""
import argparse
import re
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path

PACKAGE = "io.github.only7rb.zsu"
TABS = ["Home", "Manage", "BL Root", "Settings"]


def adb(*args: str) -> str:
    return subprocess.check_output(["adb", *args], text=True)


def bounds(node: ET.Element) -> tuple[int, int, int, int]:
    values = re.findall(r"\d+", node.get("bounds", ""))
    return tuple(map(int, values)) if len(values) == 4 else (0, 0, 0, 0)


def ui_tree(output: Path, name: str) -> ET.Element:
    adb("shell", "uiautomator", "dump", "/sdcard/zsu-ui.xml")
    text = adb("shell", "cat", "/sdcard/zsu-ui.xml")
    (output / f"{name}.xml").write_text(text)
    return ET.fromstring(text)


def tab_node(tree: ET.Element, label: str) -> ET.Element:
    matches = [
        n for n in tree.iter("node")
        if n.get("package") == PACKAGE
        and (n.get("text") == label or n.get("content-desc") == label)
    ]
    matches = [n for n in matches if bounds(n)[3] > 0]
    assert matches, f"Tab label not exposed/visible: {label}"
    return max(matches, key=lambda n: bounds(n)[1])


def app_text_node(tree: ET.Element, text: str) -> ET.Element:
    matches = [
        n for n in tree.iter("node")
        if n.get("package") == PACKAGE and n.get("text") == text and bounds(n)[3] > 0
    ]
    assert matches, f"App text not exposed/visible: {text}"
    return max(matches, key=lambda n: bounds(n)[1])


def selected_tab_ancestor(tree: ET.Element, label: str) -> bool:
    def walk(node: ET.Element, selected: bool = False) -> bool:
        selected = selected or node.get("selected") == "true"
        if node.get("package") == PACKAGE and node.get("text") == label:
            return selected
        return any(walk(child, selected) for child in node)

    return walk(tree)


def check_dock(tree: ET.Element) -> None:
    nodes = [tab_node(tree, label) for label in TABS]
    tops = [bounds(n)[1] for n in nodes]
    bottoms = [bounds(n)[3] for n in nodes]
    assert max(tops) - min(tops) <= 4, f"Tab labels not aligned: {tops}"
    assert max(bottoms) - min(bottoms) <= 4, f"Tab label heights differ: {bottoms}"
    system_nav = [
        bounds(n)[1] for n in tree.iter("node")
        if n.get("resource-id", "").endswith((":id/navigation_bar_frame", ":id/navigation_bar_view"))
        and bounds(n)[3] > bounds(n)[1]
    ]
    if system_nav:
        assert max(bottoms) < min(system_nav), "Tab labels overlap the system navigation area"


def screenshot(output: Path, name: str) -> None:
    with (output / f"{name}.png").open("wb") as image:
        subprocess.run(["adb", "exec-out", "screencap", "-p"], stdout=image, check=True)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    adb("shell", "settings", "put", "system", "system_locales", "en-US")
    adb("shell", "am", "start", "-n", f"{PACKAGE}/com.rifsxd.ksunext.ui.MainActivity")
    time.sleep(5)
    overlays = adb("shell", "cmd", "overlay", "list")
    modes = {
        "gesture": "com.android.internal.systemui.navbar.gestural",
        "three-button": "com.android.internal.systemui.navbar.threebutton",
    }
    for mode, overlay in modes.items():
        assert overlay in overlays, f"Missing emulator navigation overlay: {overlay}"
        adb("shell", "cmd", "overlay", "enable-exclusive", "--category", "--user", "0", overlay)
        time.sleep(3)
        # Switching SystemUI's navigation overlay can recreate the foreground
        # activity or return to Launcher. Reopen the app before testing its tabs.
        adb("shell", "am", "start", "-W", "-n", f"{PACKAGE}/com.rifsxd.ksunext.ui.MainActivity")
        time.sleep(3)
        for label in TABS + ["Home"]:
            name = f"{mode}-{label.lower().replace(' ', '-')}"
            tree = ui_tree(args.output, f"{name}-before")
            node = tab_node(tree, label)
            x1, y1, x2, y2 = bounds(node)
            adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
            time.sleep(1)
            tree = ui_tree(args.output, name)
            screenshot(args.output, name)
            check_dock(tree)
            if label == "Home":
                assert "CONTROL CENTER" not in ET.tostring(tree, encoding="unicode")
            assert adb("shell", "pidof", PACKAGE).strip(), "ZSU process stopped during navigation"
            print(f"PASS: {mode} → {label}; all four tab labels visible and aligned", flush=True)

        settings = ui_tree(args.output, f"{mode}-settings-for-customization")
        settings_tab = tab_node(settings, "Settings")
        x1, y1, x2, y2 = bounds(settings_tab)
        adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
        time.sleep(1)
        settings = ui_tree(args.output, f"{mode}-settings-root")
        customization = app_text_node(settings, "Customization")
        x1, y1, x2, y2 = bounds(customization)
        adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
        time.sleep(1)
        customization_tree = ui_tree(args.output, f"{mode}-customization")
        check_dock(customization_tree)
        assert selected_tab_ancestor(customization_tree, "Settings"), \
            f"Settings tab is not selected on Customization ({mode})"
        assert not selected_tab_ancestor(customization_tree, "Home"), \
            f"Home tab incorrectly selected on Customization ({mode})"
        screenshot(args.output, f"{mode}-customization")
        print(f"PASS: {mode} → Settings → Customization; Settings remains selected", flush=True)
    (args.output / "logcat.txt").write_text(adb("logcat", "-d", "-t", "2000"))
    print("Navigation smoke checks passed in gesture and three-button modes", flush=True)


if __name__ == "__main__":
    main()
