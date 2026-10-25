"""UI-06 Log in check (layout B) in a real browser (before = the base branch, after = the UI-06 branch).

Opens /login of two running builds in Chromium at 390, 768 and 1440 px in light and dark mode, in three states
(empty, field validation errors, invalid credentials from the server), and takes before and after screenshots.
On the UI-06 build it also checks what the reviewer asks of a theme change:
  - rendered text contrast against the actual background (WCAG 2.2 AA: 4.5:1, 3:1 for large text);
  - the display font is loaded and used for the headings;
  - Remember me and Forgot password share a text baseline;
  - no horizontal page overflow, and on a 1440 x 900 screen the page fits without scrolling (empty and field errors);
  - text on the photo (slogan, Back to Home) is checked against the hero navy, which the overlay darkens the photo to;
  - keyboard focus is visible on the sun and moon switch, the email field, the password toggle and the Log in button;
  - a successful login still leaves /login for the member area (POST /auth/login, then GET /users/me).

The API is answered with fixed JSON. Usage (PowerShell, from frontend/):
  pip install playwright pillow; python -m playwright install chromium
  npm run build -- --outDir ../dist-after   (on this branch; the base branch into ../dist-before)
  npx vite preview --outDir ../dist-before --port 4301   (and ../dist-after on 4302)
  python scripts/ui06-login-check.py --before http://localhost:4301 --after http://localhost:4302 --out docs/screenshots/ui-06
Exit code 0 when every check passes.
"""

import argparse
import json
import sys
from pathlib import Path

from playwright.sync_api import sync_playwright

WIDTHS = [390, 768, 1440]
THEMES = ["light", "dark"]
STATES = ["empty", "errors", "invalid"]
MEMBER = {"id": 2, "email": "member@sportify.demo", "fullName": "Taylor Nguyen", "phone": "0901234567", "role": "MEMBER", "status": "ACTIVE"}

CONTRAST = r"""() => {
  const parse = (value) => {
    const m = value.match(/rgba?\(([^)]+)\)/)
    if (!m) return null
    const [r, g, b, a = 1] = m[1].split(/[ ,/]+/).filter(Boolean).map(Number)
    return { r, g, b, a }
  }
  const lum = ({ r, g, b }) => {
    const c = [r, g, b].map((v) => { v /= 255; return v <= 0.03928 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4 })
    return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2]
  }
  // Header and slogan sit on the photo under the navy overlay: measure them against --color-navy.
  const navy = getComputedStyle(document.documentElement).getPropertyValue('--color-navy').trim()
  const onPhoto = { r: parseInt(navy.slice(1, 3), 16), g: parseInt(navy.slice(3, 5), 16), b: parseInt(navy.slice(5, 7), 16), a: 1 }
  const background = (el) => {
    for (let node = el; node; node = node.parentElement) {
      if (node.matches('.auth-stage__header, .auth-stage__copy')) return onPhoto
      const bg = parse(getComputedStyle(node).backgroundColor)
      if (bg && bg.a > 0.5) return bg
    }
    return { r: 255, g: 255, b: 255, a: 1 }
  }
  const checks = [
    ['heading', '.auth-panel__heading h1'], ['subtitle', '.auth-panel__heading p'], ['email label', '.field__label'],
    ['remember me', '.checkbox__label'], ['forgot password', '.auth-form__options .link-button'],
    ['footer text', '.auth-panel__footer span'], ['create account link', '.auth-panel__footer a'],
    ['log in button', '.auth-form .btn--primary'], ['brand name', '.brand__name'], ['brand mark', '.brand__mark'],
    ['field error', '.field__error'], ['form alert', '.notice'],
    ['back to home', '.auth-stage__home'], ['slogan title', '.auth-stage__copy__title'], ['slogan eyebrow', '.auth-stage__copy__eyebrow'],
    ['slogan text', '.auth-stage__copy__text'],
  ]
  return checks.flatMap(([name, selector]) => {
    const el = document.querySelector(selector)
    if (!el || !el.getClientRects().length || !el.textContent.trim()) return []
    const style = getComputedStyle(el)
    const fg = parse(style.color), bg = background(el)
    const [hi, lo] = [lum(fg), lum(bg)].sort((x, y) => y - x)
    const size = parseFloat(style.fontSize), weight = Number(style.fontWeight)
    const large = size >= 24 || (size >= 18.66 && weight >= 700)
    return [{ name, ratio: Math.round(((hi + 0.05) / (lo + 0.05)) * 100) / 100, needed: large ? 3 : 4.5 }]
  })
}"""

LAYOUT = r"""() => {
  // Text baseline: a zero-size inline-block placed after the text sits exactly on the baseline.
  const text = (selector) => {
    const marker = document.createElement('span')
    marker.style.cssText = 'display:inline-block;width:0;height:0;vertical-align:baseline'
    document.querySelector(selector).append(marker)
    const baseline = marker.getBoundingClientRect().bottom
    marker.remove()
    return baseline
  }
  const card = document.querySelector('.auth-panel').getBoundingClientRect()
  return {
    overflow: document.documentElement.scrollWidth - window.innerWidth,
    cardHeight: Math.round(card.height),
    pageHeight: document.documentElement.scrollHeight,
    baselineGap: Math.abs(text('.checkbox__label') - text('.auth-form__options .link-button')),
    headingFont: getComputedStyle(document.querySelector('.auth-panel__heading h1')).fontFamily,
    fontLoaded: document.fonts.check('800 32px "Be Vietnam Pro"'),
  }
}"""


