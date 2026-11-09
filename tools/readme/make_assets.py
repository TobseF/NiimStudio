#!/usr/bin/env python3
"""
Generates the SVG graphics of the README (docs/media):

    logo.svg                       app icon (same artwork as the launcher icon)
    banner-light.svg / -dark.svg   header for GitHub's light and dark theme
    icons/<name>.svg               section icons (white glyph on the accent colour)
    flags/gb.svg, flags/de.svg     language switch

and the store graphics for F-Droid (fastlane/metadata/android/en-US/images):

    icon.png (512 x 512), featureGraphic.png (1024 x 500)

The PNGs are rendered with a headless Chrome or Edge; without one they are skipped.

Usage (from the project root):  python tools/readme/make_assets.py
"""
from __future__ import annotations

import os
import shutil
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "docs" / "media"

ACCENT = "#F5413D"  # launcher icon background / app accent
SELECT = "#1890FF"  # selection frame in the editor
INK = "#1F2328"
FONT = "-apple-system,BlinkMacSystemFont,'Segoe UI','Noto Sans',Helvetica,Arial,sans-serif"

# Colours of GitHub's light and dark theme.
THEMES = {
    "light": dict(bg1="#FFF3F2", bg2="#F6F8FA", border="#D1D9E0", text="#1F2328", muted="#59636E",
                  chip="#FFFFFF", chip_border="#D1D9E0", shadow="#1F2328", shadow_op="0.10"),
    "dark": dict(bg1="#2B1716", bg2="#151B23", border="#3D444D", text="#F0F6FC", muted="#9198A1",
                 chip="#212830", chip_border="#3D444D", shadow="#000000", shadow_op="0.45"),
}

# Icon artwork in the 108 x 108 launcher grid (see res/drawable/ic_launcher_foreground.xml).
ICON_ART = f"""
  <path fill="#FFFFFF" fill-rule="evenodd" d="M30,38h48a6,6 0 0 1 6,6v20a6,6 0 0 1 -6,6h-48a6,6 0 0 1 -6,-6v-20a6,6 0 0 1 6,-6zM33,54m-3.5,0a3.5,3.5 0 1 0 7,0a3.5,3.5 0 1 0 -7,0z"/>
  <path fill="{INK}" d="M42,46.5h27a2.5,2.5 0 0 1 0,5h-27a2.5,2.5 0 0 1 0,-5zM42,56.5h18a2.5,2.5 0 0 1 0,5h-18a2.5,2.5 0 0 1 0,-5z"/>
  <path fill="none" stroke="{SELECT}" stroke-width="2" d="M21,35h66v38h-66z"/>
  <g fill="{SELECT}" stroke="#FFFFFF" stroke-width="1.5">
    <circle cx="87" cy="54" r="4"/><circle cx="54" cy="73" r="4"/><circle cx="87" cy="73" r="4"/>
  </g>"""


def logo(x: float = 0, y: float = 0, size: float = 108) -> str:
    return (f'<svg x="{x}" y="{y}" width="{size}" height="{size}" viewBox="6 6 96 96">'
            f'<rect x="6" y="6" width="96" height="96" rx="22" fill="{ACCENT}"/>{ICON_ART}</svg>')


def usbc(x: float, y: float, h: float) -> str:
    """USB-C symbol from app/src/main/assets/connectors/usbc.svg, scaled to height h."""
    pins = "".join(f'<rect x="{32.295 + i * 17.182:.3f}" y="31" width="8.591" height="22.04"/>' for i in range(11))
    return (f'<g transform="translate({x} {y}) scale({h / 100})">'
            f'<rect x="4.5" y="4.5" width="236" height="91" rx="45.5" fill="none" stroke="{INK}" stroke-width="9"/>'
            f'<g fill="{INK}"><rect x="28" y="53.04" width="189" height="15.96"/>{pins}</g></g>')


