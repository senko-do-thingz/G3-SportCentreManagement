"""UI-00 visual regression and rendered token check (before = main, after = the UI-00 branch).

Opens the public Home of two running builds in Chromium at 390, 768 and 1440 px, in light and dark mode
(plus the guest auth dialog, Log in and Sign up at 390 and 1440 px),
takes full-page screenshots, compares them pixel by pixel and reads the values the browser actually renders
(resolved custom properties, hero, footer, buttons and the call-to-action gradient).

Content is made deterministic so only styling can differ: the public API is answered with fixed JSON,
remote photos and videos are blocked (Home shows its bundled fallback images) and reduced motion is on.

Usage (PowerShell, from frontend/):
  pip install playwright pillow; python -m playwright install chromium
  npm run build -- --outDir ../dist-after          (on the UI-00 branch; repeat on main into ../dist-before)
  python -m http.server 4301 -d ../dist-before     (and 4302 for ../dist-after, in two other windows)
  python scripts/ui00-visual-check.py --before http://localhost:4301 --after http://localhost:4302 --out docs/screenshots/ui-00
Exit code 0 when every screenshot pair is identical and every rendered value matches the contract.
"""

import argparse
import json
import re
import sys
from pathlib import Path

from PIL import Image, ImageChops
from playwright.sync_api import sync_playwright

WIDTHS = [390, 768, 1440]
THEMES = ["light", "dark"]
API = "**/api/v1/**"

FIXTURES = {
    "/sports": [{"id": 41, "code": "FOOTBALL", "name": "Football"}, {"id": 52, "code": "BADMINTON", "name": "Badminton"}],
    "/packages": [{"id": 81, "name": "Football Independent", "sportId": 41, "sportName": "Football", "trainingFormat": "SELF_TRAINING",
                   "durationDays": 30, "sessionCount": 8, "priceAmount": 500000, "isActive": True}],
    "/membership-cards/tiers": [{"id": 1, "name": "Gold", "discountPercentage": 5, "isActive": True}],
    "/coaches/public": [{"id": 22, "fullName": "Le Hoang Nam", "headline": None, "specialties": None, "yearsExperience": 4, "sportIds": [41]}],
    "/facilities/public": [{"id": 61, "name": "Badminton Court 1", "facilityType": "COURT", "sportId": 52, "capacity": 8},
                           {"id": 63, "name": "Football Field", "facilityType": "FIELD", "sportId": 41, "capacity": 22}],
}

# Values the browser must render after UI-00 (Royal and Gold contract, frontend/docs/design-tokens.md).
EXPECTED = {
    "light": {
        "root --color-hero": "#0b1736", "root --color-hero-deep": "#060d20", "root --color-footer": "#070f24",
        "root --color-on-hero": "#ffffff", "root --color-on-hero-muted": "#e3e9f8", "root --color-hero-highlight": "#ffc83d",
        "home --h-primary": "#1f4fd8", "home --h-primary-hover": "#1a42b5", "home --h-primary-text": "#1f4fd8",
        "home --h-accent": "#f5b301", "home --h-on-accent": "#1a1405", "home --h-icon-bg": "#e9eefc",
        "home --h-hero": "#0b1736", "home --h-hero-accent": "#ffc83d", "home --h-hero-muted": "#e3e9f8",
        "home --h-footer": "#070f24", "home --h-deep": "#060d20", "home --h-cta-from": "#0b1736", "home --h-cta-to": "#1f4fd8",
        "hero background-color": "rgb(11, 23, 54)", "hero title highlight color": "rgb(255, 200, 61)",
        "footer background-color": "rgb(7, 15, 36)", "membership offer button background-color": "rgb(31, 79, 216)",
        "cta gradient": "linear-gradient(90deg, rgb(11, 23, 54), color(srgb 0.121569 0.309804 0.847059 / 0.8))",
    },
    "dark": {
        "root --color-hero": "#060b18", "root --color-hero-deep": "#060d20", "root --color-footer": "#050913",
        "root --color-on-hero": "#ffffff", "root --color-on-hero-muted": "#e3e9f8", "root --color-hero-highlight": "#ffc83d",
        "home --h-primary": "#3d6cf0", "home --h-primary-hover": "#5a83f3", "home --h-primary-text": "#8aa8ff",
        "home --h-accent": "#f5b301", "home --h-on-accent": "#1a1405", "home --h-icon-bg": "rgba(138, 168, 255, 0.12)",
        "home --h-hero": "#060b18", "home --h-hero-accent": "#ffc83d", "home --h-hero-muted": "#e3e9f8",
        "home --h-footer": "#050913", "home --h-deep": "#060d20", "home --h-cta-from": "#0b1736", "home --h-cta-to": "#2a50c8",
        "hero background-color": "rgb(6, 11, 24)", "hero title highlight color": "rgb(255, 200, 61)",
        "footer background-color": "rgb(5, 9, 19)", "membership offer button background-color": "rgb(61, 108, 240)",
        "cta gradient": "linear-gradient(90deg, rgb(11, 23, 54), color(srgb 0.164706 0.313726 0.784314 / 0.8))",
    },
}

