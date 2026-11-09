#!/usr/bin/env python3
"""
Records the README teaser GIF and the store screenshots of NiimStudio.

The script drives the app on an emulator or device through adb: it installs the debug APK,
starts from a clean state, plays a short storyboard that shows the layout features
(connectors, text, live resizing, alignment, snapping, cable flag, print preview) while
`screenrecord` captures the screen, and turns the video into an optimised GIF inside a
phone frame. Run it again after UI changes to refresh the media for a new version.

Controls are found through language-independent Compose test tags (exposed as resource ids),
so the same storyboard works for every app language.

Requirements: Android SDK platform-tools (adb), ffmpeg, Python 3.9+ with Pillow.
A running emulator (recommended: Pixel-sized, 1080x2400, API 34+) or a connected device.

Usage (from the project root):
    python tools/teaser/record.py                 # English + German, builds nothing
    python tools/teaser/record.py --build         # assembleDebug first
    python tools/teaser/record.py --lang de       # only German
    python tools/teaser/record.py --serial emulator-5554 --width 320
    python tools/teaser/record.py --gif-only      # re-render GIFs from build/teaser/*.mp4

Outputs:
    docs/media/teaser-<lang>.gif
    fastlane/metadata/android/<locale>/images/phoneScreenshots/<n>.png
"""
from __future__ import annotations

import argparse
import json
import re
import shutil
import subprocess
import sys
import tempfile
import time
import xml.etree.ElementTree as ET
from contextlib import contextmanager
from dataclasses import dataclass
from pathlib import Path

try:
    from PIL import Image, ImageChops, ImageDraw
except ImportError:  # pragma: no cover
    sys.exit("Pillow is required: python -m pip install pillow")

ROOT = Path(__file__).resolve().parents[2]
PKG = "io.github.tobsef.niimstudio"
ACTIVITY = f"{PKG}/.MainActivity"
LOCALES = {"en": "en-US", "de": "de-DE"}
# Text typed next to the connector.
LABEL_TEXT = {"en": "Dock", "de": "Dock"}
# Size of the default label profile (12x40 White), used to map mm to screen pixels.
LABEL_MM = (40.0, 12.0)


# --------------------------------------------------------------------------- adb helpers

@dataclass
class Node:
    rid: str
    text: str
    desc: str
    x1: int
    y1: int
    x2: int
    y2: int

    @property
    def center(self) -> tuple[int, int]:
        return (self.x1 + self.x2) // 2, (self.y1 + self.y2) // 2

    @property
    def width(self) -> int:
        return self.x2 - self.x1

    @property
    def height(self) -> int:
        return self.y2 - self.y1