def label_art(lx: float, ly: float, t: dict) -> str:
    """Cable flag label (300 x 120): USB-C + text, the text selected like in the editor."""
    lw, lh = 300, 120
    return f"""
  <g transform="rotate(-5 {lx + lw / 2} {ly + lh / 2})">
    <rect x="{lx + 16}" y="{ly + 22}" width="{lw}" height="{lh}" rx="16" fill="{t["shadow"]}" opacity="{t["shadow_op"]}" filter="url(#blur)"/>
    <rect x="{lx}" y="{ly}" width="{lw}" height="{lh}" rx="16" fill="#FFFFFF" stroke="{t["chip_border"]}"/>
    <circle cx="{lx + 22}" cy="{ly + lh / 2}" r="7" fill="{t["bg2"]}" stroke="{t["chip_border"]}"/>
    {usbc(lx + 46, ly + 30, 46)}
    <text x="{lx + 104}" y="{ly + 102}" text-anchor="middle" font-size="17" font-weight="700" fill="{INK}">USB-C</text>
    <text x="{lx + 232}" y="{ly + 72}" text-anchor="middle" font-size="34" font-weight="700" fill="{INK}">Dock</text>
    <rect x="{lx + 176}" y="{ly + 36}" width="112" height="50" fill="none" stroke="{SELECT}" stroke-width="2"/>
    <g fill="{SELECT}" stroke="#FFFFFF" stroke-width="2">
      <circle cx="{lx + 288}" cy="{ly + 61}" r="6"/><circle cx="{lx + 232}" cy="{ly + 86}" r="6"/><circle cx="{lx + 288}" cy="{ly + 86}" r="6"/>
    </g>
  </g>"""


def defs(t: dict) -> str:
    return f"""<defs>
    <linearGradient id="bg" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0" stop-color="{t["bg1"]}"/><stop offset="1" stop-color="{t["bg2"]}"/>
    </linearGradient>
    <filter id="blur" x="-20%" y="-40%" width="140%" height="180%"><feGaussianBlur stdDeviation="10"/></filter>
  </defs>"""


def feature_graphic() -> str:
    """F-Droid / store header, 1024 x 500."""
    t = THEMES["light"]
    return f"""<svg xmlns="http://www.w3.org/2000/svg" width="1024" height="500" viewBox="0 0 1024 500" font-family="{FONT}">
  {defs(t)}
  <rect width="1024" height="500" fill="url(#bg)"/>
  {logo(80, 110, 128)}
  <text x="236" y="182" font-size="64" font-weight="700" fill="{t["text"]}" letter-spacing="-1">Niim<tspan fill="{ACCENT}">Studio</tspan></text>
  <text x="239" y="226" font-size="24" fill="{t["muted"]}">Label designer for NIIMBOT printers</text>
  {label_art(684, 206, t)}
  <text x="84" y="330" font-size="22" fill="{t["muted"]}">Text · Symbols · Barcodes · QR codes</text>
  <text x="84" y="364" font-size="22" fill="{t["muted"]}">Cable flags · Templates · Batch printing</text>
</svg>
"""


def store_icon() -> str:
    """Square 512 px app icon (stores apply their own mask)."""
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="512" height="512" viewBox="6 6 96 96">'
            f'<rect x="0" y="0" width="108" height="108" fill="{ACCENT}"/>{ICON_ART}</svg>\n')


def find_browser() -> str | None:
    names = ["chrome", "google-chrome", "chromium", "chromium-browser", "msedge", "microsoft-edge"]
    for n in names:
        if p := shutil.which(n):
            return p
    for base in (os.environ.get("ProgramFiles(x86)", ""), os.environ.get("ProgramFiles", "")):
        for rel in (r"Microsoft\Edge\Application\msedge.exe", r"Google\Chrome\Application\chrome.exe"):
            p = Path(base) / rel
            if base and p.exists():
                return str(p)
    return None