PROBE = """() => {
  const root = getComputedStyle(document.documentElement)
  const home = getComputedStyle(document.querySelector('.home-page'))
  const css = (selector, property, pseudo) => {
    const element = document.querySelector(selector)
    return element ? getComputedStyle(element, pseudo).getPropertyValue(property).trim() : 'missing'
  }
  const values = {}
  for (const name of ['--color-hero', '--color-hero-deep', '--color-footer', '--color-on-hero', '--color-on-hero-muted', '--color-hero-highlight']) {
    values['root ' + name] = root.getPropertyValue(name).trim()
  }
  for (const name of ['--h-primary', '--h-primary-hover', '--h-primary-text', '--h-accent', '--h-on-accent', '--h-icon-bg', '--h-hero',
    '--h-hero-accent', '--h-hero-muted', '--h-footer', '--h-deep', '--h-cta-from', '--h-cta-to']) {
    values['home ' + name] = home.getPropertyValue(name).trim()
  }
  values['hero background-color'] = css('.home-hero', 'background-color')
  values['hero title highlight color'] = css('.home-hero h1 em', 'color')
  values['footer background-color'] = css('.home-footer', 'background-color')
  values['membership offer button background-color'] = css('.home-strip__offer', 'background-color')
  values['cta gradient'] = css('.home-cta', 'background-image', '::before')
  values['heading font-family'] = css('.home-hero h1', 'font-family')
  values['data-theme'] = document.documentElement.dataset.theme
  return values
}"""


def canonical(value):
    """Same color written as #fff, #ffffff, #ffffffff, rgb() or rgba() compares equal (builds minify hex)."""
    text = (value or "").strip().lower()
    match = re.fullmatch(r"#([0-9a-f]{3,8})", text)
    if match and len(match.group(1)) in (3, 4, 6, 8):
        digits = match.group(1)
        if len(digits) in (3, 4):
            digits = "".join(ch * 2 for ch in digits)
        alpha = int(digits[6:8], 16) / 255 if len(digits) == 8 else 1
        return f"rgba({int(digits[0:2], 16)},{int(digits[2:4], 16)},{int(digits[4:6], 16)},{alpha:.2f})"
    match = re.fullmatch(r"rgba?\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*(?:,\s*([\d.]+)\s*)?\)", text)
    if match:
        alpha = float(match.group(4)) if match.group(4) else 1
        return f"rgba({match.group(1)},{match.group(2)},{match.group(3)},{alpha:.2f})"
    return text


def answer_api(route):
    path = route.request.url.split("/api/v1", 1)[1].split("?", 1)[0]
    if path in FIXTURES:
        route.fulfill(status=200, content_type="application/json", body=json.dumps(FIXTURES[path]))
    else:
        route.fulfill(status=401, content_type="application/json", body=json.dumps({"message": "Unauthorized"}))


# Other public screens that share the tokens: guest auth dialog on Home, Log in and Sign up (no session needed).
EXTRA_SCREENS = [
    ("home-auth-dialog", "/", "Membership offers", '[role="dialog"]'),
    ("login", "/login", None, "form"),
    ("register", "/register", None, "form"),
]
EXTRA_WIDTHS = [390, 1440]