class Device:
    def __init__(self, serial: str | None):
        self.base = ["adb"] + (["-s", serial] if serial else [])
        # Wall-clock periods to cut from the video (keyboard toolbar visible), see typing().
        self.cuts: list[tuple[float, float]] = []
        self.holds: list[float] = []
        # Time of the first tap after reset_timeline(): its touch dot syncs video and clock.
        self.sync: float | None = None

    def reset_timeline(self):
        self.cuts = []
        self.holds = []
        self.sync = None

    def run(self, *args: str, check: bool = True, capture: bool = True) -> str:
        r = subprocess.run(self.base + list(args), capture_output=capture, text=True,
                           encoding="utf-8", errors="replace")
        if check and r.returncode != 0:
            raise RuntimeError(f"adb {' '.join(args)} failed: {r.stderr.strip()}")
        return r.stdout if capture else ""

    def shell(self, cmd: str, check: bool = True) -> str:
        return self.run("shell", cmd, check=check)

    def exec_out(self, cmd: str) -> bytes:
        return subprocess.run(self.base + ["exec-out", cmd], capture_output=True, check=True).stdout

    # -- input
    def tap_xy(self, x: int, y: int, pause: float = 0.7):
        if self.sync is None:
            self.sync = time.time()
        self.shell(f"input tap {x} {y}")
        time.sleep(pause)

    def swipe(self, x1, y1, x2, y2, ms: int = 600, pause: float = 0.6):
        self.shell(f"input swipe {x1} {y1} {x2} {y2} {ms}")
        time.sleep(pause)

    def key(self, *codes: str, pause: float = 0.4):
        self.shell("input keyevent " + " ".join(codes))
        time.sleep(pause)

    def type_text(self, text: str, pause: float = 0.6):
        self.shell("input text " + text.replace(" ", "%s"))
        time.sleep(pause)

    def replace_text(self, rid: str, text: str, tries: int = 3):
        """Clears the focused field and types `text`; checks the result in the UI tree."""
        for _ in range(tries):
            self.key(*(["KEYCODE_MOVE_END"] + ["KEYCODE_DEL"] * 10 + ["KEYCODE_FORWARD_DEL"] * 4), pause=0.3)
            self.type_text(text, pause=0.3)
            got = next((n.text for n in self.nodes() if n.rid == rid), "")
            if got.replace(",", ".") == text:
                return
        print(f"    warning: '{rid}' shows '{got}' instead of '{text}'")

    # -- UI tree
    def nodes(self) -> list[Node]:
        for _ in range(3):
            self.shell("uiautomator dump /sdcard/niim_ui.xml", check=False)
            raw = self.exec_out("cat /sdcard/niim_ui.xml")
            try:
                root = ET.fromstring(raw)
                break
            except ET.ParseError:
                time.sleep(0.5)
        else:
            return []
        out = []
        for el in root.iter("node"):
            m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", el.get("bounds", ""))
            if not m:
                continue
            out.append(Node(el.get("resource-id", ""), el.get("text", ""), el.get("content-desc", ""),
                            *map(int, m.groups())))
        return out

    def find(self, rid: str, timeout: float = 15) -> Node:
        end = time.time() + timeout
        while time.time() < end:
            for n in self.nodes():
                if n.rid == rid and n.height > 10:
                    return n
            time.sleep(0.4)
        raise RuntimeError(f"UI element '{rid}' not found")

    def tap(self, rid: str, pause: float = 0.8, timeout: float = 15):
        x, y = self.find(rid, timeout).center
        self.tap_xy(x, y, pause)

    def screenshot(self, path: Path):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(self.exec_out("screencap -p"))

    def density(self) -> int:
        out = self.shell("wm density")
        m = re.search(r"Override density: (\d+)", out) or re.search(r"(\d+)", out)
        return int(m.group(1))

    def keyboard_shown(self) -> bool:
        return "true" in self.shell("dumpsys input_method | grep mInputShown=", check=False)

    def hide_keyboard(self, pause: float = 0.6, wait: float = 0.6):
        """BACK only when the keyboard is up, otherwise it would leave the editor."""
        end = time.time() + wait
        while True:
            if self.keyboard_shown():
                self.key("KEYCODE_BACK", pause=pause)
                return
            if time.time() > end:
                return
            time.sleep(0.05)

    @contextmanager
    def typing(self, field: Node):
        """Focuses a text field for `input text` and key presses inside the block.

        Afterwards the keyboard is closed (the field keeps its value). With a hardware keyboard
        (emulator) Gboard pops up a floating toolbar on every key press, so the whole period is
        cut from the video: the GIF jumps straight to the typed result.
        """
        start = time.time()
        self.tap_xy(*field.center, pause=0.8)
        yield
        self.hide_keyboard(pause=0.3, wait=1.5)
        self.cuts.append((start, time.time() + 0.4))

    def screen_size(self) -> tuple[int, int]:
        m = re.search(r"(\d+)x(\d+)", self.shell("wm size"))
        return int(m.group(1)), int(m.group(2))


# --------------------------------------------------------------------------- app state