def api(state):
    def answer(route):
        path = route.request.url.split("/api/v1", 1)[1].split("?", 1)[0]
        if path == "/auth/login":
            if state == "success":
                route.fulfill(status=200, content_type="application/json", body=json.dumps({"accessToken": "a", "refreshToken": "r"}))
            else:
                route.fulfill(status=401, content_type="application/json", body=json.dumps({"message": "Bad credentials"}))
        elif path == "/users/me" and state == "success":
            route.fulfill(status=200, content_type="application/json", body=json.dumps(MEMBER))
        elif state == "success" and path != "/auth/refresh":
            # Member area data after login is not under test; empty lists keep the session alive.
            route.fulfill(status=200, content_type="application/json", body="[]")
        else:
            route.fulfill(status=401, content_type="application/json", body=json.dumps({"message": "Unauthorized"}))
    return answer


def open_login(browser, base_url, theme, width, state):
    context = browser.new_context(viewport={"width": width, "height": 900}, device_scale_factor=1, reduced_motion="reduce")
    context.add_init_script(f"try {{ localStorage.setItem('sportify.themePreference', '{theme}') }} catch {{}}")
    context.route("**/api/v1/**", api(state))
    page = context.new_page()
    page.goto(base_url + "/login", wait_until="networkidle")
    page.wait_for_selector(".auth-form")
    page.evaluate("document.fonts.ready")
    if state == "errors":
        page.get_by_role("button", name="Log in", exact=True).click()
        page.wait_for_selector(".field__error:not(:empty)")
    elif state in ("invalid", "success"):
        page.locator("input[name=email]").fill("member@sportify.demo")
        page.locator("input[name=password]").fill("WrongPassword1!")
        page.get_by_role("button", name="Log in", exact=True).click()
        if state == "invalid":
            page.wait_for_selector('[role="alert"]')
    page.add_style_tag(content="*, *::before, *::after { transition: none !important; animation: none !important; caret-color: transparent !important; }")
    page.wait_for_timeout(250)
    return context, page


def focus_checks(page):
    results = {}
    page.locator("body").click(position={"x": 2, "y": 2})
    for name, target in (("theme switch", page.locator(".auth-theme")), ("email field", page.locator("input[name=email]")), ("show password", page.get_by_role("button", name="Show password")),
                         ("log in button", page.get_by_role("button", name="Log in", exact=True))):
        target.focus()
        page.keyboard.press("Shift+Tab")
        page.keyboard.press("Tab")
        style = target.evaluate("(el) => { const s = getComputedStyle(el); return { shadow: s.boxShadow, outline: s.outlineStyle + ' ' + s.outlineWidth } }")
        results[name] = style["shadow"] != "none" or not style["outline"].startswith("none")
    return results


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--before", required=True)
    parser.add_argument("--after", required=True)
    parser.add_argument("--out", required=True)
    args = parser.parse_args()
    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    report = {"screens": [], "contrast": {}, "layout": {}, "focus": {}, "login_flow": None, "failures": []}
    fail = report["failures"].append
    with sync_playwright() as playwright:
        browser = playwright.chromium.launch()
        for theme in THEMES:
            for width in WIDTHS:
                heights = {}
                for state in STATES:
                    name = f"login-{theme}-{width}-{state}"
                    for build, url in (("before", args.before), ("after", args.after)):
                        context, page = open_login(browser, url.rstrip("/"), theme, width, state)
                        page.screenshot(path=str(out / f"{name}-{build}.png"), full_page=True)
                        if build == "after":
                            contrast = page.evaluate(CONTRAST)
                            layout = page.evaluate(LAYOUT)
                            report["contrast"][name] = contrast
                            report["layout"][name] = layout
                            heights[state] = layout["cardHeight"]
                            for item in contrast:
                                if item["ratio"] < item["needed"]:
                                    fail(f"{name}: {item['name']} contrast {item['ratio']} below {item['needed']}")
                            if layout["overflow"] > 0:
                                fail(f"{name}: horizontal overflow {layout['overflow']} px")
                            if layout["baselineGap"] > 2:
                                fail(f"{name}: Remember me and Forgot password baselines differ by {layout['baselineGap']:.1f} px")
                            if "Be Vietnam Pro" not in layout["headingFont"] or not layout["fontLoaded"]:
                                fail(f"{name}: display font not used or not loaded ({layout['headingFont']})")
                            if state == "empty":
                                report["focus"][f"{theme}-{width}"] = focus_checks(page)
                                for target, visible in report["focus"][f"{theme}-{width}"].items():
                                    if not visible:
                                        fail(f"{name}: no visible focus on {target}")
                        context.close()
                    report["screens"].append(name)
                if width == 1440:
                    for state in ("empty", "errors"):
                        page_height = report["layout"][f"login-{theme}-1440-{state}"]["pageHeight"]
                        if page_height > 900:
                            fail(f"login-{theme}-1440-{state}: page scrolls on a 1440 x 900 screen ({page_height} px)")
        context, page = open_login(browser, args.after.rstrip("/"), "light", 1440, "success")
        page.wait_for_function("!location.pathname.startsWith('/login')", timeout=10000)
        report["login_flow"] = page.evaluate("location.pathname")
        if not report["login_flow"].startswith("/member"):
            fail(f"login flow ended on {report['login_flow']}, expected the member area")
        context.close()
        browser.close()
    (out / "report.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    for line in report["failures"]:
        print("FAIL", line)
    lowest = min((item["ratio"], name, item["name"]) for name, items in report["contrast"].items() for item in items)
    print(f"{len(report['screens'])} states x 2 builds captured; lowest contrast {lowest}; login flow -> {report['login_flow']}; {len(report['failures'])} failures")
    return 1 if report["failures"] else 0


if __name__ == "__main__":
    sys.exit(main())