def render_png(svg: str, png: Path, w: int, h: int) -> bool:
    browser = find_browser()
    if not browser:
        print(f"  skipped {png.name}: no Chrome/Edge found")
        return False
    with tempfile.TemporaryDirectory() as td:
        page = Path(td) / "page.html"
        page.write_text(f'<!doctype html><html><body style="margin:0;overflow:hidden">{svg}</body></html>',
                        encoding="utf-8")
        png.parent.mkdir(parents=True, exist_ok=True)
        subprocess.run([browser, "--headless=new", "--disable-gpu", "--hide-scrollbars",
                        f"--user-data-dir={Path(td) / 'profile'}", f"--window-size={w},{h}",
                        f"--screenshot={png}", page.as_uri()], capture_output=True, check=True)
    return png.exists()


def banner(theme: str) -> str:
    t = THEMES[theme]
    w, h = 1200, 320
    chips = ["Free &amp; open source", "No account", "Works offline"]
    chip_w = [200, 140, 150]
    chip_svg, cx = [], 258
    for label, cw in zip(chips, chip_w):
        chip_svg.append(
            f'<rect x="{cx}" y="228" width="{cw}" height="36" rx="18" fill="{t["chip"]}" stroke="{t["chip_border"]}"/>'
            f'<text x="{cx + cw / 2}" y="251.5" text-anchor="middle" font-size="16" fill="{t["muted"]}">{label}</text>')
        cx += cw + 12
    return f"""<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}" viewBox="0 0 {w} {h}" font-family="{FONT}">
  <title>NiimStudio – label designer for NIIMBOT printers</title>
  {defs(t)}
  <rect x="0.5" y="0.5" width="{w - 1}" height="{h - 1}" rx="24" fill="url(#bg)" stroke="{t["border"]}"/>
  {logo(64, 80, 160)}
  <text x="256" y="152" font-size="68" font-weight="700" fill="{t["text"]}" letter-spacing="-1">Niim<tspan fill="{ACCENT}">Studio</tspan></text>
  <text x="259" y="199" font-size="24" fill="{t["muted"]}">Design and print labels on your NIIMBOT printer</text>
  {"".join(chip_svg)}
  {label_art(820, 90, t)}
</svg>
"""


# Section icons: 32 x 32, white glyph on an accent circle (readable in both themes).
GLYPHS = {
    "features": '<path fill="#fff" stroke="none" d="M16 6.5l2.3 7.2 7.2 2.3-7.2 2.3-2.3 7.2-2.3-7.2-7.2-2.3 7.2-2.3z"/>',
    "editor": '<path d="M8 10.5h11l5 5.5-5 5.5H8z"/><circle cx="12" cy="16" r="1.3" fill="#fff" stroke="none"/>',
    "download": '<path d="M16 8v11M11 14.5l5 5 5-5M9.5 24h13"/>',
    "install": '<rect x="10.5" y="6.5" width="11" height="19" rx="2.2"/><path d="M14.5 22.5h3"/>',
    "printer": '<path d="M11 12.5V8h10v4.5"/><rect x="7.5" y="12.5" width="17" height="8" rx="2"/><path d="M11 18h10v6H11z" fill="#F5413D"/>',
    "screens": '<rect x="7.5" y="9" width="17" height="14" rx="2"/><circle cx="12.5" cy="13.5" r="1.5" fill="#fff" stroke="none"/><path d="M8.5 21.5l5-5 3 3 2.2-2.2 4.8 4.8"/>',
    "language": '<circle cx="16" cy="16" r="8.5"/><ellipse cx="16" cy="16" rx="3.6" ry="8.5"/><path d="M7.5 16h17"/>',
    "build": '<path d="M12.5 11l-5 5 5 5M19.5 11l5 5-5 5M17.6 9.2l-3.2 13.6"/>',
    "plug": '<path d="M13 7.5v5M19 7.5v5M10.5 12.5h11v3.2a5.5 5.5 0 0 1-11 0zM16 21.2v3.8"/>',
    "heart": '<path fill="#fff" stroke="none" d="M16 24s-7.5-4.6-7.5-9.6A4 4 0 0 1 16 12a4 4 0 0 1 7.5 2.4C23.5 19.4 16 24 16 24z"/>',
    "license": '<path d="M16 8v16M11.5 24h9M9 11.5h14M9 11.5l-2.8 6h5.6zM23 11.5l-2.8 6h5.6z"/>',
    "info": '<circle cx="16" cy="16" r="8.5"/><path d="M16 15v6"/><circle cx="16" cy="11.4" r="1.3" fill="#fff" stroke="none"/>',
    "film": '<rect x="7.5" y="9" width="17" height="14" rx="2"/><path d="M14 13l5 3-5 3z" fill="#fff"/>',
}