def prepare(dev: Device, apk: Path, lang: str):
    print(f"[{lang}] installing {apk.name}")
    dev.run("install", "-r", str(apk))
    dev.shell(f"am force-stop {PKG}", check=False)
    dev.shell(f"pm clear {PKG}")
    for perm in ("BLUETOOTH_SCAN", "BLUETOOTH_CONNECT"):
        dev.shell(f"pm grant {PKG} android.permission.{perm}", check=False)
    # Bluetooth on, so the app does not ask to enable it.
    dev.shell("svc bluetooth enable", check=False)
    dev.shell("cmd bluetooth_manager enable", check=False)
    # Fixed app language and no auto-connect: written into the app's settings file.
    settings = json.dumps({"language": lang, "autoConnect": False, "keepAlive": False})
    with tempfile.NamedTemporaryFile("w", suffix=".json", delete=False, encoding="utf-8") as f:
        f.write(settings)
        tmp = f.name
    dev.run("push", tmp, "/data/local/tmp/niim_settings.json")
    Path(tmp).unlink(missing_ok=True)
    dev.shell("chmod 644 /data/local/tmp/niim_settings.json")
    dev.shell(f"run-as {PKG} mkdir -p files")
    dev.shell(f"run-as {PKG} cp /data/local/tmp/niim_settings.json files/settings.json")


def demo_mode(dev: Device, on: bool):
    """Clean status bar (fixed clock, full battery, no notifications) and visible touches."""
    def demo(cmd: str):
        dev.shell(f"am broadcast -a com.android.systemui.demo -e command {cmd}", check=False)

    if on:
        dev.shell("settings put global sysui_demo_allowed 1", check=False)
        demo("enter")
        demo("clock -e hhmm 1200")
        demo("battery -e level 100 -e plugged false")
        demo("network -e wifi show -e level 4 -e mobile hide")
        demo("notifications -e visible false")
        dev.shell("settings put system show_touches 1", check=False)
    else:
        demo("exit")
        dev.shell("settings put system show_touches 0", check=False)


# --------------------------------------------------------------------------- storyboard