def capture(browser, base_url, theme, width, path, route="/", click=None, ready=".home-page", probe=True):
    context = browser.new_context(viewport={"width": width, "height": 900}, reduced_motion="reduce",
                                  color_scheme=theme, device_scale_factor=1)
    context.add_init_script(f"try {{ localStorage.setItem('sportify.themePreference', '{theme}') }} catch {{}}")
    context.route(API, answer_api)
    context.route(lambda url: "unsplash.com" in url or "mixkit.co" in url, lambda route: route.abort())
    page = context.new_page()
    page.goto(base_url + route, wait_until="networkidle")
    if click:
        page.get_by_role("link", name=click).or_(page.get_by_role("button", name=click)).first.click()
    page.wait_for_selector(ready)
    page.evaluate("document.fonts.ready")
    page.add_style_tag(content="*, *::before, *::after { animation: none !important; transition: none !important; caret-color: transparent !important; }")
    page.wait_for_timeout(300)
    page.screenshot(path=str(path), full_page=True)
    values = page.evaluate(PROBE) if probe else {}
    context.close()
    return values


def compare(before_path, after_path, diff_path):
    before, after = Image.open(before_path).convert("RGB"), Image.open(after_path).convert("RGB")
    if before.size != after.size:
        return {"identical": False, "reason": f"size {before.size} vs {after.size}", "changed_pixels": None}
    diff = ImageChops.difference(before, after)
    red, green, blue = diff.split()
    mask = ImageChops.lighter(ImageChops.lighter(red, green), blue).point(lambda value: 255 if value else 0)
    changed = mask.histogram()[255]
    if changed:
        mask.save(diff_path)
    return {"identical": changed == 0, "changed_pixels": changed, "total_pixels": before.size[0] * before.size[1], "size": list(before.size)}


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--before", required=True)
    parser.add_argument("--after", required=True)
    parser.add_argument("--out", required=True)
    args = parser.parse_args()
    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    report = {"screens": [], "rendered": {}, "failures": []}
    with sync_playwright() as playwright:
        browser = playwright.chromium.launch()
        for theme in THEMES:
            for width in WIDTHS:
                name = f"home-{theme}-{width}"
                before_png, after_png = out / f"{name}-before.png", out / f"{name}-after.png"
                before_values = capture(browser, args.before.rstrip("/"), theme, width, before_png)
                after_values = capture(browser, args.after.rstrip("/"), theme, width, after_png)
                result = {"screen": name, **compare(before_png, after_png, out / f"{name}-diff.png")}
                report["screens"].append(result)
                if not result["identical"]:
                    report["failures"].append(f"{name}: screenshot differs ({result.get('changed_pixels')} pixels)")
                if after_values.get("data-theme") != theme:
                    report["failures"].append(f"{name}: data-theme is {after_values.get('data-theme')}")
                for key, value in after_values.items():
                    if key in ("data-theme",):
                        continue
                    # New shared tokens do not exist on main (empty before); every other value must be unchanged.
                    if before_values.get(key) != "" and canonical(before_values.get(key)) != canonical(value):
                        report["failures"].append(f"{name}: {key} rendered {before_values.get(key)!r} before and {value!r} after")
                    expected = EXPECTED[theme].get(key)
                    if expected is not None and canonical(value) != canonical(expected):
                        report["failures"].append(f"{name}: {key} is {value!r}, expected {expected!r}")
                if width == 1440:
                    report["rendered"][theme] = {"before": before_values, "after": after_values}
        for theme in THEMES:
            for width in EXTRA_WIDTHS:
                for screen, route, click, ready in EXTRA_SCREENS:
                    name = f"{screen}-{theme}-{width}"
                    before_png, after_png = out / f"{name}-before.png", out / f"{name}-after.png"
                    capture(browser, args.before.rstrip("/"), theme, width, before_png, route, click, ready, probe=False)
                    capture(browser, args.after.rstrip("/"), theme, width, after_png, route, click, ready, probe=False)
                    result = {"screen": name, **compare(before_png, after_png, out / f"{name}-diff.png")}
                    report["screens"].append(result)
                    if not result["identical"]:
                        report["failures"].append(f"{name}: screenshot differs ({result.get('changed_pixels')} pixels)")
        browser.close()
    (out / "report.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    for line in report["failures"]:
        print("FAIL", line)
    print(f"{len(report['screens'])} screen pairs, {sum(s['identical'] for s in report['screens'])} identical, {len(report['failures'])} failures")
    return 1 if report["failures"] else 0


if __name__ == "__main__":
    sys.exit(main())