def icon(glyph: str) -> str:
    return ('<svg xmlns="http://www.w3.org/2000/svg" width="32" height="32" viewBox="0 0 32 32">'
            f'<circle cx="16" cy="16" r="16" fill="{ACCENT}"/>'
            '<g fill="none" stroke="#fff" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">'
            f'{glyph}</g></svg>\n')


FLAGS = {
    # United Kingdom (60 x 30 union flag, standard construction)
    "gb": """<svg xmlns="http://www.w3.org/2000/svg" width="30" height="20" viewBox="0 0 60 40">
  <clipPath id="c"><rect width="60" height="40" rx="4"/></clipPath>
  <g clip-path="url(#c)">
    <rect width="60" height="40" fill="#012169"/>
    <path d="M0 0l60 40M60 0L0 40" stroke="#fff" stroke-width="8"/>
    <path d="M0 0l60 40M60 0L0 40" stroke="#C8102E" stroke-width="3"/>
    <path d="M30 0v40M0 20h60" stroke="#fff" stroke-width="12"/>
    <path d="M30 0v40M0 20h60" stroke="#C8102E" stroke-width="7"/>
  </g>
</svg>
""",
    "de": """<svg xmlns="http://www.w3.org/2000/svg" width="30" height="20" viewBox="0 0 60 40">
  <clipPath id="c"><rect width="60" height="40" rx="4"/></clipPath>
  <g clip-path="url(#c)">
    <rect width="60" height="13.34" fill="#000"/>
    <rect y="13.33" width="60" height="13.34" fill="#DD0000"/>
    <rect y="26.66" width="60" height="13.34" fill="#FFCE00"/>
  </g>
</svg>
""",
}


def main():
    (OUT / "icons").mkdir(parents=True, exist_ok=True)
    (OUT / "flags").mkdir(parents=True, exist_ok=True)
    (OUT / "logo.svg").write_text(
        f'<svg xmlns="http://www.w3.org/2000/svg" width="160" height="160" viewBox="0 0 160 160">{logo(0, 0, 160)}</svg>\n',
        encoding="utf-8")
    for theme in THEMES:
        (OUT / f"banner-{theme}.svg").write_text(banner(theme), encoding="utf-8")
    for name, glyph in GLYPHS.items():
        (OUT / "icons" / f"{name}.svg").write_text(icon(glyph), encoding="utf-8")
    for name, svg in FLAGS.items():
        (OUT / "flags" / f"{name}.svg").write_text(svg, encoding="utf-8")
    print(f"written to {OUT.relative_to(ROOT)}")

    store = ROOT / "fastlane" / "metadata" / "android" / "en-US" / "images"
    if render_png(store_icon(), store / "icon.png", 512, 512):
        print(f"written {(store / 'icon.png').relative_to(ROOT)}")
    if render_png(feature_graphic(), store / "featureGraphic.png", 1024, 500):
        print(f"written {(store / 'featureGraphic.png').relative_to(ROOT)}")


if __name__ == "__main__":
    main()