class Storyboard:
    def __init__(self, dev: Device, lang: str, shots_dir: Path):
        self.dev = dev
        self.lang = lang
        self.shots_dir = shots_dir
        self.shot_no = 0

    def shot(self):
        self.shot_no += 1
        self.dev.holds.append(time.time())  # key moment: stays longer in the GIF
        self.dev.screenshot(self.shots_dir / f"{self.shot_no}.png")

    def canvas_empty_spot(self) -> tuple[int, int]:
        c = self.dev.find("canvas")
        return (c.x1 + c.x2) // 2, c.y1 + c.height // 10

    def panel_reveal(self, rid: str, tries: int = 4, down: bool = True) -> Node:
        """Scrolls the bottom panel until the element is visible."""
        toggle = self.dev.find("panel_toggle")  # bottom of the tab row
        save = self.dev.find("save")  # top of the bottom bar
        for _ in range(tries):
            try:
                n = self.dev.find(rid, timeout=2)
                if n.y1 >= toggle.y2 and n.y2 <= save.y1:
                    return n
            except RuntimeError:
                pass
            w, _ = self.dev.screen_size()
            y_from = save.y1 - 150
            y_to = y_from - 350
            if not down:
                y_from, y_to = y_to, y_from
            self.dev.swipe(w // 2, y_from, w // 2, y_to, 300, pause=0.6)
        return self.dev.find(rid)

    def prepare_canvas(self):
        try:
            self.dev.find("add_text", timeout=60)  # first start can be slow
        except RuntimeError:  # start once more
            self.dev.shell(f"am force-stop {PKG}")
            self.dev.shell(f"am start -n {ACTIVITY}")
            self.dev.find("add_text", timeout=60)
        time.sleep(1.0)
        self.dev.tap("clear_all")
        self.dev.tap("confirm_ok", pause=1.0)

    def mm_to_screen(self, x_mm: float, y_mm: float) -> tuple[int, int]:
        """Same mapping as LabelCanvas (single-sided layout, no zoom)."""
        c = self.dev.find("canvas")
        pad = 28 * self.dev.density() / 160
        w_mm, h_mm = LABEL_MM
        s = min((c.width - 2 * pad) / w_mm, (c.height - 2 * pad) / h_mm)
        ox = c.x1 + (c.width - w_mm * s) / 2
        oy = c.y1 + (c.height - h_mm * s) / 2
        return round(ox + x_mm * s), round(oy + y_mm * s)

    def play(self):
        d = self.dev
        w_mm, h_mm = LABEL_MM
        time.sleep(1.2)
        # 1. Connector: the first one goes to the left.
        d.tap("add_connector", pause=1.0)
        self.shot()  # connector picker
        d.tap("connector_usbc", pause=1.2)
        d.tap_xy(*self.canvas_empty_spot(), pause=0.9)
        # 2. Text (starts full width in the middle).
        d.tap("add_text", pause=1.0)
        with d.typing(d.find("content_text")):
            d.replace_text("content_text", LABEL_TEXT[self.lang])
        time.sleep(0.6)
        # 3. Live resizing with the inline value editor and rounding.
        d.tap("bar_align", pause=1.0)
        n = self.panel_reveal("stepper_width")
        d.tap_xy(*n.center, pause=1.0)
        with d.typing(self.panel_reveal("stepper_input")):  # editor unfolds below the row
            d.replace_text("stepper_input", "17.4")
        time.sleep(1.2)
        self.shot()  # inline editor with rounding chips
        n = self.panel_reveal("chip_17")
        d.tap_xy(*n.center, pause=1.0)
        n = self.panel_reveal("stepper_width", down=False)
        d.tap_xy(*n.center, pause=0.8)  # fold the editor
        # 4. Align to the right edge, next to the connector.
        n = self.panel_reveal("align_RIGHT", down=False)
        d.tap_xy(*n.center, pause=1.2)
        self.shot()  # editor with selection and floating bar
        # 5. Drag with snap guides: down and back, it snaps to the centre line.
        x_mm = w_mm - 17 / 2 - 1
        sx, sy = self.mm_to_screen(x_mm, h_mm / 2)
        tx, ty = self.mm_to_screen(x_mm - 3, h_mm / 2 + 2.5)
        d.swipe(sx, sy, tx, ty, 800, pause=0.5)
        d.swipe(tx, ty, sx, sy + 3, 800, pause=0.8)
        d.tap_xy(*self.canvas_empty_spot(), pause=0.8)
        # 6. Cable flag: printed on both sides.
        d.tap("tab_LAYOUT", pause=0.8)
        d.tap("segment_FLAG", pause=2.2)
        self.shot()  # flag with whole-label preview
        d.tap("segment_SINGLE", pause=1.2)
        # 7. Print preview.
        d.tap("print", pause=2.5)
        self.shot()  # 1-bit print preview
        d.tap("print_close", pause=1.0)


# --------------------------------------------------------------------------- video -> GIF

def make_gif(mp4: Path, gif: Path, width: int, fps: int, hold: float = 1.0, hold_key: float = 2.4):
    """Video -> frames -> phone frame with transparent rounded corners -> palette GIF.

    Frames that hardly differ from the previous one (encoder noise, waiting for the UI dump)
    are merged, and still periods are shortened to `hold` seconds. That keeps the GIF small
    and the pace brisk.
    """
    ffmpeg = shutil.which("ffmpeg") or sys.exit("ffmpeg not found in PATH")
    with tempfile.TemporaryDirectory() as td:
        tdp = Path(td)
        raw = tdp / "raw"
        framed = tdp / "framed"
        raw.mkdir()
        framed.mkdir()
        subprocess.run([ffmpeg, "-loglevel", "error", "-i", str(mp4), "-vf",
                        f"fps={fps},scale={width}:-2:flags=lanczos", str(raw / "%04d.png")], check=True)
        frames = sorted(raw.glob("*.png"))
        if not frames:
            sys.exit("no frames extracted")

        def changed(a: Image.Image, b: Image.Image) -> int:
            """Number of pixels that clearly differ."""
            return ImageChops.difference(a, b).point(lambda v: 255 if v > 40 else 0).histogram()[255]

        # 0. Storyboard timeline: periods to cut and key moments to hold longer. The first
        #    visible change in the video (touch dot of the first tap) is time 0 of that clock.
        meta = mp4.with_suffix(".json")
        info = json.loads(meta.read_text(encoding="utf-8")) if meta.exists() else {}
        cuts, holds = info.get("cuts", []), info.get("holds", [])
        first = Image.open(frames[0]).convert("L")
        sync = next((i for i, f in enumerate(frames) if changed(Image.open(f).convert("L"), first) > 15), 0)
        timed = [(f, (i - sync) / fps) for i, f in enumerate(frames)
                 if not any(a <= (i - sync) / fps <= b for a, b in cuts)]

        # 1. Merge near-identical frames: [file, gray image, video frames, first t, last t].
        runs: list[list] = []
        for f, t in timed:
            gray = Image.open(f).convert("L")
            if runs and changed(gray, runs[-1][1]) < 6:  # a few pixels of encoder noise
                runs[-1][2] += 1
                runs[-1][4] = t
                continue
            # A blinking text cursor toggles a handful of pixels: A B A B ... -> A.
            if (len(runs) >= 2 and runs[-1][2] <= 8 and changed(runs[-1][1], runs[-2][1]) < 40
                    and changed(gray, runs[-2][1]) < 6):
                runs[-2][2] += runs.pop()[2] + 1
                runs[-1][4] = t
                continue
            runs.append([f, gray, 1, t, t])

        def duration(run) -> float:
            _, _, n, t0, t1 = run
            key_moment = any(t0 - 0.2 <= m <= t1 + 0.2 for m in holds)
            return min(n / fps, hold_key if key_moment else hold)

        # 2. Phone frame around each kept frame.
        w, h = Image.open(frames[0]).size
        bezel = max(6, width // 40)
        radius = width // 9
        fw, fh = w + 2 * bezel, h + 2 * bezel
        frame_mask = Image.new("L", (fw, fh), 0)
        ImageDraw.Draw(frame_mask).rounded_rectangle((0, 0, fw - 1, fh - 1), radius=radius + bezel, fill=255)
        screen_mask = Image.new("L", (w, h), 0)
        ImageDraw.Draw(screen_mask).rounded_rectangle((0, 0, w - 1, h - 1), radius=radius, fill=255)
        body = Image.new("RGBA", (fw, fh), (28, 28, 30, 255))
        lines = []
        for i, run in enumerate(runs):
            f = run[0]
            img = Image.new("RGBA", (fw, fh), (0, 0, 0, 0))
            img.paste(body, (0, 0), frame_mask)
            img.paste(Image.open(f).convert("RGBA"), (bezel, bezel), screen_mask)
            out = framed / f"{i:04d}.png"
            img.save(out)
            lines += [f"file '{out.as_posix()}'", f"duration {duration(run):.3f}"]
        lines.append(lines[-2])  # concat demuxer: the last file needs to be listed twice
        listing = tdp / "frames.txt"
        listing.write_text("\n".join(lines) + "\n", encoding="utf-8")

        # 3. One palette for the whole clip, no dithering (flat UI colours stay clean).
        gif.parent.mkdir(parents=True, exist_ok=True)
        subprocess.run([
            ffmpeg, "-loglevel", "error", "-y", "-f", "concat", "-safe", "0", "-i", str(listing),
            "-filter_complex",
            "split[a][b];[a]palettegen=reserve_transparent=1:max_colors=128[p];"
            "[b][p]paletteuse=dither=none:diff_mode=rectangle:alpha_threshold=128",
            "-fps_mode", "vfr", "-loop", "0", str(gif)], check=True)
        total = sum(duration(r) for r in runs)
    print(f"    {gif.relative_to(ROOT)}  ({gif.stat().st_size / 1e6:.1f} MB, {len(runs)} frames, {total:.0f} s)")

def downscale_screenshots(folder: Path, max_width: int = 720):
    for p in folder.glob("*.png"):
        im = Image.open(p)
        if im.width > max_width:
            im = im.resize((max_width, round(im.height * max_width / im.width)), Image.LANCZOS)
        im.save(p, optimize=True)


# --------------------------------------------------------------------------- main

def video_path(lang: str) -> Path:
    """Raw recording, kept so that the GIF can be re-rendered with --gif-only."""
    return ROOT / "build" / "teaser" / f"teaser-{lang}.mp4"


def gif_path(lang: str) -> Path:
    return ROOT / "docs" / "media" / f"teaser-{lang}.gif"


def record(dev: Device, apk: Path, lang: str, width: int, fps: int, make_video: bool):
    shots = ROOT / "fastlane" / "metadata" / "android" / LOCALES[lang] / "images" / "phoneScreenshots"
    if shots.exists():
        shutil.rmtree(shots)
    prepare(dev, apk, lang)
    demo_mode(dev, True)
    try:
        dev.shell(f"am start -n {ACTIVITY}")
        story = Storyboard(dev, lang, shots)
        story.prepare_canvas()
        remote = "/sdcard/niim_teaser.mp4"
        rec = None
        if make_video:
            dev.shell(f"rm -f {remote}", check=False)
            rec = subprocess.Popen(dev.base + ["shell", f"screenrecord --bit-rate 8000000 {remote}"])
            time.sleep(1.0)
        print(f"[{lang}] playing storyboard")
        dev.reset_timeline()
        story.play()
        if rec:
            time.sleep(0.8)
            dev.shell("pkill -INT screenrecord", check=False)
            rec.wait(timeout=20)
            time.sleep(1.5)  # let the file be finalised
            mp4 = video_path(lang)
            mp4.parent.mkdir(parents=True, exist_ok=True)
            dev.run("pull", remote, str(mp4))
            # Periods to cut, in seconds relative to the first tap.
            cuts = [[round(a - dev.sync, 3), round(b - dev.sync, 3)] for a, b in dev.cuts]
            holds = [round(t - dev.sync, 3) for t in dev.holds]
            mp4.with_suffix(".json").write_text(json.dumps({"cuts": cuts, "holds": holds}, indent=1),
                                                encoding="utf-8")
            make_gif(mp4, gif_path(lang), width, fps)
        downscale_screenshots(shots)
        print(f"    {story.shot_no} screenshots in {shots.relative_to(ROOT)}")
    finally:
        demo_mode(dev, False)


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--serial", help="adb serial of the emulator/device")
    ap.add_argument("--lang", default="all", choices=["all", *LOCALES])
    ap.add_argument("--build", action="store_true", help="run gradlew assembleDebug first")
    ap.add_argument("--apk", type=Path, help="APK to install (default: newest debug APK)")
    ap.add_argument("--width", type=int, default=300, help="GIF screen width in px")
    ap.add_argument("--fps", type=int, default=12)
    ap.add_argument("--no-video", action="store_true", help="only take screenshots")
    ap.add_argument("--gif-only", action="store_true",
                    help="re-render the GIFs from the last recordings in build/teaser (no device needed)")
    args = ap.parse_args()
    langs = list(LOCALES) if args.lang == "all" else [args.lang]

    if args.gif_only:
        for lang in langs:
            make_gif(video_path(lang), gif_path(lang), args.width, args.fps)
        return

    if args.build:
        gradlew = ROOT / ("gradlew.bat" if sys.platform == "win32" else "gradlew")
        subprocess.run([str(gradlew), "assembleDebug"], cwd=ROOT, check=True)
    apk = args.apk or max((ROOT / "app/build/outputs/apk/debug").glob("*.apk"), key=lambda p: p.stat().st_mtime,
                          default=None)
    if not apk:
        sys.exit("No debug APK found - run with --build")
    dev = Device(args.serial)
    for lang in langs:
        record(dev, apk, lang, args.width, args.fps, not args.no_video)


if __name__ == "__main__":
    main()
